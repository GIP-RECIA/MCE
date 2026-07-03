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

import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class EmailVerificationService {

    private static final int CODE_BYTES = 32;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private PersonneService personneService;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateVerificationCode() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : " + uid);
        }

        String code = generateVerificationCode();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, (int) mailProperties.getVerification().getExpiryHours());
        Date limite = cal.getTime();

        cerbereConfirmationRepository.deletePendingByPersonId(person.getId());

        CerbereConfirmation confirmation = new CerbereConfirmation();
        confirmation.setAPersonne(person);
        confirmation.setCode(code);
        confirmation.setMail(email);
        confirmation.setLimite(limite);
        confirmation.setConfirmation(null);
        confirmation.setEditor(person);
        cerbereConfirmationRepository.save(confirmation);

        sendEmail(email, code, uid);

        log.info("Email de verification envoye a {} pour l'utilisateur [uid={}]", email, uid);
    }

    private void sendEmail(String to, String code, String uid) {
        String verificationUrl = mailProperties.getVerification().getBaseUrl()
                + "?uid=" + uid + "&code=" + code;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromEmail());
        message.setTo(to);
        message.setSubject("Verification de votre adresse email");
        message.setText(
                "Bonjour,\n\n"
                        + "Vous avez demande la verification de votre adresse email.\n\n"
                        + "Veuillez cliquer sur le lien suivant pour confirmer votre adresse :\n"
                        + verificationUrl + "\n\n"
                        + "Ce lien est valable " + mailProperties.getVerification().getExpiryHours() + " heures.\n\n"
                        + "Si vous n'etes pas a l'origine de cette demande, ignorez cet email.\n\n"
                        + "Cordialement,\n"
                        + "Votre equipe support");

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Erreur lors de l'envoi de l'email de verification a {} : {}", to, e.getMessage());
            throw new RuntimeException("Erreur lors de l'envoi de l'email de verification", e);
        }
    }

    @Transactional
    public void verifyEmail(String uid, String code) {
        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : " + uid);
        }

        Optional<CerbereConfirmation> optConfirmation =
                cerbereConfirmationRepository.findPendingByPersonIdAndCode(person.getId(), code);

        if (optConfirmation.isEmpty()) {
            throw new IllegalArgumentException("Code de verification invalide ou deja utilise");
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            cerbereConfirmationRepository.delete(confirmation);
            throw new IllegalArgumentException("Le code de verification a expire");
        }

        String email = confirmation.getMail();

        personneService.updateEmail(uid, email);

        confirmation.setConfirmation(new Date());
        cerbereConfirmationRepository.save(confirmation);

        log.info("Email verifie avec succes pour l'utilisateur [uid={}] -> {}", uid, email);
    }

}
