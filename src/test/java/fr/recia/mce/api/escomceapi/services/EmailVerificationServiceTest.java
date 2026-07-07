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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - EmailVerificationService")
class EmailVerificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private PersonneService personneService;

    @InjectMocks
    private EmailVerificationService service;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> mailCaptor;

    @Captor
    private ArgumentCaptor<CerbereConfirmation> confirmationCaptor;

    private APersonne person;
    private final String uid = "testUser";
    private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        person = new APersonne();
        person.setId(42L);
        person.setUid(uid);

        MailProperties.Verification verification = new MailProperties.Verification();
        verification.setExpiryHours(24);
        lenient().when(mailProperties.getVerification()).thenReturn(verification);
        lenient().when(mailProperties.getFromEmail()).thenReturn("noreply@mce.fr");
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
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("generateVerificationCode")
    class GenerateVerificationCodeTests {

        @Test
        @DisplayName("Génère un code à 6 chiffres")
        void generates6DigitCode() {
            String code = service.generateVerificationCode();
            assertThat(code).isNotNull().hasSize(6);
            assertThat(code).matches("\\d{6}");
        }
    }

    @Nested
    @DisplayName("sendVerificationEmail")
    class SendVerificationEmailTests {

        @Test
        @DisplayName("Succès : envoie l'email et sauvegarde la confirmation")
        void success() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendVerificationEmail(uid, email);

            verify(cerbereConfirmationRepository).deletePendingByPersonId(42L);
            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            verify(mailSender).send(mailCaptor.capture());

            CerbereConfirmation saved = confirmationCaptor.getValue();
            assertThat(saved.getAPersonne()).isEqualTo(person);
            assertThat(saved.getMail()).isEqualTo(email);
            assertThat(saved.getCode()).isNotNull().hasSize(64);
            assertThat(saved.getCode()).matches("[0-9a-f]{64}");
            assertThat(saved.getConfirmation()).isNull();
            assertThat(saved.getLimite()).isAfter(new Date());

            SimpleMailMessage msg = mailCaptor.getValue();
            assertThat(msg.getTo()).containsExactly(email);
            assertThat(msg.getFrom()).isEqualTo("noreply@mce.fr");
            assertThat(msg.getSubject()).contains("Verification");
            assertThat(msg.getText()).contains("Votre code de verification est :");
            assertThat(msg.getText()).doesNotContain("http");
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable")
        void userNotFound() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(null);

            assertThatThrownBy(() -> service.sendVerificationEmail(uid, email))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(uid);

            verifyNoInteractions(cerbereConfirmationRepository, mailSender);
        }

        @Test
        @DisplayName("Échec : MailException transformé en RuntimeException")
        void mailException() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            doThrow(new MailException("SMTP error") {}).when(mailSender).send(any(SimpleMailMessage.class));

            assertThatThrownBy(() -> service.sendVerificationEmail(uid, email))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Erreur lors de l'envoi");

            verify(cerbereConfirmationRepository).save(any());
        }
    }

    @Nested
    @DisplayName("verifyEmail")
    class VerifyEmailTests {

        @Test
        @DisplayName("Succès : confirme l'email et appelle updateEmail")
        void success() {
            String code = "123456";
            String hashedCode = sha256(code);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode(hashedCode);
            confirmation.setMail(email);
            confirmation.setLimite(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingByPersonIdAndCode(42L, hashedCode))
                    .thenReturn(Optional.of(confirmation));

            service.verifyEmail(uid, code);

            verify(personneService).updateEmail(uid, email);
            assertThat(confirmation.getConfirmation()).isNotNull();
            verify(cerbereConfirmationRepository).save(confirmation);
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable")
        void userNotFound() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(null);

            assertThatThrownBy(() -> service.verifyEmail(uid, "code"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(uid);

            verifyNoInteractions(cerbereConfirmationRepository, personneService);
        }

        @Test
        @DisplayName("Échec : code invalide")
        void invalidCode() {
            String badCode = "999999";
            String hashedBadCode = sha256(badCode);

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingByPersonIdAndCode(42L, hashedBadCode))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(uid, badCode))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Code de verification invalide");

            verifyNoInteractions(personneService);
        }

        @Test
        @DisplayName("Échec : code expiré")
        void expiredCode() {
            String code = "654321";
            String hashedCode = sha256(code);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode(hashedCode);
            confirmation.setMail(email);
            confirmation.setLimite(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingByPersonIdAndCode(42L, hashedCode))
                    .thenReturn(Optional.of(confirmation));

            assertThatThrownBy(() -> service.verifyEmail(uid, code))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expire");

            verify(cerbereConfirmationRepository).delete(confirmation);
            verifyNoInteractions(personneService);
        }
    }
}
