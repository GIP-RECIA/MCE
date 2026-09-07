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

    public String getVerificationFrontendUrl() {
        return verificationCodeService.getVerificationFrontendUrl();
    }

    public String generateVerificationCode() {
        return verificationCodeService.generateVerificationCode();
    }

    public void purgeStaleAttemptEntries() {
        attemptGuardService.purgeStaleAttemptEntries();
    }

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByUid(uid);
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
        if (passwordResetPolicyService.isInactiveAccount(person)) {
            log.warn("[RESET_PASSWORD] État '{}' ≠ '{}' pour uid={}", person.getEtat(),
                    PasswordResetPolicyService.VALID_ACCOUNT_STATE, uid);
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }

        PersonneDTO personneDTO = personneService.getUserByUid(uid);
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }

        passwordResetPolicyService.assertPasswordResetAllowed(personneDTO, uid);

        // Génération du code
        String code = verificationCodeService.generateVerificationCode();
        String hashedCode = verificationCodeService.hashWithPrefix(code, ConfirmationType.PASSWORD_RESET);

        Date limite = verificationCodeService.calculateExpiryDate();

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
        attemptGuardService.clearResetAttempt(person.getId());

        final String recipient = email;
        confirmationMailSender.sendAfterCommit(() -> confirmationMailSender.sendResetEmail(recipient, code));
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
        APersonne person = aPersonneRepository.findByUid(uid);
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
            person = aPersonneRepository.findByUid(uid);
            if (person == null) {
                throw new InvalidCodeException("Aucun compte associé à cet identifiant");
            }

            AttemptGuardService.AttemptEntry attempts = attemptGuardService.resetAttempt(person.getId());
            attempts.touch();
            int maxAttempts = passwordResetPolicyService.maxAttempts();

            if (attempts.count.get() >= maxAttempts) {
                log.warn("[PROCESS_RESET_PASSWORD] Compteur saturé ({}/{}) uid={}", attempts.count.get(), maxAttempts, uid);
                cerbereConfirmationRepository.deletePendingPasswordResetByPersonId(person.getId());
                attemptGuardService.clearResetAttempt(person.getId());
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
                    attemptGuardService.clearResetAttempt(person.getId());
                    throw new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de réinitialisation.");
                }
                throw new InvalidCodeException("Le code de réinitialisation est incorrect ou a déjà été utilisé. Veuillez demander un nouveau code.");
            }

            confirmation = optConfirmation.get();
        }

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[PROCESS_RESET_PASSWORD] Code expiré uid={}", person.getUid());
            cerbereConfirmationRepository.delete(confirmation);
            attemptGuardService.clearResetAttempt(person.getId());
            throw new CodeExpiredException("Le code de réinitialisation a expiré. Veuillez demander un nouveau code.");
        }

        if (passwordResetPolicyService.isInactiveAccount(person)) {
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }

        PersonneDTO personneDTO = personneService.getUserByUid(person.getUid());
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }

        passwordResetPolicyService.assertPasswordResetAllowed(personneDTO, person.getUid());

        log.info("[PROCESS_RESET_PASSWORD] uid={} validationCharte={} charteAccepted={}",
                person.getUid(), person.getValidationCharte(), charteAccepted);
        if (charteService.isCharteRequired(person)) {
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

}