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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class EmailVerificationService {

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

    private String hashCode(String code) {
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
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    @Transactional
    public void sendVerificationEmail(String uid, String email) {
        APersonne person = aPersonneRepository.findByUid(uid);
        if (person == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : " + uid);
        }

        String code = generateVerificationCode();
        String hashedCode = hashCode(code);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, (int) mailProperties.getVerification().getExpiryHours());
        Date limite = cal.getTime();

        cerbereConfirmationRepository.deletePendingByPersonId(person.getId());

        CerbereConfirmation confirmation = new CerbereConfirmation();
        confirmation.setAPersonne(person);
        confirmation.setCode(hashedCode);
        confirmation.setMail(email);
        confirmation.setLimite(limite);
        confirmation.setConfirmation(null);
        confirmation.setEditor(person);
        cerbereConfirmationRepository.save(confirmation);

        sendEmail(email, code);

        log.info("Email de verification envoye a {} pour l'utilisateur [uid={}]", email, uid);
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromEmail());
        message.setTo(to);
        message.setSubject("Verification de votre adresse email");
        message.setText(
                "Bonjour,\n\n"
                        + "Vous avez demande la verification de votre adresse email.\n\n"
                        + "Votre code de verification est : " + code + "\n\n"
                        + "Veuillez saisir ce code sur la page de verification pour confirmer votre adresse.\n\n"
                        + "Ce code est valable " + mailProperties.getVerification().getExpiryHours() + " heures.\n\n"
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
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : utilisateur introuvable", uid);
            throw new IllegalArgumentException("Utilisateur introuvable : " + uid);
        }

        String hashedCode = hashCode(code);
        Optional<CerbereConfirmation> optConfirmation =
                cerbereConfirmationRepository.findPendingByPersonIdAndCode(person.getId(), hashedCode);

        if (optConfirmation.isEmpty()) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code invalide ou déjà utilisé (code={})", uid, code);
            throw new IllegalArgumentException("Code de verification invalide ou deja utilise");
        }

        CerbereConfirmation confirmation = optConfirmation.get();

        if (confirmation.getLimite().before(new Date())) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : code expiré (limite={})", uid, confirmation.getLimite());
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
