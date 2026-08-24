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
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ConcurrentHashMap<Long, AtomicInteger> resetAttempts = new ConcurrentHashMap<>();

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

        sendEmail(email, code);

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

        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new InvalidCodeException("Aucun compte associé à cet identifiant");
        }

        if (profil != null && !profil.isBlank()) {
            String dbCategorie = person.getCategorie();
            if (dbCategorie == null || !profil.equalsIgnoreCase(dbCategorie)) {
                log.warn("[RESET_PASSWORD] Profil incohérent : front='{}' vs DB='{}' uid={}", profil, dbCategorie, uid);
                throw new InvalidCodeException("Profil incohérent avec votre compte");
            }
        }

        // L'email fourni doit appartenir au compte : sans ce contrôle, quiconque
        // connaît un uid recevrait le code sur sa propre adresse.
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

        EnumPublic pub = personneDTO.getEnumPublic();
        if (pub == null) {
            log.warn("[RESET_PASSWORD] Profil non défini pour uid={}, utilisation du profil par défaut AUTRE", uid);
            pub = EnumPublic.AUTRE;
        }

        // Comptes sans mot de passe local réinitialisable :
        // - EduConnect (parents/élèves éduc nat) : le mot de passe se gère sur le portail EduConnect ;
        // - sans connectOk ni ntPass, aucun mode d'authentification local n'existe
        //   (même règle que UserDTOFactoryImpl.computePassEditable).
        if (pub.isEduconnect()) {
            log.warn("[RESET_PASSWORD] Refus : compte EduConnect uid={}", uid);
            throw new InvalidCodeException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
        }
        if (!pub.isConnectOk() && !personneDTO.isNtPass()) {
            log.warn("[RESET_PASSWORD] Refus : ni connectOk ni ntPass uid={}", uid);
            throw new InvalidCodeException("Aucune réinitialisation possible pour ce compte : aucun mode d'authentification local n'est actif");
        }

        // Génération du code
        String code = generateVerificationCode();
        String hashedCode = hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, (int) mailProperties.getVerification().getExpiryHours());
        Date limite = cal.getTime();

        // Réutilisation ou création
        List<CerbereConfirmation> existing = cerbereConfirmationRepository.findLatestPasswordResetByPersonId(person.getId());
        CerbereConfirmation confirmation;
        if (!existing.isEmpty()) {
            CerbereConfirmation lastExisting = existing.get(0);
            if (lastExisting.getConfirmation() != null) {
                log.info("[RESET_PASSWORD] RESET id={} déjà consommé, création d'une nouvelle confirmation", lastExisting.getId());
                confirmation = new CerbereConfirmation();
                confirmation.setAPersonne(person);
                confirmation.setEditor(person);
            } else {
                confirmation = lastExisting;
            }
            confirmation.setCode(hashedCode);
            confirmation.setMail(email);
            confirmation.setLimite(limite);
            confirmation.setConfirmation(null);
        } else {
            confirmation = new CerbereConfirmation();
            confirmation.setAPersonne(person);
            confirmation.setCode(hashedCode);
            confirmation.setMail(email);
            confirmation.setLimite(limite);
            confirmation.setConfirmation(null);
            confirmation.setEditor(person);
        }
        cerbereConfirmationRepository.save(confirmation);

        sendResetEmail(email, code);
        log.info("[RESET_PASSWORD] Code envoyé à {} pour uid={}", email, uid);
    }

    /**
     * L'email fourni doit correspondre (insensible à la casse) à l'email du compte,
     * à l'email personnel, ou à un email confirmé via Cerbère.
     */
    private boolean isEmailAssociatedWithAccount(APersonne person, String providedEmail) {
        if (providedEmail == null || providedEmail.isBlank()) {
            return false;
        }
        String candidate = providedEmail.trim();
        if (sameEmail(candidate, person.getEmail()) || sameEmail(candidate, person.getEmailPersonnel())) {
            return true;
        }
        return cerbereConfirmationRepository.findConfirmedByPersonId(person.getId()).stream()
                .anyMatch(c -> sameEmail(candidate, c.getMail()));
    }

    private boolean sameEmail(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b.trim());
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

        if (optConfirmation.isEmpty()) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code invalide ou déjà utilisé", uid);
            throw new InvalidCodeException("Le code de vérification est incorrect ou a déjà été utilisé.");
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code expiré (limite={})", uid, confirmation.getLimite());
            cerbereConfirmationRepository.delete(confirmation);
            throw new CodeExpiredException("Le code de vérification a expiré. Veuillez en demander un nouveau.");
        }

        String email = confirmation.getMail();
        personneService.updateEmail(uid, email);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        log.info("Email vérifié avec succès pour l'utilisateur [uid={}] -> {}", uid, email);
    }

    @Transactional
    public void processResetPassword(String uid, String code, String newPassword, String confirmPassword, boolean charteAccepted) {
        log.info("[PROCESS_RESET_PASSWORD] Début uid={}", uid);

        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new InvalidCodeException("Aucun compte associé à cet identifiant");
        }

        String hashedCode = hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);
        Optional<CerbereConfirmation> optConfirmation = cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(person.getId(), hashedCode);

        if (optConfirmation.isEmpty()) {
            throw new InvalidCodeException("Le code de réinitialisation est incorrect ou a déjà été utilisé. Veuillez demander un nouveau code.");
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        AtomicInteger attempts = resetAttempts.computeIfAbsent(person.getId(), k -> new AtomicInteger(0));
        int currentAttempt = attempts.incrementAndGet();
        int maxAttempts = mceProperties.getSecurity().getResetPolicy().getMaxAttempts();
        log.info("[PROCESS_RESET_PASSWORD] Tentative {}/{} pour uid={}", currentAttempt, maxAttempts, uid);

        if (currentAttempt > maxAttempts) {
            log.warn("[PROCESS_RESET_PASSWORD] Nombre max de tentatives dépassé uid={}, suppression de la confirmation", uid);
            cerbereConfirmationRepository.delete(confirmation);
            resetAttempts.remove(person.getId());
            throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de réinitialisation.");
        }

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

}
