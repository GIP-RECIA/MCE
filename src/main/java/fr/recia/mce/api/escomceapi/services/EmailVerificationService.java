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

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.enums.ConfirmationType;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.SurType;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.ChampsObligatoiresException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import fr.recia.mce.api.escomceapi.services.exception.ResendCooldownActiveException;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetStatusDTO;
import fr.recia.mce.api.escomceapi.web.dto.RecoverUidRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Coordination du cycle de vérification d'email et de réinitialisation de mot de passe.
 *
 * <p>Service d'orchestration : chaque responsabilité technique est déléguée à un service spécialisé
 * ({@link VerificationCodeService}, {@link ConfirmationMailSender}, {@link AttemptGuardService},
 * {@link AccountEmailService}, {@link PasswordResetPolicyService}).</p>
 */
@Service
@Slf4j
public class EmailVerificationService {

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private IStructureService structureService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CharteService charteService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private ConfirmationMailSender confirmationMailSender;

    @Autowired
    private AttemptGuardService attemptGuardService;

    @Autowired
    private AccountEmailService accountEmailService;

    @Autowired
    private PasswordResetPolicyService passwordResetPolicyService;

    public String generateVerificationCode() {
        return verificationCodeService.generateVerificationCode();
    }

    public void purgeStaleAttemptEntries() {
        attemptGuardService.purgeStaleAttemptEntries();
    }

    /**
     * Garde commune aux parcours de réinitialisation et mot de passe réseau : refuse un compte dont l'état
     * n'est pas Valide (supprimé, bloqué, en attente...).
     */
    private void assertAccountActive(APersonne person, String logPrefix) {
        if (passwordResetPolicyService.isInactiveAccount(person)) {
            log.warn("[{}] État '{}' ≠ '{}' pour uid={}", logPrefix, person.getEtat(),
                    PasswordResetPolicyService.VALID_ACCOUNT_STATE, person.getUid());
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }
    }

    /**
     * Charge le profil de l'utilisateur ; échoue si le profil n'est pas (re)chargeable.
     */
    private PersonneDTO loadProfileOrThrow(APersonne person) {
        PersonneDTO personneDTO = personneService.getUserByUid(person.getUid());
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }
        return personneDTO;
    }

    /**
     * Fabrique un code de vérification hashé (préfixé par le type) avec sa date d'expiration.
     */
    private VerificationCodeBundle buildVerificationCode(ConfirmationType type) {
        String code = verificationCodeService.generateVerificationCode();
        String hashedCode = verificationCodeService.hashWithPrefix(code, type);
        Date limite = verificationCodeService.calculateExpiryDate();
        return new VerificationCodeBundle(code, hashedCode, limite);
    }

    /**
     * Termine une demande de code : réutilise/crée la confirmation, réarme le compteur de tentatives puis
     * programme l'envoi de l'email après commit. Partage la même terminaison entre le parcours public et
     * le parcours « mot de passe réseau » (seuls diffèrent le destinataire et le préfixe de log).
     */
    private void completeCodeSend(APersonne person, String recipient, List<CerbereConfirmation> existing,
            ConfirmationType type, String logPrefix) {
        VerificationCodeBundle bundle = buildVerificationCode(type);
        storeOrReuseConfirmation(person, existing, bundle.hashedCode, recipient, bundle.limite, logPrefix);

        // Nouvelle demande de code : le compteur de tentatives repart de zéro.
        attemptGuardService.clearResetAttempt(person.getId());

        confirmationMailSender.sendAfterCommit(() -> confirmationMailSender.sendResetEmail(recipient, bundle.code));
        log.info("[{}] Code généré pour uid={} (envoi programmé après commit)", logPrefix, person.getUid());
    }

    /**
     * Regroupe le code en clair (envoyé par email), sa version hashée persistée et sa date d'expiration.
     */
    private static final class VerificationCodeBundle {
        private final String code;
        private final String hashedCode;
        private final Date limite;

        private VerificationCodeBundle(String code, String hashedCode, Date limite) {
            this.code = code;
            this.hashedCode = hashedCode;
            this.limite = limite;
        }
    }

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByLogin(uid);
        if (person == null) {
            throw new InactiveAccountException("Aucun compte associé à cet identifiant");
        }

        // Anti-double-clic
        passwordResetPolicyService.assertResendAllowed(
                cerbereConfirmationRepository.findPendingEmailVerificationByPersonId(person.getId()), uid, "VERIFY_EMAIL");

        String code = verificationCodeService.generateVerificationCode();
        String hashedCode = verificationCodeService.hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);

        Date limite = verificationCodeService.calculateExpiryDate();

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
        attemptGuardService.clearVerificationAttempt(person.getId());

        confirmationMailSender.sendAfterCommit(() -> confirmationMailSender.sendVerificationEmail(email, code));

        log.info("Email de vérification envoyé à {} pour l'utilisateur [uid={}]", email, uid);
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
        List<String> accountEmails = accountEmailService.collectAccountEmails(person);

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

        if (!accountEmailService.isEmailAssociatedWithAccount(person, email)) {
            log.warn("[RESET_PASSWORD] Email non associé à ce compte : uid={}", uid);
            throw new InvalidCodeException("Cette adresse email n'est pas associée à votre compte");
        }

        // Anti-double-clic
        passwordResetPolicyService.assertResendAllowed(
                cerbereConfirmationRepository.findPendingPasswordResetByPersonId(person.getId()), uid, "RESET_PASSWORD");

        // Vérification de l'état du compte
        assertAccountActive(person, "RESET_PASSWORD");

        PersonneDTO personneDTO = loadProfileOrThrow(person);

        passwordResetPolicyService.assertPasswordResetAllowed(personneDTO, uid);

        // Réutilisation ou création de la confirmation, puis envoi du code
        List<CerbereConfirmation> existing = cerbereConfirmationRepository.findLatestPasswordResetByPersonId(person.getId());
        completeCodeSend(person, email, existing, ConfirmationType.PASSWORD_RESET, "RESET_PASSWORD");
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
                if (accountEmailService.sameEmail(email, accountEmailService.ldapEmailByUid(uid))) {
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
        } catch (ResendCooldownActiveException e) {
            // Code déjà envoyé récemment : le code en attente reste valide et réutilisable,
            // le parcours reprend (charte + resetToken) sans renvoyer d'email.
            log.warn("[RECOVER_UID] Code déjà envoyé récemment pour uid={} : réutilisation du code en attente",
                    target.getUid());
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
        attemptGuardService.putResetChallenge(resetToken, new AttemptGuardService.ResetChallenge(target.getUid(),
                System.currentTimeMillis() + verificationCodeService.getVerificationExpiryHoursMs()));
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
        Set<String> sirens = new LinkedHashSet<>();
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

    @Transactional
    public void verifyEmail(String uid, String code) {
        APersonne person = aPersonneRepository.findByLogin(uid);
        if (person == null) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : utilisateur introuvable", uid);
            throw new InvalidCodeException("Aucun compte associé à cet identifiant");
        }

        String hashedCode = verificationCodeService.hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);
        Optional<CerbereConfirmation> optConfirmation = cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(person.getId(), hashedCode);

        // Même protection anti-bruteforce que pour le reset : seuls les codes
        // incorrects consomment une tentative ; au-delà de maxAttempts le code
        // en attente est détruit.
        AttemptGuardService.AttemptEntry attempts = attemptGuardService.verificationAttempt(person.getId());
        attempts.touch();
        int maxAttempts = passwordResetPolicyService.maxAttempts();

        if (attempts.count.get() >= maxAttempts) {
            log.warn("[VERIFY_EMAIL] Compteur saturé ({}/{}) uid={} : suppression du code", attempts.count.get(), maxAttempts, uid);
            cerbereConfirmationRepository.deletePendingEmailVerificationByPersonId(person.getId());
            attemptGuardService.clearVerificationAttempt(person.getId());
            throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de vérification.");
        }

        if (optConfirmation.isEmpty()) {
            int currentAttempt = attempts.count.incrementAndGet();
            log.info("[VERIFY_EMAIL] Mauvais code, tentative {}/{} pour uid={}", currentAttempt, maxAttempts, uid);
            if (currentAttempt > maxAttempts) {
                cerbereConfirmationRepository.deletePendingEmailVerificationByPersonId(person.getId());
                attemptGuardService.clearVerificationAttempt(person.getId());
                throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de vérification.");
            }
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code invalide ou déjà utilisé", uid);
            throw new InvalidCodeException("Le code de vérification est incorrect ou a déjà été utilisé.");
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code expiré (limite={})", uid, confirmation.getLimite());
            cerbereConfirmationRepository.delete(confirmation);
            attemptGuardService.clearVerificationAttempt(person.getId());
            throw new CodeExpiredException("Le code de vérification a expiré. Veuillez en demander un nouveau.");
        }

        String email = confirmation.getMail();
        personneService.updateEmail(uid, email);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        attemptGuardService.clearVerificationAttempt(person.getId());

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

        String hashedCode = verificationCodeService.hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);

        if (uid == null || uid.isBlank()) {
            AttemptGuardService.ResetChallenge challenge = resetToken == null ? null : attemptGuardService.resetChallenge(resetToken);
            if (challenge == null || challenge.expiresAtMs <= System.currentTimeMillis()) {
                if (challenge != null) {
                    attemptGuardService.removeResetChallenge(resetToken);
                }
                throw new InvalidCodeException("Le code de réinitialisation est incorrect ou a déjà été utilisé. Veuillez demander un nouveau code.");
            }
            uid = challenge.uid;
        }

        if (uid != null && !uid.isBlank()) {
            // Cas UID connu : résolution par uid + code
            APersonne resolvedPerson = aPersonneRepository.findByUid(uid);
            if (resolvedPerson == null) {
                throw new InvalidCodeException("Aucun compte associé à cet identifiant");
            }
            person = resolvedPerson;

            confirmation = validatePendingCode(
                    resolvedPerson,
                    hashedCode,
                    "PROCESS_RESET_PASSWORD",
                    "Trop de tentatives échouées. Veuillez demander un nouveau code de réinitialisation.",
                    "Le code de réinitialisation est incorrect ou a déjà été utilisé. Veuillez demander un nouveau code.",
                    "Le code de réinitialisation a expiré. Veuillez demander un nouveau code.",
                    () -> cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(resolvedPerson.getId(), hashedCode),
                    () -> cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(resolvedPerson.getId()));
        }

        assertAccountActive(person, "PROCESS_RESET_PASSWORD");

        PersonneDTO personneDTO = loadProfileOrThrow(person);

        passwordResetPolicyService.assertPasswordResetAllowed(personneDTO, person.getUid());

        boolean charteRequise = charteService.isCharteRequired(person);
        log.info("[PROCESS_RESET_PASSWORD] uid={} charteRequise={} (validationcharteservice) charteAccepted={}",
                person.getUid(), charteRequise, charteAccepted);
        if (charteRequise) {
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

        attemptGuardService.clearResetAttempt(person.getId());
        if (resetToken != null) {
            attemptGuardService.removeResetChallenge(resetToken);
        }

        personneService.clearUserCaches(person.getUid());

        log.info("[PROCESS_RESET_PASSWORD] Succès uid={}", person.getUid());
    }

    /**
     * Fabrique la confirmation de code : réutilise la confirmation en attente du type donné si elle existe,
     * sinon en crée une nouvelle rattachée à la personne. Positionne le code hashé, le destinataire et la
     * limite puis persiste. Partagé entre le parcours public et le parcours « mot de passe réseau ».
     */
    private void storeOrReuseConfirmation(APersonne person, List<CerbereConfirmation> existing,
            String hashedCode, String recipient, Date limite, String logPrefix) {
        CerbereConfirmation confirmation;
        if (!existing.isEmpty() && existing.get(0).getConfirmation() == null) {
            confirmation = existing.get(0);
            log.info("[{}] Réutilisation de la confirmation existante id={}", logPrefix, confirmation.getId());
        } else {
            confirmation = new CerbereConfirmation();
            confirmation.setAPersonne(person);
            confirmation.setEditor(person);
        }
        confirmation.setCode(hashedCode);
        confirmation.setMail(recipient);
        confirmation.setLimite(limite);
        confirmation.setConfirmation(null);
        cerbereConfirmationRepository.save(confirmation);
    }

    /**
     * Garde-fou partagé d'application d'un code (anti-bruteforce + expiration) pour les parcours de
     * réinitialisation/mot de passe réseau : compteur de tentatives, résolution de la confirmation en attente,
     * rejet d'un code erroné/expiré. La suppression des confirmations en attente et la recherche du code sont
     * fournies par l'appelant (chaque parcours travaille sur son propre type de confirmation).
     *
     * @return la confirmation valide, non expirée
     */
    private CerbereConfirmation validatePendingCode(APersonne person, String hashedCode, String logPrefix,
            String tooManyAttemptsMessage, String wrongCodeMessage, String expiredMessage,
            Supplier<Optional<CerbereConfirmation>> pendingCodeLookup, Runnable deletePendingCampaign) {
        AttemptGuardService.AttemptEntry attempts = attemptGuardService.resetAttempt(person.getId());
        attempts.touch();
        int maxAttempts = passwordResetPolicyService.maxAttempts();

        if (attempts.count.get() >= maxAttempts) {
            log.warn("[{}] Compteur saturé ({}/{}) uid={}", logPrefix, attempts.count.get(), maxAttempts, person.getUid());
            deletePendingCampaign.run();
            attemptGuardService.clearResetAttempt(person.getId());
            throw new MaxAttemptsExceededException(tooManyAttemptsMessage);
        }

        Optional<CerbereConfirmation> optConfirmation = pendingCodeLookup.get();

        if (optConfirmation.isEmpty()) {
            int currentAttempt = attempts.count.incrementAndGet();
            log.info("[{}] Mauvais code, tentative {}/{} pour uid={}", logPrefix, currentAttempt, maxAttempts, person.getUid());
            if (currentAttempt > maxAttempts) {
                log.warn("[{}] Nombre max de tentatives dépassé uid={}, suppression du code", logPrefix, person.getUid());
                deletePendingCampaign.run();
                attemptGuardService.clearResetAttempt(person.getId());
                throw new MaxAttemptsExceededException(tooManyAttemptsMessage);
            }
            throw new InvalidCodeException(wrongCodeMessage);
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[{}] Code expiré uid={}", logPrefix, person.getUid());
            cerbereConfirmationRepository.delete(confirmation);
            attemptGuardService.clearResetAttempt(person.getId());
            throw new CodeExpiredException(expiredMessage);
        }
        return confirmation;
    }

    /**
     * Parcours « mot de passe réseau » pour un compte déjà authentifié dans Mon Compte (uid issu du jeton
     * Soffit, jamais du corps de requête). Conforme à l'ancienne application Cerbère : un compte CVDL ntPass
     * sans mot de passe local stocké ({@code noOldPass}) ne fournit ni ancien mot de passe ni code de
     * vérification — il change directement son mot de passe réseau (nouveau + confirmation). L'état du compte
     * doit être Valide ; la charte n'est pas re-demandée (l'ancien écran ne la redemandait pas non plus).
     *
     * @param uid identifiant de l'utilisateur authentifié (provenant du jeton, jamais du corps de requête)
     */
    @Transactional
    public void changeNetworkPassword(String uid, String newPassword, String confirmPassword) {
        log.info("[NETWORK_PASSWORD_RESET] Début changeNetworkPassword uid={}", uid);

        if (uid == null || uid.isBlank()) {
            throw new InvalidCodeException("Le changement de mot de passe réseau a échoué.");
        }

        APersonne person = aPersonneRepository.findByUidWithLock(uid);
        if (person == null) {
            throw new InactiveAccountException("Aucun compte associé à cet identifiant");
        }

        assertAccountActive(person, "NETWORK_PASSWORD_RESET");

        PersonneDTO personneDTO = loadProfileOrThrow(person);

        // Réservé aux comptes à mot de passe réseau seul : un compte disposant d'un mot de passe local
        // stocké doit passer par le changement avec l'ancien mot de passe.
        if (!passwordService.isNoOldPassEligible(personneDTO)) {
            log.warn("[NETWORK_PASSWORD_RESET] REFUSÉ pour l'utilisateur [{}] - Raison : compte non éligible au mot de passe réseau seul", uid);
            throw new InvalidCodeException(
                    "Ce parcours est réservé aux comptes à mot de passe réseau seul. Si votre compte dispose d'un mot de passe local, utilisez le changement avec votre ancien mot de passe.");
        }

        // Même règle de réinitialisation que le parcours public : CVDL ntPass autorisé.
        passwordResetPolicyService.assertPasswordResetAllowed(personneDTO, person.getUid());

        passwordService.resetPassword(personneDTO, newPassword, confirmPassword);

        personneService.clearUserCaches(person.getUid());

        log.info("[NETWORK_PASSWORD_RESET] Succès uid={}", person.getUid());
    }

    /**
     * Statut du parcours « mot de passe réseau » du compte authentifié, exposé au portail Mon Compte afin
     * qu'il affiche l'écran de changement direct pour les comptes éligibles (CVDL ntPass sans mot de passe
     * local stocké et état Valide). {@code ntPass} n'étant pas présent dans le jeton OIDC, l'éligibilité se
     * calcule côté serveur.
     *
     * @param uid identifiant de l'utilisateur authentifié (provenant du jeton, jamais du corps de requête)
     * @return {@link NetworkPasswordResetStatusDTO} (jamais {@code null} ; uid inconnu ⇒ non éligible)
     */
    public NetworkPasswordResetStatusDTO getNetworkPasswordResetStatus(String uid) {
        log.info("[NETWORK_PASSWORD_RESET] Statut uid={}", uid);

        if (uid == null || uid.isBlank()) {
            return new NetworkPasswordResetStatusDTO(false);
        }

        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            log.warn("[NETWORK_PASSWORD_RESET] uid={} inconnu, statut non éligible", uid);
            return new NetworkPasswordResetStatusDTO(false);
        }

        boolean eligible = false;
        PersonneDTO personneDTO = personneService.getUserByUid(uid);
        if (personneDTO != null) {
            if (passwordResetPolicyService.isInactiveAccount(person)) {
                log.info("[NETWORK_PASSWORD_RESET] Compte non actif uid={}, statut non éligible", uid);
            } else {
                eligible = passwordService.isNoOldPassEligible(personneDTO);
            }
        } else {
            log.warn("[NETWORK_PASSWORD_RESET] Profil non chargeable pour uid={}, statut non éligible", uid);
        }

        log.info("[NETWORK_PASSWORD_RESET] Statut uid={} eligible={}", uid, eligible);
        return new NetworkPasswordResetStatusDTO(eligible);
    }

}