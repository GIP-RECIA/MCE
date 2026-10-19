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
import fr.recia.mce.api.escomceapi.services.beans.AttemptEntry;
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
    private VerificationCodeService verificationCodeService;

    @Autowired
    private ConfirmationMailSender confirmationMailSender;

    @Autowired
    private AttemptGuardService attemptGuardService;

    @Autowired
    private PasswordResetPolicyService passwordResetPolicyService;

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new InactiveAccountException("Aucun compte associé à cet identifiant");
        }

        // Anti-double-clic
        passwordResetPolicyService.assertResendAllowed(cerbereConfirmationRepository.findPendingEmailVerificationByPersonId(person.getId()), uid, "VERIFY_EMAIL");

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
        AttemptEntry attempts = attemptGuardService.verificationAttempt(person.getId());
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
        // TODO : à quoi ça sert de le faire 2 fois ?
        personneService.updateEmail(uid, email);
        person.setEmailPersonnel(email);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        attemptGuardService.clearVerificationAttempt(person.getId());

        log.info("Email vérifié avec succès pour l'utilisateur [uid={}] -> {} (mail personnel enregistré)", uid, email);
    }

}
