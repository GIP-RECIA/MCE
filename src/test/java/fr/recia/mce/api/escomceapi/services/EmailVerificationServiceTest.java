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
import fr.recia.mce.api.escomceapi.configuration.bean.CharteProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.SecurityProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import fr.recia.mce.api.escomceapi.services.exception.ResendCooldownActiveException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private MCEProperties mceProperties;

    @Mock
    private PersonneService personneService;

    @Mock
    private PasswordService passwordService;

    // ChartService en spy : isCharteRequired(APersonne) exécute la vraie règle de domaine
    // (charte requise tant que validationCharte est null), comme l'ancien contrôle inline.
    @Spy
    private CharteService charteService = new CharteService();

    @Mock
    private CharteProperties charteProperties;

    @Mock
    private IUserDTOFactory userDTOFactory;

    @Spy
    private AttemptGuardService attemptGuardService = new AttemptGuardService();

    @Spy
    private VerificationCodeService verificationCodeService = new VerificationCodeService();

    @Spy
    private ConfirmationMailSender confirmationMailSender = new ConfirmationMailSender();

    @Spy
    private AccountEmailService accountEmailService = new AccountEmailService();

    @Spy
    private PasswordResetPolicyService passwordResetPolicyService = new PasswordResetPolicyService();

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
        verification.setCodeLength(6);
        lenient().when(mailProperties.getVerification()).thenReturn(verification);
        lenient().when(mailProperties.getFromEmail()).thenReturn("noreply@mce.fr");

        MailProperties.EmailTemplates templates = new MailProperties.EmailTemplates();
        MailProperties.EmailTemplates.Template verificationTemplate = new MailProperties.EmailTemplates.Template();
        verificationTemplate.setSubject("Verification");
        verificationTemplate.setBody("Votre code de verification est : {{code}}");
        templates.setVerification(verificationTemplate);
        MailProperties.EmailTemplates.Template resetTemplate = new MailProperties.EmailTemplates.Template();
        resetTemplate.setSubject("Réinitialisation");
        resetTemplate.setBody("Votre code de réinitialisation est : {{code}}");
        templates.setReset(resetTemplate);
        lenient().when(mailProperties.getTemplates()).thenReturn(templates);

        SecurityProperties security = new SecurityProperties();
        lenient().when(mceProperties.getSecurity()).thenReturn(security);
        lenient().when(userDTOFactory.canResetPassword(any(PersonneDTO.class))).thenAnswer(invocation -> {
            PersonneDTO dto = invocation.getArgument(0);
            EnumPublic pub = dto.getEnumPublic();
            return pub != null && !pub.isEduconnect() && (pub.isConnectOk() || dto.isNtPass());
        });

        ReflectionTestUtils.setField(verificationCodeService, "mailProperties", mailProperties);
        ReflectionTestUtils.setField(confirmationMailSender, "mailSender", mailSender);
        ReflectionTestUtils.setField(confirmationMailSender, "mailProperties", mailProperties);
        ReflectionTestUtils.setField(accountEmailService, "cerbereConfirmationRepository", cerbereConfirmationRepository);
        ReflectionTestUtils.setField(accountEmailService, "personneService", personneService);
        ReflectionTestUtils.setField(passwordResetPolicyService, "mailProperties", mailProperties);
        ReflectionTestUtils.setField(passwordResetPolicyService, "mceProperties", mceProperties);
        ReflectionTestUtils.setField(passwordResetPolicyService, "userDTOFactory", userDTOFactory);

        lenient().when(charteProperties.getDefaultUrl()).thenReturn("https://charte.example.fr");
        ReflectionTestUtils.setField(charteService, "aPersonneRepository", aPersonneRepository);
        ReflectionTestUtils.setField(charteService, "charteProperties", charteProperties);
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

    private AttemptGuardService.AttemptEntry entryWithCount(int n) {
        AttemptGuardService.AttemptEntry entry = new AttemptGuardService.AttemptEntry();
        entry.count.set(n);
        return entry;
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

        @Test
        @DisplayName("Génère un code à la longueur configurée (codeLength)")
        void generatesCodeWithConfiguredLength() {
            MailProperties.Verification verification = new MailProperties.Verification();
            verification.setExpiryHours(24);
            verification.setCodeLength(8);
            when(mailProperties.getVerification()).thenReturn(verification);

            String code = service.generateVerificationCode();

            assertThat(code).isNotNull().hasSize(8);
            assertThat(code).matches("\\d{8}");
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

            verify(cerbereConfirmationRepository).deletePendingEmailVerificationByPersonId(42L);
            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            verify(mailSender).send(mailCaptor.capture());

            CerbereConfirmation saved = confirmationCaptor.getValue();
            assertThat(saved.getAPersonne()).isEqualTo(person);
            assertThat(saved.getMail()).isEqualTo(email);
            assertThat(saved.getCode()).isNotNull().startsWith("VERIFY:");
            assertThat(saved.getCode()).matches("VERIFY:[0-9a-f]{64}");
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
        @DisplayName("Anti-double-clic : demande récente (< cooldown) → ResendCooldownActiveException")
        void antiDoubleClickBlocksRecentVerification() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonId(42L))
                    .thenReturn(List.of(pendingReset(42L, 1_000)));

            assertThatThrownBy(() -> service.sendVerificationEmail(uid, email))
                    .isInstanceOf(ResendCooldownActiveException.class)
                    .satisfies(e -> assertThat(((ResendCooldownActiveException) e).getRetryAfterSeconds()).isPositive());

            verify(cerbereConfirmationRepository, never()).deletePendingEmailVerificationByPersonId(42L);
            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable")
        void userNotFound() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(null);

            assertThatThrownBy(() -> service.sendVerificationEmail(uid, email))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Aucun compte associé");

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
            String hashedCode = "VERIFY:" + sha256(code);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode(hashedCode);
            confirmation.setMail(email);
            confirmation.setLimite(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(42L, hashedCode))
                    .thenReturn(Optional.of(confirmation));

            service.verifyEmail(uid, code);

            verify(personneService).updateEmail(uid, email);
            assertThat(confirmation.getConfirmation()).isNotNull();
            verify(cerbereConfirmationRepository).save(confirmation);
        }

        @Test
        @DisplayName("Test unitaire : la synchronisation diffère l'email jusqu'au commit")
        void verificationEmailDeferredUntilAfterCommit() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            TransactionSynchronizationManager.initSynchronization();
            try {
                service.sendVerificationEmail(uid, email);

                verify(mailSender, never()).send(any(SimpleMailMessage.class));
                verify(cerbereConfirmationRepository).save(any(CerbereConfirmation.class));

                for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCommit();
                }
                verify(mailSender).send(any(SimpleMailMessage.class));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable")
        void userNotFound() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(null);

            assertThatThrownBy(() -> service.verifyEmail(uid, "code"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Aucun compte associé");

            verifyNoInteractions(cerbereConfirmationRepository, personneService);
        }

        @Test
        @DisplayName("Échec : code invalide")
        void invalidCode() {
            String badCode = "999999";
            String hashedBadCode = "VERIFY:" + sha256(badCode);

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(42L, hashedBadCode))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(uid, badCode))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("code de vérification est incorrect");

            verifyNoInteractions(personneService);
        }

        @Test
        @DisplayName("Échec : code expiré")
        void expiredCode() {
            String code = "654321";
            String hashedCode = "VERIFY:" + sha256(code);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode(hashedCode);
            confirmation.setMail(email);
            confirmation.setLimite(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(42L, hashedCode))
                    .thenReturn(Optional.of(confirmation));

            assertThatThrownBy(() -> service.verifyEmail(uid, code))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a expiré");

            verify(cerbereConfirmationRepository).delete(confirmation);
            verifyNoInteractions(personneService);
        }

        @Test
        @DisplayName("Trop de mauvais codes → MaxAttemptsExceededException et code détruit")
        void locksAfterMaxWrongCodes() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);
            String hashedBad = "VERIFY:" + sha256("999999");
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(42L, hashedBad))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> service.verifyEmail(uid, "999999"))
                        .isInstanceOf(InvalidCodeException.class);
            }
            assertThatThrownBy(() -> service.verifyEmail(uid, "999999"))
                    .isInstanceOf(MaxAttemptsExceededException.class)
                    .hasMessageContaining("Trop de tentatives");

            verify(cerbereConfirmationRepository).deletePendingEmailVerificationByPersonId(42L);
        }

        @Test
        @DisplayName("Compteur saturé : même le bon code de vérification est refusé")
        void lockoutBlocksEvenCorrectCode() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);
            @SuppressWarnings("unchecked")
            Map<Long, AttemptGuardService.AttemptEntry> attempts = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService,
                    "verificationAttempts");
            attempts.put(42L, entryWithCount(2));

            when(aPersonneRepository.findByUid(uid)).thenReturn(person);

            assertThatThrownBy(() -> service.verifyEmail(uid, "123456"))
                    .isInstanceOf(MaxAttemptsExceededException.class);

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(personneService);
        }

        @Test
        @DisplayName("Succès ou expiration : le compteur de vérification est purgé")
        void counterClearedOnSuccessAndExpiry() {
            @SuppressWarnings("unchecked")
            Map<Long, AttemptGuardService.AttemptEntry> attempts = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService,
                    "verificationAttempts");
            attempts.put(42L, entryWithCount(1));

            // Succès
            String code = "123456";
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode("VERIFY:" + sha256(code));
            confirmation.setMail(email);
            confirmation.setLimite(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(42L,
                    "VERIFY:" + sha256(code))).thenReturn(Optional.of(confirmation));

            service.verifyEmail(uid, code);

            assertThat(attempts).doesNotContainKey(42L);

            // Expiration : purge également
            attempts.put(42L, entryWithCount(1));
            CerbereConfirmation expiree = new CerbereConfirmation();
            expiree.setCode("VERIFY:" + sha256("654321"));
            expiree.setLimite(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(42L,
                    "VERIFY:" + sha256("654321"))).thenReturn(Optional.of(expiree));

            assertThatThrownBy(() -> service.verifyEmail(uid, "654321"))
                    .isInstanceOf(CodeExpiredException.class);

            assertThat(attempts).doesNotContainKey(42L);
        }

        @Test
        @DisplayName("Un nouveau code de vérification remet le compteur à zéro")
        void newCodeClearsCounter() {
            @SuppressWarnings("unchecked")
            Map<Long, AttemptGuardService.AttemptEntry> attempts = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService,
                    "verificationAttempts");
            attempts.put(42L, entryWithCount(99));
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendVerificationEmail(uid, email);

            assertThat(attempts).doesNotContainKey(42L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Flux mot de passe oublié
    // ─────────────────────────────────────────────────────────────────────

    private APersonne validPerson(long id) {
        APersonne p = new APersonne();
        p.setId(id);
        p.setUid("user" + id);
        p.setEtat("Valide");
        p.setEmail(email);
        return p;
    }

    /**
     * DTO avec un profil « connectOk » (ex. PERSONNEL) : utilisé par les tests heureux qui ne
     * ciblent pas la règle de profil, pour passer {@code assertPasswordResetAllowed} proprement.
     */
    private PersonneDTO connectOkDto() {
        PersonneDTO dto = mock(PersonneDTO.class);
        lenient().when(dto.getEnumPublic()).thenReturn(EnumPublic.PERSONNEL);
        return dto;
    }

    /**
     * Confirmation en attente dont la création estimée (limite - expiryHours) date de {@code ageMs} millisecondes dans le passé.
     */
    private CerbereConfirmation pendingReset(long personId, long ageMs) {
        CerbereConfirmation c = new CerbereConfirmation();
        c.setCode("RESET:" + sha256("000000"));
        long expiryHoursMs = 24 * 3_600_000L;
        c.setLimite(new Date(System.currentTimeMillis() + expiryHoursMs - ageMs));
        c.setConfirmation(null);
        return c;
    }

    @Nested
    @DisplayName("sendPasswordResetCode")
    class SendPasswordResetCodeTests {

        private static final long COOLDOWN_MS = 60_000L;

        @BeforeEach
        void configurePolicy() {
            mceProperties.getSecurity().getResetPolicy().setResendCooldownMs(COOLDOWN_MS);
        }

        private void stubHappyPath(APersonne p) {
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("Succès : crée une confirmation hashée RESET: et envoie l'email")
        void successCreatesNewConfirmation() {
            APersonne p = validPerson(101L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(101L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(101L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            verify(mailSender).send(mailCaptor.capture());

            CerbereConfirmation saved = confirmationCaptor.getValue();
            assertThat(saved.getAPersonne()).isEqualTo(p);
            assertThat(saved.getCode()).startsWith("RESET:").matches("RESET:[0-9a-f]{64}");
            assertThat(saved.getMail()).isEqualTo(email);
            assertThat(saved.getConfirmation()).isNull();
            assertThat(saved.getLimite()).isAfter(new Date());

            SimpleMailMessage msg = mailCaptor.getValue();
            assertThat(msg.getTo()).containsExactly(email);
            assertThat(msg.getSubject()).isEqualTo("Réinitialisation");
            assertThat(msg.getText()).contains("Votre code de réinitialisation est :");
            assertThat(msg.getText()).doesNotContain("http");
        }

        @Test
        @DisplayName("Succès : réutilise la confirmation en attente existante au lieu d'en créer une nouvelle")
        void reusesPendingConfirmation() {
            APersonne p = validPerson(102L);
            CerbereConfirmation existing = pendingReset(102L, COOLDOWN_MS + 300_000);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(102L))
                    .thenReturn(List.of(existing));
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(102L))
                    .thenReturn(List.of(existing));
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            ArgumentCaptor<CerbereConfirmation> captor = ArgumentCaptor.forClass(CerbereConfirmation.class);
            verify(cerbereConfirmationRepository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(existing);
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Crée une nouvelle confirmation quand l'existante est déjà consommée")
        void createsFreshWhenPreviousConsumed() {
            APersonne p = validPerson(103L);
            CerbereConfirmation consumed = new CerbereConfirmation();
            consumed.setCode("RESET:" + sha256("111111"));
            consumed.setLimite(new Date(System.currentTimeMillis() + 3_600_000L));
            consumed.setConfirmation(new Date());

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(103L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(103L))
                    .thenReturn(List.of(consumed));
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            assertThat(confirmationCaptor.getValue()).isNotSameAs(consumed);
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Échec : email non associé au compte → InvalidCodeException, rien n'est envoyé")
        void unassociatedEmailThrows() {
            APersonne p = validPerson(109L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), "attaquant@evil.fr", null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("n'est pas associée");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Email personnel du compte accepté")
        void personalEmailAccepted() {
            APersonne p = validPerson(110L);
            p.setEmail(null);
            p.setEmailPersonnel(email);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(110L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(110L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Email confirmé via Cerbère accepté même s'il diffère des emails du compte")
        void confirmedEmailAccepted() {
            APersonne p = validPerson(111L);
            p.setEmail("compte@exemple.fr");
            CerbereConfirmation confirmed = new CerbereConfirmation();
            confirmed.setMail(email);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(111L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(111L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findConfirmedByPersonId(111L)).thenReturn(List.of(confirmed));
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Correspondance d'email insensible à la casse, espaces tolérés")
        void emailMatchCaseInsensitive() {
            APersonne p = validPerson(112L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(112L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(112L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), "  TEST@EXAMPLE.COM ", null);

            verify(mailSender).send(mailCaptor.capture());
            assertThat(mailCaptor.getValue().getTo()).containsExactly("TEST@EXAMPLE.COM");
        }

        @Test
        @DisplayName("EduConnect : refus systématique, le mot de passe se gère sur le portail EduConnect")
        void eduConnectAccountRejectedByEduConnect() {
            APersonne p = validPerson(113L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.PARENT_EDUC);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("EduConnect");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("EduConnect avec ntPass : refus malgré ntPass (le check EduConnect prime)")
        void eduConnectWithNtPassStillRejected() {
            APersonne p = validPerson(1131L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.PARENT_EDUC);
            lenient().when(dto.isNtPass()).thenReturn(true);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("EduConnect");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Échec : ni connectOk ni ntPass → aucun mot de passe local à réinitialiser")
        void noLocalAuthThrows() {
            APersonne p = validPerson(114L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("authentification local");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Passage : EDUCATION sans connectOk mais avec ntPass")
        void ntPassWithoutConnectOkAllowed() {
            APersonne p = validPerson(115L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(dto.isNtPass()).thenReturn(true);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(115L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(115L)).thenReturn(List.of());
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Passage : profil connectOk sans ntPass (ex. AUTRE)")
        void connectOkWithoutNtPassAllowed() {
            APersonne p = validPerson(116L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.PERSONNEL);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(116L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(116L)).thenReturn(List.of());
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Le chargement de la personne passe par le verrou pessimiste (anti-course)")
        void loadsPersonWithPessimisticLock() {
            APersonne p = validPerson(117L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), "attaquant@evil.fr", null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("n'est pas associée");

            verify(aPersonneRepository).findByUidWithLock(p.getUid());
            verify(aPersonneRepository, never()).findByUid(p.getUid());
        }

        @Test
        @DisplayName("Échec : uid inconnu → InvalidCodeException, rien n'est persisté ni envoyé")
        void unknownUidThrows() {
            when(aPersonneRepository.findByUidWithLock(uid)).thenReturn(null);

            assertThatThrownBy(() -> service.sendPasswordResetCode(uid, email, null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("Aucun compte associé");

            verifyNoInteractions(cerbereConfirmationRepository, mailSender, personneService);
        }

        @Test
        @DisplayName("Échec : profil fourni incohérent avec la catégorie du compte")
        void incoherentProfileThrows() {
            APersonne p = validPerson(104L);
            p.setCategorie("Enseignant");
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, "ELEVE"))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("Profil incohérent");

            verifyNoInteractions(cerbereConfirmationRepository, mailSender);
        }

        @Test
        @DisplayName("Profil cohérent (nom EnumCategorie ↔ dbname en base), insensible à la casse : le code est envoyé")
        void coherentProfileCaseInsensitive() {
            APersonne p = validPerson(105L);
            p.setCategorie("Eleve");
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(105L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(105L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, "eleve");

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Anti-double-clic : demande récente (< cooldown) → ResendCooldownActiveException avec temps restant")
        void antiDoubleClickBlocksRecentRequest() {
            APersonne p = validPerson(106L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(106L))
                    .thenReturn(List.of(pendingReset(106L, 1_000)));

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(ResendCooldownActiveException.class)
                    .satisfies(e -> assertThat(((ResendCooldownActiveException) e).getRemainingMs()).isPositive());

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Anti-double-clic : demande ancienne (> cooldown) → un nouveau code part")
        void allowsNewCodeAfterCooldown() {
            APersonne p = validPerson(107L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(107L))
                    .thenReturn(List.of(pendingReset(107L, COOLDOWN_MS + 300_000)));
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(107L))
                    .thenReturn(List.of(pendingReset(107L, COOLDOWN_MS + 300_000)));
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(cerbereConfirmationRepository).save(any());
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Auto-assign : email vide + 1 seul email sur le compte → utilise cet email automatiquement")
        void autoAssignsSingleEmailWhenBlank() {
            APersonne p = validPerson(200L);
            p.setEmail("unique@example.com");
            p.setEmailPersonnel(null);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(200L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(200L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(200L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), "", null);

            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            assertThat(confirmationCaptor.getValue().getMail()).isEqualTo("unique@example.com");
            verify(mailSender).send(mailCaptor.capture());
            assertThat(mailCaptor.getValue().getTo()).containsExactly("unique@example.com");
        }

        @Test
        @DisplayName("Auto-assign : email vide + emailPersonnel seul → utilise emailPersonnel")
        void autoAssignsPersonnelEmailWhenBlank() {
            APersonne p = validPerson(201L);
            p.setEmail(null);
            p.setEmailPersonnel("perso@example.com");
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(201L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(201L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(201L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), "", null);

            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            assertThat(confirmationCaptor.getValue().getMail()).isEqualTo("perso@example.com");
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Auto-assign : email vide + 1 seul email confirmé → utilise l'email confirmé")
        void autoAssignsConfirmedEmailWhenBlank() {
            APersonne p = validPerson(202L);
            p.setEmail(null);
            p.setEmailPersonnel(null);
            CerbereConfirmation confirmed = new CerbereConfirmation();
            confirmed.setMail("confirme@example.com");
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(202L)).thenReturn(List.of(confirmed));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(202L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(202L)).thenReturn(List.of());
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), "", null);

            verify(cerbereConfirmationRepository).save(confirmationCaptor.capture());
            assertThat(confirmationCaptor.getValue().getMail()).isEqualTo("confirme@example.com");
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Email vide + 0 emails sur le compte → exception spéciale pour contacter un administrateur")
        void noEmailAtAllThrowsContactAdmin() {
            APersonne p = validPerson(203L);
            p.setEmail(null);
            p.setEmailPersonnel(null);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(203L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), "", null))
                    .isInstanceOf(ContactAdminException.class)
                    .hasMessageContaining("administrateur");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Email fourni + 0 emails sur le compte → exception spéciale pour contacter un administrateur (email obligatoire)")
        void noEmailAtAllWithProvidedEmailStillThrowsContactAdmin() {
            APersonne p = validPerson(205L);
            p.setEmail(null);
            p.setEmailPersonnel(null);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(205L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), "inconnu@example.com", null))
                    .isInstanceOf(ContactAdminException.class)
                    .hasMessageContaining("administrateur");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Email vide + 2 emails sur le compte → erreur email obligatoire")
        void multipleEmailsBlankThrowsEmailRequired() {
            APersonne p = validPerson(204L);
            p.setEmail("a@example.com");
            p.setEmailPersonnel("b@example.com");
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(204L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), "", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("adresse email");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Échec : compte non actif → InactiveAccountException")
        void inactiveAccountThrows() {
            APersonne p = validPerson(108L);
            p.setEtat("Supprime");
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(108L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InactiveAccountException.class)
                    .hasMessageContaining("compte n'est pas actif");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Échec : profil impossible à charger → InactiveAccountException")
        void profileLoadFailureThrows() {
            APersonne p = validPerson(109L);
            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(109L)).thenReturn(List.of());
            when(personneService.getUserByUid(p.getUid())).thenReturn(null);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InactiveAccountException.class)
                    .hasMessageContaining("Impossible de charger votre profil");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("L'email de reset n'est envoyé qu'après le commit de la transaction")
        void resetEmailDeferredUntilAfterCommit() {
            APersonne p = validPerson(118L);
            stubHappyPath(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(118L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(118L)).thenReturn(List.of());

            TransactionSynchronizationManager.initSynchronization();
            try {
                service.sendPasswordResetCode(p.getUid(), email, null);

                verify(mailSender, never()).send(any(SimpleMailMessage.class));
                verify(cerbereConfirmationRepository).save(any(CerbereConfirmation.class));

                for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCommit();
                }
                verify(mailSender).send(any(SimpleMailMessage.class));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("Échec SMTP : MailException encapsulée en RuntimeException")
        void smtpFailureWrapsRuntime() {
            APersonne p = validPerson(110L);
            stubHappyPath(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(110L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(110L)).thenReturn(List.of());
            doThrow(new MailException("SMTP down") {
            }).when(mailSender).send(any(SimpleMailMessage.class));

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Erreur lors de l'envoi du code");

            verify(cerbereConfirmationRepository).save(any());
        }
    }

    @Nested
    @DisplayName("processResetPassword")
    class ProcessResetPasswordTests {

        private PersonneDTO dto;

        @BeforeEach
        void configureDefaults() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(5);
        }

        /** Prépare un compte actif avec un code « 123456 » valide et non expiré. */
        private CerbereConfirmation stubPending(String uidValue, long personId, boolean charteValide) {
            APersonne p = validPerson(personId);
            p.setUid(uidValue);
            p.setValidationCharte(charteValide ? new Date() : null);
            when(aPersonneRepository.findByUid(uidValue)).thenReturn(p);

            String hashed = "RESET:" + sha256("123456");
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode(hashed);
            confirmation.setMail(email);
            confirmation.setLimite(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(eq(personId), anyString()))
                    .thenAnswer(inv -> inv.getArgument(1).equals(hashed) ? Optional.of(confirmation) : Optional.empty());

            dto = connectOkDto();
            lenient().when(dto.isCharteValide()).thenReturn(charteValide);
            lenient().when(personneService.getUserByUid(uidValue)).thenReturn(dto);
            return confirmation;
        }

        @Test
        @DisplayName("Succès : mot de passe changé, code consommé, purge et caches vidés")
        void successConsumesCode() {
            CerbereConfirmation confirmation = stubPending("alice", 201L, true);

            service.processResetPassword("alice", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false);

            verify(passwordService).resetPassword(dto, "N3wPassw0rd!X", "N3wPassw0rd!X");
            verify(personneService, never()).signCharte(anyString());
            assertThat(confirmation.getConfirmation()).isNotNull();
            verify(cerbereConfirmationRepository).save(confirmation);
            verify(cerbereConfirmationRepository).deletePendingPasswordResetByPersonId(201L);
            verify(personneService).clearUserCaches("alice");
        }

        @Test
        @DisplayName("Charte invalide + charteAccepted=true : signature puis succès")
        void signsCharteWhenAccepted() {
            stubPending("bob", 202L, false);

            service.processResetPassword("bob", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", true);

            verify(personneService).signCharte("bob");
            verify(passwordService).resetPassword(eq(dto), anyString(), anyString());
            verify(cerbereConfirmationRepository).deletePendingPasswordResetByPersonId(202L);
        }

        @Test
        @DisplayName("Charte invalide + charteAccepted=false : refus avant tout changement")
        void refusesWhenCharteNotAccepted() {
            stubPending("carol", 203L, false);

            assertThatThrownBy(() -> service.processResetPassword("carol", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(CharteNotAcceptedException.class)
                    .hasMessageContaining("conditions générales");

            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
            verify(personneService, never()).signCharte(anyString());
            verify(cerbereConfirmationRepository, never()).deletePendingPasswordResetByPersonId(anyLong());
            verify(personneService, never()).clearUserCaches(anyString());
        }

        @Test
        @DisplayName("Code inconnu ou déjà utilisé → InvalidCodeException")
        void unknownCodeThrows() {
            APersonne p = validPerson(204L);
            when(aPersonneRepository.findByUid(p.getUid())).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(
                    eq(204L), anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "999999", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("incorrect ou a déjà été utilisé");

            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("La confirmation est chargée via le verrou pessimiste (anti-course)")
        void usesPessimisticLockOnConfirmation() {
            stubPending("alice", 201L, true);

            service.processResetPassword("alice", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false);

            verify(cerbereConfirmationRepository).findPendingPasswordResetByPersonIdAndCodeWithLock(
                    201L, "RESET:" + sha256("123456"));
            verify(cerbereConfirmationRepository, never()).findPendingByPersonIdAndCodeAndType(
                    anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("Code expiré → CodeExpiredException, suppression de la confirmation et remise à zéro du compteur")
        void expiredCodeDeleted() {
            APersonne p = validPerson(205L);
            when(aPersonneRepository.findByUid(p.getUid())).thenReturn(p);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode("RESET:" + sha256("123456"));
            confirmation.setLimite(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(205L,
                    "RESET:" + sha256("123456"))).thenReturn(Optional.of(confirmation));

            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(CodeExpiredException.class)
                    .hasMessageContaining("a expiré");

            verify(cerbereConfirmationRepository).delete(confirmation);
            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Après expiration, le nouveau code repart avec un compteur de tentatives vierge")
        void counterResetAfterExpiry() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);
            APersonne p = validPerson(210L);
            when(aPersonneRepository.findByUid(p.getUid())).thenReturn(p);

            // 1re vie du code : 1 tentative puis expiration
            CerbereConfirmation expiree = new CerbereConfirmation();
            expiree.setCode("RESET:" + sha256("111111"));
            expiree.setLimite(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(210L,
                    "RESET:" + sha256("111111"))).thenReturn(Optional.of(expiree));
            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "111111", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(CodeExpiredException.class);
            verify(cerbereConfirmationRepository).delete(expiree);

            // Nouvelle demande : le compteur doit avoir été remis à zéro
            CerbereConfirmation neuve = new CerbereConfirmation();
            neuve.setCode("RESET:" + sha256("222222"));
            neuve.setLimite(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(210L,
                    "RESET:" + sha256("222222"))).thenReturn(Optional.of(neuve));

            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "222222", "N3wPassw0rd!X", "MauvaiseConfirm!1", false))
                    .isInstanceOf(IllegalArgumentException.class);

            // 2e tentative avec le nouveau code : doit encore être sous maxAttempts=2 (pas de MaxAttempts…)
            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "222222", "N3wPassw0rd!X", "MauvaiseConfirm!2", false))
                    .isNotInstanceOf(MaxAttemptsExceededException.class);
        }

        @Test
        @DisplayName("Compte inactif → InactiveAccountException, code conservé")
        void inactiveAccountThrows() {
            APersonne p = validPerson(206L);
            p.setEtat("Bloque");
            when(aPersonneRepository.findByUid(p.getUid())).thenReturn(p);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode("RESET:" + sha256("123456"));
            confirmation.setLimite(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(206L,
                    "RESET:" + sha256("123456"))).thenReturn(Optional.of(confirmation));

            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InactiveAccountException.class);

            verify(cerbereConfirmationRepository, never()).delete(any());
            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Trop de mauvais codes → MaxAttemptsExceededException, code détruit")
        void maxAttemptsExceededDeletesConfirmation() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);
            stubPending("dave", 207L, true);

            assertThatThrownBy(() -> service.processResetPassword("dave", "000000", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class);
            assertThatThrownBy(() -> service.processResetPassword("dave", "999999", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class);
            assertThatThrownBy(() -> service.processResetPassword("dave", "000000", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(MaxAttemptsExceededException.class)
                    .hasMessageContaining("Trop de tentatives");

            verify(cerbereConfirmationRepository).deletePendingPasswordResetByPersonId(207L);
            verify(cerbereConfirmationRepository, never()).delete(any(CerbereConfirmation.class));
            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Compteur saturé par des mauvais codes : même le bon code est refusé")
        void lockoutBlocksEvenCorrectCode() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);
            CerbereConfirmation confirmation = stubPending("frank", 211L, true);

            assertThatThrownBy(() -> service.processResetPassword("frank", "000000", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class);
            assertThatThrownBy(() -> service.processResetPassword("frank", "111111", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class);

            assertThatThrownBy(() -> service.processResetPassword("frank", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(MaxAttemptsExceededException.class);

            verify(cerbereConfirmationRepository).deletePendingPasswordResetByPersonId(211L);
            assertThat(confirmation.getConfirmation()).isNull();
        }

        @Test
        @DisplayName("Sans UID : les mauvais codes déclenchent le verrouillage")
        void uidlessLockoutBlocksFurtherAttempts() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);

            APersonne p = validPerson(214L);
            p.setUid("uidless");
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setAPersonne(p);
            String expectedHash = "RESET:" + sha256("123456");
            confirmation.setCode(expectedHash);
            confirmation.setLimite(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)));

            @SuppressWarnings("unchecked")
            Map<String, AttemptGuardService.ResetChallenge> challenges =
                    (Map<String, AttemptGuardService.ResetChallenge>) ReflectionTestUtils.getField(attemptGuardService, "resetChallenges");
            challenges.put("token", new AttemptGuardService.ResetChallenge("uidless",
                    System.currentTimeMillis() + 30 * 60_000L));
            when(aPersonneRepository.findByUid("uidless")).thenReturn(p);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(eq(214L), anyString()))
                    .thenAnswer(invocation -> expectedHash.equals(invocation.getArgument(1))
                            ? Optional.of(confirmation)
                            : Optional.empty());

            assertThatThrownBy(() -> service.processResetPassword(null, "token", "000000",
                    "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class);
            assertThatThrownBy(() -> service.processResetPassword(null, "token", "999999",
                    "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class);
            assertThatThrownBy(() -> service.processResetPassword(null, "token", "123456",
                    "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(MaxAttemptsExceededException.class);

            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
            assertThat(confirmation.getConfirmation()).isNull();
        }

        @Test
        @DisplayName("Les échecs bénins (charte refusée) ne consomment pas de tentatives")
        void charteRefusalDoesNotConsumeAttempts() {
            mceProperties.getSecurity().getResetPolicy().setMaxAttempts(2);
            CerbereConfirmation confirmation = stubPending("gina", 212L, false);

            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> service.processResetPassword("gina", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                        .isInstanceOf(CharteNotAcceptedException.class)
                        .hasMessageContaining("conditions générales");
            }

            // Le code est toujours utilisable : acceptation de la charte → succès
            service.processResetPassword("gina", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", true);

            verify(passwordService).resetPassword(eq(dto), anyString(), anyString());
            verify(cerbereConfirmationRepository, never()).delete(any(CerbereConfirmation.class));
            assertThat(confirmation.getConfirmation()).isNotNull();
        }

        @Test
        @DisplayName("Une nouvelle demande de code remet le compteur de tentatives à zéro")
        void newPasswordRequestClearsAttemptCounter() {
            @SuppressWarnings("unchecked")
            Map<Long, AttemptGuardService.AttemptEntry> attempts = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService,
                    "resetAttempts");
            APersonne p = validPerson(213L);
            attempts.put(213L, entryWithCount(99));

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            PersonneDTO dto = connectOkDto();
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonId(213L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.findLatestPasswordResetByPersonId(213L)).thenReturn(List.of());
            when(cerbereConfirmationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.sendPasswordResetCode(p.getUid(), email, null);

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("Profil non défini (enumPublic null) : refus, aucun mode d'authentification local reconnu")
        void nullProfileRejected() {
            APersonne p = validPerson(117L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(null);
            lenient().when(dto.isNtPass()).thenReturn(true);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("profil non reconnu");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Profil à évaluer : un compte EduConnect reste refusé")
        void evaluatedEduConnectProfileRejected() {
            APersonne p = validPerson(118L);
            PersonneDTO dto = mock(PersonneDTO.class);
            when(dto.getEnumPublic()).thenReturn(null);
            when(userDTOFactory.evalPublic(dto)).thenReturn(EnumPublic.PARENT_EDUC);

            when(aPersonneRepository.findByUidWithLock(p.getUid())).thenReturn(p);
            when(personneService.getUserByUid(p.getUid())).thenReturn(dto);

            assertThatThrownBy(() -> service.sendPasswordResetCode(p.getUid(), email, null))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("EduConnect");

            verify(cerbereConfirmationRepository, never()).save(any());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("Échec métier après validation du code : le code reste utilisable")
        void businessFailureKeepsCodeValid() {
            CerbereConfirmation confirmation = stubPending("eve", 208L, true);
            doThrow(new WeakPasswordException("Mot de passe trop faible"))
                    .when(passwordService).resetPassword(any(), anyString(), anyString());

            assertThatThrownBy(() -> service.processResetPassword("eve", "123456", "weak", "weak", false))
                    .isInstanceOf(WeakPasswordException.class);

            assertThat(confirmation.getConfirmation()).isNull();
            verify(cerbereConfirmationRepository, never()).delete(any());
            verify(cerbereConfirmationRepository, never()).deletePendingPasswordResetByPersonId(anyLong());
            verify(personneService, never()).clearUserCaches(anyString());
        }

        @Test
        @DisplayName("Profil impossible à charger après vérification du code → InactiveAccountException")
        void profileLoadFailureThrows() {
            APersonne p = validPerson(209L);
            p.setEtat("Valide");
            when(aPersonneRepository.findByUid(p.getUid())).thenReturn(p);
            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setCode("RESET:" + sha256("123456"));
            confirmation.setLimite(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
            when(cerbereConfirmationRepository.findPendingPasswordResetByPersonIdAndCodeWithLock(209L,
                    "RESET:" + sha256("123456"))).thenReturn(Optional.of(confirmation));
            when(personneService.getUserByUid(p.getUid())).thenReturn(null);

            assertThatThrownBy(() -> service.processResetPassword(p.getUid(), "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InactiveAccountException.class)
                    .hasMessageContaining("Impossible de charger votre profil");

            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Compte EduConnect : refus même avec un code valide en attente (le check EduConnect prime)")
        void eduConnectRejectedEvenWithPendingCode() {
            stubPending("educonnect", 211L, true);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.PARENT_EDUC);
            lenient().when(dto.isNtPass()).thenReturn(true);

            assertThatThrownBy(() -> service.processResetPassword("educonnect", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("EduConnect");

            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
            verify(personneService, never()).clearUserCaches(anyString());
        }

        @Test
        @DisplayName("Ni connectOk ni ntPass : refus même avec un code valide en attente")
        void noLocalAuthRejectedEvenWithPendingCode() {
            stubPending("noauth", 212L, true);
            when(dto.getEnumPublic()).thenReturn(EnumPublic.CVDL);
            lenient().when(dto.isNtPass()).thenReturn(false);

            assertThatThrownBy(() -> service.processResetPassword("noauth", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", false))
                    .isInstanceOf(InvalidCodeException.class)
                    .hasMessageContaining("aucun mode d'authentification local");

            verify(passwordService, never()).resetPassword(any(), anyString(), anyString());
            verify(personneService, never()).clearUserCaches(anyString());
        }
    }

    @Nested
    @DisplayName("Purge périodique des compteurs de tentatives")
    class AttemptPurgeTests {

        @Test
        @DisplayName("Le compteur est local à une instance : il ne simule pas un partage multi-instance")
        void attemptCounterIsScopedToOneServiceInstance() {
            @SuppressWarnings("unchecked")
            Map<Long, AttemptGuardService.AttemptEntry> attempts = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService,
                    "resetAttempts");
            attempts.put(42L, entryWithCount(1));

            AttemptGuardService anotherGuard = new AttemptGuardService();
            @SuppressWarnings("unchecked")
            Map<Long, AttemptGuardService.AttemptEntry> otherAttempts = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(
                    anotherGuard, "resetAttempts");

            assertThat(attempts).containsKey(42L);
            assertThat(otherAttempts).doesNotContainKey(42L);
        }

        @Test
        @DisplayName("Les entrées inactives depuis plus de 2 h sont supprimées, les récentes conservées")
        void purgeRemovesOnlyStaleEntries() {
            Map<Long, AttemptGuardService.AttemptEntry> reset = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService,
                    "resetAttempts");
            Map<Long, AttemptGuardService.AttemptEntry> verification = (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils
                    .getField(attemptGuardService, "verificationAttempts");

            AttemptGuardService.AttemptEntry staleReset = entryWithCount(1);
            staleReset.lastTouchMs = System.currentTimeMillis() - 3 * 3_600_000L;
            reset.put(301L, staleReset);
            AttemptGuardService.AttemptEntry staleVerification = entryWithCount(3);
            staleVerification.lastTouchMs = System.currentTimeMillis() - 3 * 3_600_000L;
            verification.put(302L, staleVerification);
            reset.put(303L, new AttemptGuardService.AttemptEntry());

            attemptGuardService.purgeStaleAttemptEntries();

            assertThat(reset).containsOnlyKeys(303L);
            assertThat(verification).isEmpty();
        }

        @Test
        @DisplayName("Une tentative récente n'est pas purgée : le compteur reste pertinent")
        void recentWrongCodeAttemptsAreNotPurged() {
            when(aPersonneRepository.findByUid(uid)).thenReturn(person);
            when(cerbereConfirmationRepository.findPendingEmailVerificationByPersonIdAndCode(eq(42L), anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(uid, "999999"))
                    .isInstanceOf(InvalidCodeException.class);

            attemptGuardService.purgeStaleAttemptEntries();

            Map<Long, AttemptGuardService.AttemptEntry> verification =
                    (Map<Long, AttemptGuardService.AttemptEntry>) ReflectionTestUtils.getField(attemptGuardService, "verificationAttempts");
            assertThat(verification).containsKey(42L);
            assertThat(verification.get(42L).count.get()).isEqualTo(1);
        }
    }
}
