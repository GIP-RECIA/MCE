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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envoi des emails de confirmation (vérification d'email et réinitialisation de mot de passe) à partir des
 * templates configurés, avec envoi différé au commit de la transaction.
 */
@Service
@Slf4j
public class ConfirmationMailSender {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MailProperties mailProperties;

    public void sendVerificationEmail(String to, String code) {
        sendEmailWithTemplate(to, code, mailProperties.getTemplates().getVerification(),
                "Erreur lors de l'envoi de l'email de vérification à {} : {}");
    }

    public void sendResetEmail(String to, String code) {
        sendEmailWithTemplate(to, code, mailProperties.getTemplates().getReset(),
                "Erreur lors de l'envoi du code de réinitialisation à {} : {}");
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

    /**
     * Diffère l'envoi SMTP au commit de la transaction : un rollback ne doit pas laisser partir un code inexistant, et le SMTP lent ne doit pas retenir la
     * connexion DB. Hors transaction (contexte sans synchronisation), l'envoi est immédiat.
     */
    public void sendAfterCommit(Runnable emailAction) {
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

}