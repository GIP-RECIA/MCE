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

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.enums.ConfirmationType;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.beans.AttemptEntry;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service de vérification de mail (envoi et validation de code)
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
    private ResendPolicyService resendPolicyService;

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByUid(uid);

        // On ne renvoie pas un code si on vient d'en envoyer un autre récemment
        List<CerbereConfirmation> cerbereConfirmationsForPerson = cerbereConfirmationRepository.findPendingEmailVerificationByPersonId(person.getId());
        resendPolicyService.assertResendAllowed(cerbereConfirmationsForPerson, uid, "VERIFY_EMAIL");

        // Avant tout, on supprime tous les codes encore valides
        cerbereConfirmationRepository.deletePendingEmailVerificationByPersonId(person.getId());

        // On créé le nouveau code de confirmation
        String code = verificationCodeService.generateVerificationCode();
        String hashedCode = verificationCodeService.hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);
        Date limite = verificationCodeService.calculateExpiryDate();
        CerbereConfirmation confirmation = new CerbereConfirmation();
        confirmation.setAPersonne(person);
        confirmation.setCode(hashedCode);
        confirmation.setMail(email);
        confirmation.setLimite(limite);
        confirmation.setConfirmation(null);
        confirmation.setEditor(person);
        cerbereConfirmationRepository.save(confirmation);

        // Nouveau code de vérification : le compteur de tentatives repart de zéro
        attemptGuardService.clearVerificationAttempt(person.getId());

        // Envoyer le mail de vérification
        final String displayName = person.getDisplayName();
        confirmationMailSender.sendVerificationEmail(email, displayName, code);

        log.info("Email de vérification envoyé à {} pour l'utilisateur [uid={}]", email, uid);
    }

    @Transactional(noRollbackFor = MaxAttemptsExceededException.class)
    public void verifyEmail(String uid, String code, boolean doForward) {
        APersonne person = aPersonneRepository.findByUid(uid);
        String hashedCode = verificationCodeService.hashWithPrefix(code, ConfirmationType.EMAIL_VERIFICATION);
        Optional<CerbereConfirmation> optConfirmation = cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(person.getId(), hashedCode);

        // On vérifie qu'on a pas fait trop de tentatives de vérification échouées
        AttemptEntry attempts = attemptGuardService.verificationAttempt(person.getId());
        attempts.touch();
        final int maxAttempts = resendPolicyService.maxAttempts();
        if (optConfirmation.isEmpty()) {
            final int currentAttempt = attempts.count.incrementAndGet();
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

        // On vérifie qu'on a pas validé le code trop tard
        if (confirmation.getLimite().before(new Date())) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code expiré (limite={})", uid, confirmation.getLimite());
            cerbereConfirmationRepository.delete(confirmation);
            attemptGuardService.clearVerificationAttempt(person.getId());
            throw new CodeExpiredException("Le code de vérification a expiré. Veuillez en demander un nouveau.");
        }

        // On vérifie que la personne peut modifier le mail puis on applique la modification
        String email = confirmation.getMail();
        personneService.assertUpdateEmail(uid, email);
        person.setEmailPersonnel(email);
        person.setDoForward(doForward);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        attemptGuardService.clearVerificationAttempt(person.getId());

        log.info("Email vérifié avec succès pour l'utilisateur [uid={}] -> {} (mail personnel enregistré)", uid, email);
    }

}
