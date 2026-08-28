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
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;
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

    private final ConcurrentHashMap<Long, AttemptEntry> verificationAttempts = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "PT15M")
    public void purgeStaleAttemptEntries() {
        long now = System.currentTimeMillis();
        int before = resetAttempts.size() + verificationAttempts.size();
        resetAttempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        verificationAttempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        int removed = before - resetAttempts.size() - verificationAttempts.size();
        if (removed > 0) {
            log.info("Purge des compteurs de tentatives expirés : {} entrée(s) supprimée(s)", removed);
        }
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
    private PasswordService passwordService;

    private final SecureRandom secureRandom = new SecureRandom();

    private String hashWithPrefix(String code, ConfirmationType type) {
        return type.getCodePrefix() + sha256(code);
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

        String code = generateVerificationCode();
        String hashedCode = hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, (int) mailProperties.getVerification().getExpiryHours());
        Date limite = cal.getTime();

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
        MailProperties.EmailTemplates.Template tpl = mailProperties.getTemplates().getVerification();
        String expiryHours = String.valueOf(mailProperties.getVerification().getExpiryHours());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromEmail());
        message.setTo(to);
        message.setSubject(tpl.getSubject());
        message.setText(tpl.getBody()
                .replace("{{code}}", code)
                .replace("{{expiryHours}}", expiryHours));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Erreur lors de l'envoi de l'email de vérification à {} : {}", to, e.getMessage());
            throw new RuntimeException("Erreur lors de l'envoi de l'email de verification", e);
        }
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

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, (int) mailProperties.getVerification().getExpiryHours());
        Date limite = cal.getTime();

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
        if (sameEmail(candidate, person.getEmail()) || sameEmail(candidate, person.getEmailPersonnel())) {
            return true;
        }
        if (cerbereConfirmationRepository.findConfirmedByPersonId(person.getId()).stream()
                .anyMatch(c -> sameEmail(candidate, c.getMail()))) {
            return true;
        }
        // Source LDAP : l'email principal de l'annuaire peut différer de celui stocké en base.
        return sameEmail(candidate, ldapEmail(person));
    }

    private boolean sameEmail(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b.trim());
    }

    /**
     * Récupère l'email principal de l'utilisateur depuis l'annuaire LDAP. Retourne {@code null} si la personne est absente de l'annuaire ou sans email.
     */
    private String ldapEmail(APersonne person) {
        try {
            IExternalUser ldapUser = personneService.retrievePersonLdap(person.getUid());
            if (ldapUser != null && ldapUser.getEmail() != null && !ldapUser.getEmail().isBlank()) {
                return ldapUser.getEmail().trim();
            }
        } catch (Exception e) {
            log.warn("[RESET_PASSWORD] Impossible de récupérer l'email LDAP pour uid={}", person.getUid(), e);
        }
        return null;
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
        MailProperties.EmailTemplates.Template tpl = mailProperties.getTemplates().getReset();
        String expiryHours = String.valueOf(mailProperties.getVerification().getExpiryHours());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromEmail());
        message.setTo(to);
        message.setSubject(tpl.getSubject());
        message.setText(tpl.getBody()
                .replace("{{code}}", code)
                .replace("{{expiryHours}}", expiryHours));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Erreur lors de l'envoi du code de réinitialisation à {} : {}", to, e.getMessage(), e);
            throw new RuntimeException("Erreur lors de l'envoi du code de reinitialisation", e);
        }
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
        log.info("[PROCESS_RESET_PASSWORD] Début uid={}", uid);

        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new InvalidCodeException("Aucun compte associé à cet identifiant");
        }

        AttemptEntry attempts = resetAttempts.computeIfAbsent(person.getId(), k -> new AttemptEntry());
        attempts.touch();
        int maxAttempts = mceProperties.getSecurity().getResetPolicy().getMaxAttempts();

        // Compteur saturé par des mauvais codes : le code en attente est détruit,
        // même si celui soumis cette fois est le bon.
        if (attempts.count.get() >= maxAttempts) {
            log.warn("[PROCESS_RESET_PASSWORD] Compteur saturé ({}/{}) uid={} : suppression du code",
                    attempts.count.get(), maxAttempts, uid);
            cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(person.getId());
            resetAttempts.remove(person.getId());
            throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de réinitialisation.");
        }

        String hashedCode = hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);
        Optional<CerbereConfirmation> optConfirmation = cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(person.getId(),
                hashedCode);

        // Seuls les codes incorrects consomment une tentative : les échecs bénins
        // (charte non acceptée, mot de passe faible…) ne doivent pas pénaliser l'utilisateur.
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

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[PROCESS_RESET_PASSWORD] Code expiré uid={}", uid);
            cerbereConfirmationRepository.delete(confirmation);
            resetAttempts.remove(person.getId());
            throw new CodeExpiredException("Le code de réinitialisation a expiré. Veuillez demander un nouveau code.");
        }

        if (!VALID_ACCOUNT_STATE.equals(person.getEtat())) {
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }

        PersonneDTO personneDTO = personneService.getUserByUid(uid);
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }

        assertPasswordResetAllowed(personneDTO, uid);

        if (!personneDTO.isCharteValide()) {
            if (!charteAccepted) {
                throw new CharteNotAcceptedException("Vous devez accepter les conditions générales d'utilisation avant de changer votre mot de passe");
            }
            personneService.signCharte(uid);
        }

        passwordService.resetPassword(personneDTO, newPassword, confirmPassword);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(person.getId());

        resetAttempts.remove(person.getId());

        personneService.clearUserCaches(uid);

        log.info("[PROCESS_RESET_PASSWORD] Succès uid={}", uid);
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
     * <li>sans connectOk ni ntPass, aucun mode d'authentification local n'existe (même règle que UserDTOFactoryImpl.computePassEditable).</li>
     * </ul>
     */
    private void assertPasswordResetAllowed(PersonneDTO personneDTO, String uid) {
        EnumPublic pub = personneDTO.getEnumPublic();
        if (pub == null) {
            log.warn("[PASSWORD_RESET] Profil non défini pour uid={}, utilisation du profil par défaut AUTRE", uid);
            pub = EnumPublic.AUTRE;
        }

        if (pub.isEduconnect()) {
            log.warn("[PASSWORD_RESET] Refus : compte EduConnect uid={}", uid);
            throw new InvalidCodeException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
        }
        if (!pub.isConnectOk() && !personneDTO.isNtPass()) {
            log.warn("[PASSWORD_RESET] Refus : ni connectOk ni ntPass uid={}", uid);
            throw new InvalidCodeException("Aucune réinitialisation possible pour ce compte : aucun mode d'authentification local n'est actif");
        }
    }

}
