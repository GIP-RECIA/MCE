/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.ConfirmationType;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.enums.SurType;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.ChampsObligatoiresException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.RecoverUidRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class EmailVerificationService {

    private static final String VALID_ACCOUNT_STATE = "Valide";

    /**
     * Durée de conservation d'une entrée de compteur de tentatives : doit dépasser la durée de vie d'un code (expiryHours) pour ne pas purger un compteur
     * encore pertinent, tout en libérant la mémoire des comptes abandonnés en cours de route.
     */
    private static final long ATTEMPT_ENTRY_TTL_MS = 2 * 3_600_000L;

    /**
     * Compteur de tentatives horodaté : le champ {@code lastTouchMs} est rafraîchi à chaque accès (bon ou mauvais code), ce qui permet à
     * {@link #purgeStaleAttemptEntries()} de supprimer les entrées des utilisateurs partis sans conclure.
     */
    static final class AttemptEntry {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long lastTouchMs = System.currentTimeMillis();

        boolean isStale(long nowMs) {
            return nowMs - lastTouchMs >= ATTEMPT_ENTRY_TTL_MS;
        }

        void touch() {
            lastTouchMs = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<Long, AttemptEntry> resetAttempts = new ConcurrentHashMap<>();

    /**
     * Compteur utilisé lorsque le boug ne fournit pas d'UID : un mauvais code
     * ne permet pas encore d'identifier la personne concernée.
     */
    private final ConcurrentHashMap<String, ResetChallenge> resetChallenges = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, AttemptEntry> verificationAttempts = new ConcurrentHashMap<>();

    static final class ResetChallenge {
        final String uid;
        final long expiresAtMs;

        ResetChallenge(String uid, long expiresAtMs) {
            this.uid = uid;
            this.expiresAtMs = expiresAtMs;
        }
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void purgeStaleAttemptEntries() {
        long now = System.currentTimeMillis();
        int before = resetAttempts.size() + resetChallenges.size() + verificationAttempts.size();
        resetAttempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        resetChallenges.entrySet().removeIf(e -> e.getValue().expiresAtMs <= now);
        verificationAttempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        int removed = before - resetAttempts.size() - resetChallenges.size() - verificationAttempts.size();
        if (removed > 0) {
            log.info("Purge des compteurs de tentatives expirés : {} entrée(s) supprimée(s)", removed);
        }
    }

    private boolean isResendCooldownActive(List<CerbereConfirmation> pending, String uid, String logPrefix) {
        if (!pending.isEmpty()) {
            CerbereConfirmation last = pending.get(0);
            if (last.getLimite() != null) {
                long expiryHours = mailProperties.getVerification().getExpiryHours();
                long estimatedCreation = last.getLimite().getTime() - (expiryHours * 3_600_000L);
                long elapsed = System.currentTimeMillis() - estimatedCreation;
                if (elapsed < mceProperties.getSecurity().getResetPolicy().getResendCooldownMs()) {
                    log.warn("[{}] Anti-double-clic : dernière demande il y a {} ms pour uid={}", logPrefix, elapsed, uid);
                    return true;
                }
            }
        }
        return false;
    }

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private IStructureService structureService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CharteService charteService;

    @Autowired
    private IUserDTOFactory userDTOFactory;

    private final SecureRandom secureRandom = new SecureRandom();

    private String hashWithPrefix(String code, ConfirmationType type) {
        return type.getCodePrefix() + sha256(code);
    }

    private Date calculateExpiryDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, (int) mailProperties.getVerification().getExpiryHours());
        return cal.getTime();
    }

    private void sendEmailWithTemplate(String to, String code, MailProperties.EmailTemplates.Template template, String errorMessage) {
        String expiryHours = String.valueOf(mailProperties.getVerification().getExpiryHours());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromEmail());
        message.setTo(to);
        message.setSubject(template.getSubject());
        message.setText(template.getBody()
                .replace("{{code}}", code)
                .replace("{{expiryHours}}", expiryHours));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error(errorMessage, to, e.getMessage(), e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    private String sha256(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }

    public String getVerificationFrontendUrl() {
        return mailProperties.getVerification().getFrontendUrl();
    }

    public String generateVerificationCode() {
        int codeLength = mailProperties.getVerification().getCodeLength();
        int max = (int) Math.pow(10, codeLength);
        int code = secureRandom.nextInt(max);
        return String.format("%0" + codeLength + "d", code);
    }

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new InactiveAccountException("Aucun compte associé à cet identifiant");
        }

        // Anti-double-clic
        List<CerbereConfirmation> pending = cerbereConfirmationRepository.findPendingEmailVerificationByPersonId(person.getId());
        if (!pending.isEmpty()) {
            CerbereConfirmation last = pending.get(0);
            if (last.getLimite() != null) {
                long expiryHours = mailProperties.getVerification().getExpiryHours();
                long estimatedCreation = last.getLimite().getTime() - (expiryHours * 3_600_000L);
                long elapsed = System.currentTimeMillis() - estimatedCreation;
                if (elapsed < mceProperties.getSecurity().getResetPolicy().getResendCooldownMs()) {
                    log.warn("[VERIFY_EMAIL] Anti-double-clic : dernière demande il y a {} ms pour uid={}", elapsed, uid);
                    return;
                }
            }
        }

        String code = generateVerificationCode();
        String hashedCode = hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);

        Date limite = calculateExpiryDate();

        cerbereConfirmationRepository.deletePendingEmailVerificationByPersonId(person.getId());

        CerbereConfirmation confirmation = new CerbereConfirmation();
        confirmation.setAPersonne(person);
        confirmation.setCode(hashedCode);
        confirmation.setMail(email);
        confirmation.setLimite(limite);
        confirmation.setConfirmation(null);
        confirmation.setEditor(person);
        cerbereConfirmationRepository.save(confirmation);

        // Nouveau code de vérification : le compteur de tentatives repart de zéro.
        verificationAttempts.remove(person.getId());

        sendAfterCommit(() -> sendEmail(email, code));

        log.info("Email de vérification envoyé à {} pour l'utilisateur [uid={}]", email, uid);
    }

    private void sendEmail(String to, String code) {
        sendEmailWithTemplate(to, code, mailProperties.getTemplates().getVerification(),
                "Erreur lors de l'envoi de l'email de vérification à {} : {}");
    }

    @Transactional
    public void sendPasswordResetCode(String uid, String email, String profil) {
        log.info("[RESET_PASSWORD] Début sendPasswordResetCode uid={}, email={}, profil={}", uid, email, profil);
        if (email != null) {
            email = email.trim();
        }

        // Verrou pessimiste sur la ligne personne : deux requêtes simultanées pour le
        // même uid sont sérialisées ; la seconde retombera sur l'anti-double-clic
        // au lieu de créer un second code valide.
        APersonne person = aPersonneRepository.findByUidWithLock(uid);
        if (person == null) {
            throw new InvalidCodeException("Aucun compte associé à cet identifiant");
        }

        if (profil != null && !profil.isBlank()) {
            EnumCategorie requested = EnumCategorie.fromProfile(profil);
            String dbCategorie = person.getCategorie();
            if (requested == null || dbCategorie == null || !requested.getDbname().equalsIgnoreCase(dbCategorie)) {
                log.warn("[RESET_PASSWORD] Profil incohérent : front='{}' vs DB='{}' uid={}", profil, dbCategorie, uid);
                throw new InvalidCodeException("Profil incohérent avec votre compte");
            }
        }

        // Collecte de tous les emails associés au compte (LDAP + personnel + confirmés)
        List<String> accountEmails = collectAccountEmails(person);

        // L'email est obligatoire pour réinitialiser : si le compte ne porte aucun email,
        // impossible d'envoyer un code — l'utilisateur doit contacter un administrateur
        // de son établissement (même si un email a été fourni dans la requête).
        if (accountEmails.isEmpty()) {
            log.warn("[RESET_PASSWORD] Aucun email sur le compte uid={}", uid);
            throw new ContactAdminException(
                    "Aucune adresse email n'est associée à votre compte. Veuillez contacter un administrateur de votre établissement.");
        }

        if (email == null || email.isBlank()) {
            if (accountEmails.size() == 1) {
                email = accountEmails.get(0);
                log.info("[RESET_PASSWORD] Email auto-assigné (seul email du compte) uid={}, email={}", uid, email);
            } else {
                log.warn("[RESET_PASSWORD] Email non fourni pour un compte avec {} emails uid={}", accountEmails.size(), uid);
                throw new InvalidCodeException(
                        "Veuillez renseigner votre adresse email pour réinitialiser votre mot de passe.");
            }
        }

        if (!isEmailAssociatedWithAccount(person, email)) {
            log.warn("[RESET_PASSWORD] Email non associé à ce compte : uid={}", uid);
            throw new InvalidCodeException("Cette adresse email n'est pas associée à votre compte");
        }

        // Anti-double-clic
        List<CerbereConfirmation> pending = cerbereConfirmationRepository.findPendingPasswordResetByPersonId(person.getId());
        if (!pending.isEmpty()) {
            CerbereConfirmation last = pending.get(0);
            if (last.getLimite() != null) {
                long expiryHours = mailProperties.getVerification().getExpiryHours();
                long estimatedCreation = last.getLimite().getTime() - (expiryHours * 3_600_000L);
                long elapsed = System.currentTimeMillis() - estimatedCreation;
                if (elapsed < mceProperties.getSecurity().getResetPolicy().getResendCooldownMs()) {
                    log.warn("[RESET_PASSWORD] Anti-double-clic : dernière demande il y a {} ms pour uid={}", elapsed, uid);
                    return;
                }
            }
        }

        // Vérification de l'état du compte
        if (!VALID_ACCOUNT_STATE.equals(person.getEtat())) {
            log.warn("[RESET_PASSWORD] État '{}' ≠ '{}' pour uid={}", person.getEtat(), VALID_ACCOUNT_STATE, uid);
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }

        PersonneDTO personneDTO = personneService.getUserByUid(uid);
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }

        assertPasswordResetAllowed(personneDTO, uid);

        // Génération du code
        String code = generateVerificationCode();
        String hashedCode = hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);

        Date limite = calculateExpiryDate();

        // Réutilisation ou création
        List<CerbereConfirmation> existing = cerbereConfirmationRepository.findLatestPasswordResetByPersonId(person.getId());
        CerbereConfirmation confirmation;
        if (!existing.isEmpty() && existing.get(0).getConfirmation() == null) {
            confirmation = existing.get(0);
            log.info("[RESET_PASSWORD] Réutilisation de la confirmation existante id={}", confirmation.getId());
        } else {
            confirmation = new CerbereConfirmation();
            confirmation.setAPersonne(person);
            confirmation.setEditor(person);
        }
        confirmation.setCode(hashedCode);
        confirmation.setMail(email);
        confirmation.setLimite(limite);
        confirmation.setConfirmation(null);
        cerbereConfirmationRepository.save(confirmation);

        // Nouvelle demande de code : le compteur de tentatives repart de zéro.
        resetAttempts.remove(person.getId());

        final String recipient = email;
        sendAfterCommit(() -> sendResetEmail(recipient, code));
        log.info("[RESET_PASSWORD] Code généré pour uid={} (envoi programmé après commit)", uid);
    }

    /**
     * Parcours « mot de passe oublié sans uid » : résout le compte de façon <b>précise</b> à partir de
     * l'identité (nom, prénom, profil), de l'établissement (type × ville × établissement) et de l'email
     * fournis, puis envoie le code de réinitialisation si la cible est <b>unique</b>.
     *
     * <p>
     * Demander autant d'informations que l'ancien {@code search-uid} permet d'éviter les <b>doublons</b>
     * (plusieurs comptes sur le même email) et le <b>vol</b> (déclencher une réinitialisation en ne
     * connaissant que l'email), tout en conservant une réponse <b>générique</b> : qu'il existe un compte
     * ou non, le front reçoit la même réponse. Aucun uid n'est renvoyé → pas d'énumération.
     * </p>
     *
     * <p>Renvoie les informations de charte de la cible résolue (nécessaires au front pour afficher la
     * case avant la saisie du code) sans exposer l'uid. {@code null} si aucun code n'a été envoyé.</p>
     *
     * @param request identité et critères fournis par l'utilisateur
     * @return informations de charte de la cible, ou {@code null} si aucun code envoyé
     */
    @Transactional
    public RecoverUidResult recoverUid(RecoverUidRequestDTO request) {
        validateRecoverRequest(request);
        String email = request.getEmail().trim();
        EnumCategorie categorie = EnumCategorie.fromProfile(request.getProfil());
        Collection<String> sirens = resolveSirens(request.getTypeEtablissement().trim(), request.getVille().trim(),
                request.getEtablissement().trim());
        if (sirens.isEmpty()) {
            log.warn("[RECOVER_UID] Établissement invalide : type={}, ville={}, etab={}",
                    request.getTypeEtablissement(), request.getVille(), request.getEtablissement());
            throw new ChampsObligatoiresException(
                    "Le type, la ville ou l'établissement ne correspond pas aux informations disponibles");
        }
        List<APersonne> matches = aPersonneRepository.searchByIdentityAndEmail(
                request.getNom().trim(), request.getPrenom().trim(), categorie.getDbname(), sirens, email);
        log.info("[RECOVER_UID] {} compte(s) après filtrage de tous les champs", matches.size());

        if (matches.isEmpty() && !aPersonneRepository.searchByIdentityAndEmailWithoutCategory(
                request.getNom().trim(), request.getPrenom().trim(), sirens, email).isEmpty()) {
            log.warn("[RECOVER_UID] Profil incohérent pour email={} : profil demandé={}", email, request.getProfil());
            throw new ChampsObligatoiresException("Le profil ne correspond pas aux informations du compte");
        }

        // L'annuaire peut contenir un email absent des colonnes email de la base.
        // Ce cas reste soumis aux mêmes contrôles d'identité, profil et établissement.
        if (matches.isEmpty()) {
            List<Object[]> candidates = aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(
                    request.getNom().trim(), request.getPrenom().trim(), categorie.getDbname(), sirens);
            for (Object[] candidate : candidates) {
                String uid = (String) candidate[0];
                if (sameEmail(email, ldapEmailByUid(uid))) {
                    APersonne person = aPersonneRepository.findByUid(uid);
                    if (person != null) {
                        matches.add(person);
                    }
                }
            }
        }

        if (matches.size() != 1) {
            log.warn("[RECOVER_UID] Informations de récupération incohérentes ou ambiguës : {} cible(s)", matches.size());
            throw new ChampsObligatoiresException(
                    "Les informations fournies ne correspondent pas à un compte unique");
        }

        APersonne target = matches.get(0);
        try {
            sendPasswordResetCode(target.getUid(), email, request.getProfil());
        } catch (InvalidCodeException | InactiveAccountException | ContactAdminException | CharteNotAcceptedException e) {
            // Propager les exceptions de validation pour affichage au frontend
            throw e;
        } catch (RuntimeException e) {
            log.warn("[RECOVER_UID] Code non envoyé pour uid={} : {}", target.getUid(), e.getMessage());
            return null;
        }

        boolean charteRequired = charteService.isCharteRequired(target.getUid());
        String charteUrl = charteService.getCharteUrl(target.getUid());  // Toujours récupérer l'URL (même si non requise)
        log.info("[RECOVER_UID] uid={} charte requise ? {} charteUrl={}", target.getUid(), charteRequired, charteUrl);
        String resetToken = UUID.randomUUID().toString();
        resetChallenges.put(resetToken, new ResetChallenge(target.getUid(),
                System.currentTimeMillis() + mailProperties.getVerification().getExpiryHours() * 3_600_000L));
        return new RecoverUidResult(charteRequired, charteUrl, resetToken);
    }

    /**
     * Informations de charte de la cible résolue par {@link #recoverUid(RecoverUidRequestDTO)}.
     * Ne contient aucun identifiant (uid) : transmissible au front sans risque d'énumération.
     */
    public static class RecoverUidResult {
        private final boolean charteRequired;
        private final String charteUrl;
        private final String resetToken;

        public RecoverUidResult(boolean charteRequired, String charteUrl) {
            this(charteRequired, charteUrl, null);
        }

        public RecoverUidResult(boolean charteRequired, String charteUrl, String resetToken) {
            this.charteRequired = charteRequired;
            this.charteUrl = charteUrl;
            this.resetToken = resetToken;
        }

        public boolean isCharteRequired() {
            return charteRequired;
        }

        public String getCharteUrl() {
            return charteUrl;
        }

        public String getResetToken() {
            return resetToken;
        }
    }

    private void validateRecoverRequest(RecoverUidRequestDTO request) {
        if (request == null) {
            throw new ChampsObligatoiresException("Le corps de la requête est obligatoire");
        }
        requireRecoverField(request.getNom(), "Le nom est obligatoire");
        requireRecoverField(request.getPrenom(), "Le prénom est obligatoire");
        requireRecoverField(request.getEmail(), "L'adresse email est obligatoire");
        requireRecoverField(request.getProfil(), "Le profil est obligatoire");
        requireRecoverField(request.getTypeEtablissement(), "Le type d'établissement est obligatoire");
        requireRecoverField(request.getVille(), "La ville est obligatoire");
        requireRecoverField(request.getEtablissement(), "L'établissement est obligatoire");
        if (!request.getEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ChampsObligatoiresException("Le format de l'adresse email est invalide");
        }
        if (EnumCategorie.fromProfile(request.getProfil()) == null) {
            throw new ChampsObligatoiresException("Profil inconnu : " + request.getProfil());
        }
    }

    private void requireRecoverField(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ChampsObligatoiresException(message);
        }
    }

    /**
     * Résout l'ensemble des SIREN d'établissements LDAP qui correspondent au filtre type × ville ×
     * établissement. Vide si aucune structure ne correspond (aucun compte ne pourra être ciblé).
     */
    private Collection<String> resolveSirens(String typeEtablissement, String ville, String etablissement) {
        SurType surType;
        try {
            surType = SurType.valueOf(typeEtablissement.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ChampsObligatoiresException("Type inconnu : " + typeEtablissement
                    + ". Valeurs acceptees : " + SurType.acceptedValues());
        }
        Set<String> sirens = new java.util.LinkedHashSet<>();
        for (IExternalStructure s : structureService.getAllStructures()) {
            if (surType.matches(s.getType()) && ville.equalsIgnoreCase(s.getVille())
                    && etablissement.equalsIgnoreCase(s.getId())) {
                String id = s.getId();
                if (id != null && !id.isBlank()) {
                    sirens.add(id);
                }
            }
        }
        return sirens;
    }

    private String ldapEmailByUid(String uid) {
        try {
            IExternalUser ldapUser = personneService.retrievePersonLdap(uid);
            if (ldapUser != null && ldapUser.getEmail() != null && !ldapUser.getEmail().isBlank()) {
                return ldapUser.getEmail().trim();
            }
        } catch (Exception e) {
            log.warn("[LDAP_EMAIL] Impossible de récupérer l'email LDAP pour uid={}", uid, e);
        }
        return null;
    }

    /**
     * Diffère l'envoi SMTP au commit de la transaction : un rollback ne doit pas laisser partir un code inexistant, et le SMTP lent ne doit pas retenir la
     * connexion DB. Hors transaction (contexte sans synchronisation), l'envoi est immédiat.
     */
    private void sendAfterCommit(Runnable emailAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailAction.run();
                }
            });
        } else {
            emailAction.run();
        }
    }

    /**
     * L'email fourni doit correspondre (insensible à la casse) à l'email du compte, à l'email personnel, à un email confirmé via Cerbère, ou à l'email LDAP.
     *
     * @return true si l'email fourni est associé au compte (via une source connue)
     */
    private boolean isEmailAssociatedWithAccount(APersonne person, String providedEmail) {
        if (providedEmail == null || providedEmail.isBlank()) {
            return false;
        }
        String candidate = providedEmail.trim();
        return collectAccountEmails(person).stream()
                .anyMatch(email -> sameEmail(candidate, email));
    }

    private boolean sameEmail(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b.trim());
    }

    /**
     * Récupère l'email principal de l'utilisateur depuis l'annuaire LDAP. Retourne {@code null} si la personne est absente de l'annuaire ou sans email.
     */
    private String ldapEmail(APersonne person) {
        return ldapEmailByUid(person.getUid());
    }

    private List<String> collectAccountEmails(APersonne person) {
        java.util.Set<String> emails = new java.util.LinkedHashSet<>();
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            emails.add(person.getEmail().trim().toLowerCase());
        }
        if (person.getEmailPersonnel() != null && !person.getEmailPersonnel().isBlank()) {
            emails.add(person.getEmailPersonnel().trim().toLowerCase());
        }
        for (CerbereConfirmation c : cerbereConfirmationRepository.findConfirmedByPersonId(person.getId())) {
            if (c.getMail() != null && !c.getMail().isBlank()) {
                emails.add(c.getMail().trim().toLowerCase());
            }
        }
        String ldapMail = ldapEmail(person);
        if (ldapMail != null) {
            emails.add(ldapMail.trim().toLowerCase());
        }
        return new java.util.ArrayList<>(emails);
    }

    private void sendResetEmail(String to, String code) {
        sendEmailWithTemplate(to, code, mailProperties.getTemplates().getReset(),
                "Erreur lors de l'envoi du code de réinitialisation à {} : {}");
    }

    @Transactional
    public void verifyEmail(String uid, String code) {
        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : utilisateur introuvable", uid);
            throw new InvalidCodeException("Aucun compte associé à cet identifiant");
        }

        String hashedCode = hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);
        Optional<CerbereConfirmation> optConfirmation = cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(person.getId(), hashedCode);

        // Même protection anti-bruteforce que pour le reset : seuls les codes
        // incorrects consomment une tentative ; au-delà de maxAttempts le code
        // en attente est détruit.
        AttemptEntry attempts = verificationAttempts.computeIfAbsent(person.getId(), k -> new AttemptEntry());
        attempts.touch();
        int maxAttempts = mceProperties.getSecurity().getResetPolicy().getMaxAttempts();

        if (attempts.count.get() >= maxAttempts) {
            log.warn("[VERIFY_EMAIL] Compteur saturé ({}/{}) uid={} : suppression du code", attempts.count.get(), maxAttempts, uid);
            cerbereConfirmationRepository.deletePendingEmailVerificationByPersonId(person.getId());
            verificationAttempts.remove(person.getId());
            throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de vérification.");
        }

        if (optConfirmation.isEmpty()) {
            int currentAttempt = attempts.count.incrementAndGet();
            log.info("[VERIFY_EMAIL] Mauvais code, tentative {}/{} pour uid={}", currentAttempt, maxAttempts, uid);
            if (currentAttempt > maxAttempts) {
                cerbereConfirmationRepository.deletePendingEmailVerificationByPersonId(person.getId());
                verificationAttempts.remove(person.getId());
                throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de vérification.");
            }
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code invalide ou déjà utilisé", uid);
            throw new InvalidCodeException("Le code de vérification est incorrect ou a déjà été utilisé.");
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code expiré (limite={})", uid, confirmation.getLimite());
            cerbereConfirmationRepository.delete(confirmation);
            verificationAttempts.remove(person.getId());
            throw new CodeExpiredException("Le code de vérification a expiré. Veuillez en demander un nouveau.");
        }

        String email = confirmation.getMail();
        personneService.updateEmail(uid, email);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        verificationAttempts.remove(person.getId());

        log.info("Email vérifié avec succès pour l'utilisateur [uid={}] -> {}", uid, email);
    }

    @Transactional
    public void processResetPassword(String uid, String code, String newPassword, String confirmPassword, boolean charteAccepted) {
        processResetPassword(uid, null, code, newPassword, confirmPassword, charteAccepted);
    }

    @Transactional
    public void processResetPassword(String uid, String resetToken, String code, String newPassword,
            String confirmPassword, boolean charteAccepted) {
        log.info("[PROCESS_RESET_PASSWORD] Début uid={}", uid);

        APersonne person = null;
        CerbereConfirmation confirmation = null;

        String hashedCode = hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);

        if (uid == null || uid.isBlank()) {
            ResetChallenge challenge = resetToken == null ? null : resetChallenges.get(resetToken);
            if (challenge == null || challenge.expiresAtMs <= System.currentTimeMillis()) {
                if (challenge != null) {
                    resetChallenges.remove(resetToken);
                }
                throw new InvalidCodeException("Le code de réinitialisation est incorrect ou a déjà été utilisé. Veuillez demander un nouveau code.");
            }
            uid = challenge.uid;
        }

        if (uid != null && !uid.isBlank()) {
            // Cas UID connu : résolution par uid + code
            person = aPersonneRepository.findByUid(uid);
            if (person == null) {
                throw new InvalidCodeException("Aucun compte associé à cet identifiant");
            }

            AttemptEntry attempts = resetAttempts.computeIfAbsent(person.getId(), k -> new AttemptEntry());
            attempts.touch();
            int maxAttempts = mceProperties.getSecurity().getResetPolicy().getMaxAttempts();

            if (attempts.count.get() >= maxAttempts) {
                log.warn("[PROCESS_RESET_PASSWORD] Compteur saturé ({}/{}) uid={}", attempts.count.get(), maxAttempts, uid);
                cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(person.getId());
                resetAttempts.remove(person.getId());
                throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de réinitialisation.");
            }

            Optional<CerbereConfirmation> optConfirmation = cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(
                    person.getId(), hashedCode);

            if (optConfirmation.isEmpty()) {
                int currentAttempt = attempts.count.incrementAndGet();
                log.info("[PROCESS_RESET_PASSWORD] Mauvais code, tentative {}/{} pour uid={}", currentAttempt, maxAttempts, uid);
                if (currentAttempt > maxAttempts) {
                    log.warn("[PROCESS_RESET_PASSWORD] Nombre max de tentatives dépassé uid={}, suppression du code", uid);
                    cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(person.getId());
                    resetAttempts.remove(person.getId());
                    throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de réinitialisation.");
                }
                throw new InvalidCodeException("Le code de réinitialisation est incorrect ou a déjà été utilisé. Veuillez demander un nouveau code.");
            }

            confirmation = optConfirmation.get();
        }

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[PROCESS_RESET_PASSWORD] Code expiré uid={}", person.getUid());
            cerbereConfirmationRepository.delete(confirmation);
            resetAttempts.remove(person.getId());
            throw new CodeExpiredException("Le code de réinitialisation a expiré. Veuillez demander un nouveau code.");
        }

        if (!VALID_ACCOUNT_STATE.equals(person.getEtat())) {
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }

        PersonneDTO personneDTO = personneService.getUserByUid(person.getUid());
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }

        assertPasswordResetAllowed(personneDTO, person.getUid());

        log.info("[PROCESS_RESET_PASSWORD] uid={} validationCharte={} charteAccepted={}",
                person.getUid(), person.getValidationCharte(), charteAccepted);
        if (person.getValidationCharte() == null) {
            if (!charteAccepted) {
                String charteUrl = charteService.getCharteUrl(person.getUid());
                log.info("[PROCESS_RESET_PASSWORD] uid={} charte requise, charteUrl={}", person.getUid(), charteUrl);
                throw new CharteNotAcceptedException(
                        "Vous devez accepter les conditions générales d'utilisation avant de changer votre mot de passe",
                        charteUrl);
            }
            log.info("[PROCESS_RESET_PASSWORD] uid={} charte acceptée → signature", person.getUid());
            personneService.signCharte(person.getUid());
        } else {
            log.info("[PROCESS_RESET_PASSWORD] uid={} charte déjà signée, skip", person.getUid());
        }

        passwordService.resetPassword(personneDTO, newPassword, confirmPassword);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(person.getId());

        resetAttempts.remove(person.getId());
        if (resetToken != null) {
            resetChallenges.remove(resetToken);
        }

        personneService.clearUserCaches(person.getUid());

        log.info("[PROCESS_RESET_PASSWORD] Succès uid={}", person.getUid());
    }

    /**
     * Vérifie que le compte dispose d'un mode d'authentification local permettant de réinitialiser son mot de passe. La règle est partagée entre la demande de
     * code et son utilisation.
     *
     * <p>
     * Comptes sans mot de passe local réinitialisable :
     * </p>
     * <ul>
     * <li>EduConnect (parents/élèves éduc nat) : le mot de passe se gère sur le portail EduConnect ;</li>
     * <li>profil indéterminé ({@code enumPublic == null}) : aucun mode d'authentification local reconnu
     * (même règle que UserDTOFactoryImpl.computePassEditable qui renvoie false sur un profil null) ;</li>
     * <li>sans connectOk ni ntPass, aucun mode d'authentification local n'existe (même règle que UserDTOFactoryImpl.computePassEditable).</li>
     * </ul>
     */
    private void assertPasswordResetAllowed(PersonneDTO personneDTO, String uid) {
        if (!userDTOFactory.canResetPassword(personneDTO)) {
            EnumPublic pub = personneDTO.getEnumPublic();
            if (pub == null && userDTOFactory != null) {
                try {
                    pub = userDTOFactory.evalPublic(personneDTO);
                    personneDTO.setEnumPublic(pub);
                } catch (RuntimeException e) {
                    log.error("[PASSWORD_RESET] Échec de l'évaluation du profil uid={}", uid, e);
                }
            }
            if (pub == null) {
                log.warn("[PASSWORD_RESET] Profil non défini pour uid={} : réinitialisation refusée", uid);
                throw new InvalidCodeException("profil non reconnu pour le compte : réinitialisation impossible");
            }
            if (pub.isEduconnect()) {
                log.warn("[PASSWORD_RESET] Refus : compte EduConnect uid={}, enumPublic={}, ntPass={}", uid, pub, personneDTO.isNtPass());
                throw new InvalidCodeException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
            }
            log.warn("[PASSWORD_RESET] Refus : mot de passe local non éditable uid={}, enumPublic={}, ntPass={}",
                    uid, pub, personneDTO.isNtPass());
            throw new InvalidCodeException("Aucune réinitialisation possible pour ce compte : aucun mode d'authentification local n'est actif");
        }
        log.info("[PASSWORD_RESET] Autorisation uid={} : enumPublic={}, ntPass={}", uid, personneDTO.getEnumPublic(), personneDTO.isNtPass());
    }

}
