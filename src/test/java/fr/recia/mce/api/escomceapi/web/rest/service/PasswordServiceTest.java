package fr.recia.mce.api.escomceapi.web.rest.service;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerberePassword;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerberePasswordRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Exhaustifs - PasswordService")
class PasswordServiceTest {

    @Mock
    private IExternalUserDao externalUserDao;

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private CerberePasswordRepository cerberePasswordRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MCEProperties mceProperties;

    @Mock
    private ExternalUserHelper externalUserHelper;

    @Mock
    private IExternalUser extUser;

    @InjectMocks
    private PasswordService passwordService;

    private PersonneDTO personneDTO;
    private APersonne aPersonne;
    private final String uid = "user123";
    private final String strongPassword = "StrongPassword123!";

    @BeforeEach
    void setUp() {
        aPersonne = new APersonne();
        aPersonne.setId(100L);
        aPersonne.setUid(uid);
        personneDTO = new PersonneDTO(aPersonne);
        personneDTO.setExtUser(extUser); // Initialisation pour les tests LDAP
        lenient().when(externalUserHelper.getUserGroupAttribute()).thenReturn("memberOf");
    }

    private PasswordChangeRequestDTO createRequest(String oldPass, String newPass, String confirmPass) {
        PasswordChangeRequestDTO req = new PasswordChangeRequestDTO();
        req.setOldPass(oldPass);
        req.setNewPass(newPass);
        req.setConfirmPass(confirmPass);
        return req;
    }

    // =========================================================================
    @Nested
    @DisplayName("Tests de Robustesse du Mot de Passe - isPasswordStrongEnough")
    class PasswordStrengthTests {

        @Test
        @DisplayName("Doit accepter les mots de passe respectant tous les critères (Cas standard)")
        void shouldAcceptValidPasswords() {
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("Abc123Valid!"));
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("Password@2026"));
        }

        @Test
        @DisplayName("Doit accepter un mot de passe avec exactement 12 caractères et 3 types")
        void shouldAcceptExactly12CharsWith3Types() {
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("Abc123456789"));
        }

        @Test
        @DisplayName("Doit accepter un mot de passe avec les 4 types de caractères")
        void shouldAcceptAll4Types() {
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("aB1!password"));
        }

        @Test
        @DisplayName("Doit refuser si le mot de passe est nul")
        void shouldRejectNull() {
            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough(null))
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessage("Mot de passe requis");
        }

        @Test
        @DisplayName("Doit refuser si le mot de passe est vide")
        void shouldRejectEmpty() {
            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough(""))
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessageContaining("12 caractères");
        }

        @Test
        @DisplayName("Doit refuser si moins de 12 caractères (Ex: 11)")
        void shouldRejectTooShort() {
            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("Abc1234567!"))
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessageContaining("12 caractères");
        }

        @Test
        @DisplayName("Doit refuser si seulement 1 type de caractères")
        void shouldRejectOnlyOneType() {
            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("aaaaaaaaaaaa")) // Minuscules
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessageContaining("trois types différents");

            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("AAAAAAAAAAAA")) // Majuscules
                    .isInstanceOf(WeakPasswordException.class);

            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("111111111111")) // Chiffres
                    .isInstanceOf(WeakPasswordException.class);

            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("!!!!!!!!!!!!")) // Symboles
                    .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        @DisplayName("Doit refuser si seulement 2 types de caractères")
        void shouldRejectOnlyTwoTypes() {
            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("aaaaaAAAAAAA")) // Lower + Upper
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessageContaining("trois types différents");

            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("aaaaa1111111")) // Lower + Digit
                    .isInstanceOf(WeakPasswordException.class);

            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("AAAAA1111111")) // Upper + Digit
                    .isInstanceOf(WeakPasswordException.class);

            assertThatThrownBy(() -> PasswordService.isPasswordStrongEnough("aaaaa!!!!!!!")) // Lower + Symbol
                    .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        @DisplayName("Doit accepter les combinaisons de 3 types (Toutes les variantes)")
        void shouldAcceptAll3TypeCombinations() {
            // Lower + Upper + Digit
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("abcABC123456"));
            // Lower + Upper + Symbol
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("abcABC!!!!!!"));
            // Lower + Digit + Symbol
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("abc123456!!!"));
            // Upper + Digit + Symbol
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("ABC123456!!!"));
        }

        @Test
        @DisplayName("Doit gérer les espaces comme des symboles")
        void shouldHandleSpacesAsSymbols() {
            // Lower (abc) + Upper (DEF) + Space (symbol)
            assertThatNoException().isThrownBy(() -> PasswordService.isPasswordStrongEnough("abc DEF      "));
        }


    }


    @Nested
    @DisplayName("Tests de validation de requête - validateRequest")
    class ValidateRequestTests {

        @Test
        @DisplayName("Doit accepter une requête valide")
        void shouldAcceptValidRequest() {
            PasswordChangeRequestDTO req = createRequest("OldPass123!", "NewPass123456!", "NewPass123456!");

            assertThatNoException().isThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            );
        }

        @Test
        @DisplayName("Doit refuser si oldPass est nul")
        void shouldRejectNullOldPass() {
            PasswordChangeRequestDTO req = createRequest(null, strongPassword, strongPassword);

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ancien mot de passe requis");
        }

        @Test
        @DisplayName("Doit refuser si oldPass est vide")
        void shouldRejectBlankOldPass() {
            PasswordChangeRequestDTO req = createRequest("   ", strongPassword, strongPassword);

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ancien mot de passe requis");
        }

        @Test
        @DisplayName("Doit refuser si newPass est nul")
        void shouldRejectNullNewPass() {
            PasswordChangeRequestDTO req = createRequest(strongPassword, null, null);

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Nouveau mot de passe requis");
        }

        @Test
        @DisplayName("Doit refuser si newPass est vide")
        void shouldRejectBlankNewPass() {
            PasswordChangeRequestDTO req = createRequest(strongPassword, "   ", "   ");

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Nouveau mot de passe requis");
        }

        @Test
        @DisplayName("Doit refuser si newPass == oldPass")
        void shouldRejectSamePassword() {
            String pass = strongPassword;
            PasswordChangeRequestDTO req = createRequest(pass, pass, pass);

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("différent de l'ancien");
        }

        @Test
        @DisplayName("Doit refuser si confirmation différente")
        void shouldRejectConfirmationMismatch() {
            PasswordChangeRequestDTO req = createRequest(strongPassword, "NewPass123456!", "WrongConfirm");

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confirmation");
        }

        @Test
        @DisplayName("Doit refuser si le nouveau mot de passe est faible")
        void shouldRejectWeakPassword() {
            PasswordChangeRequestDTO req = createRequest(strongPassword, "weak", "weak");

            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, req)
            )
                    .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        @DisplayName("Doit refuser si la requête est nulle")
        void shouldRejectNullRequest() {
            assertThatThrownBy(() ->
                    passwordService.validateRequest(personneDTO, null)
            )
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }


    @Nested
    @DisplayName("Tests de Parsing - parse ")
    class ParseTests {

        @Test
        @DisplayName("Doit échouer si le hash est nul ou vide")
        void shouldReturnNullForBlankHash() {
            assertThat((Object) ReflectionTestUtils.invokeMethod(passwordService, "parse", (Object) null)).isNull();
            assertThat((Object) ReflectionTestUtils.invokeMethod(passwordService, "parse", "")).isNull();
            assertThat((Object) ReflectionTestUtils.invokeMethod(passwordService, "parse", "   ")).isNull();
        }

        @Test
        @DisplayName("Doit échouer pour un algorithme inconnu dans le switch (default)")
        void shouldReturnNullForUnknownAlgoInSwitch() {
            assertThat((Object) ReflectionTestUtils.invokeMethod(passwordService, "parse", "{OTHER}somehash")).isNull();
        }

        @Test
        @DisplayName("Doit retourner null si SHA-1 n'est pas disponible (NoSuchAlgorithmException)")
        void shouldReturnNullWhenSha1NotFound() {
            try (MockedStatic<MessageDigest> mockedMessageDigest = mockStatic(MessageDigest.class)) {
                mockedMessageDigest.when(() -> MessageDigest.getInstance("SHA-1"))
                        .thenThrow(new NoSuchAlgorithmException("SHA-1 not found"));

                Object result = ReflectionTestUtils.invokeMethod(passwordService, "parse", "{SSHA}somehash");
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("Doit retourner null pour un algo inconnu")
        void shouldReturnNullForUnknownAlgo() {
            assertThat((Object) ReflectionTestUtils.invokeMethod(
                    passwordService, "parse", "{NEWALGO}somecontent"
            )).isNull();
        }

        @Test
        @DisplayName("Doit retourner null si le hash ne correspond pas au format LDAP attendu")
        void shouldReturnNullForInvalidHashFormat() {
            // pas de {} autour de l'algo
            assertThat((Object) ReflectionTestUtils.invokeMethod(
                    passwordService, "parse", "SSHAsomehash"
            )).isNull();

            // minuscules → regex n'accepte que majuscules
            assertThat((Object) ReflectionTestUtils.invokeMethod(
                    passwordService, "parse", "{ssha}somehash"
            )).isNull();

            // pas de contenu après {}
            assertThat((Object) ReflectionTestUtils.invokeMethod(
                    passwordService, "parse", "{SSHA}"
            )).isNull();

            // format complètement invalide
            assertThat((Object) ReflectionTestUtils.invokeMethod(
                    passwordService, "parse", "randomstring"
            )).isNull();
        }

    }

    @Nested
    @DisplayName("Tests de Vérification de Hash - verify Password")
    class VerificationTests {

        @Test
        @DisplayName("Doit échouer si la personne ou le mot de passe saisi est nul/vide")
        void shouldReturnFalseForInvalidInputs() {
            assertThat(passwordService.verifyPassword(null, strongPassword, true)).isFalse();
            assertThat(passwordService.verifyPassword(personneDTO, null, true)).isFalse();
            assertThat(passwordService.verifyPassword(personneDTO, "   ", true)).isFalse();
        }

        @Test
        @DisplayName("Doit gérer un UID nul en le remplaçant par 'unknown'")
        void shouldHandleNullUid() {
            aPersonne.setUid(null);
            aPersonne.setPassword(null); // Pour déclencher le log avec 'unknown'
            assertThat(passwordService.verifyPassword(personneDTO, strongPassword, true)).isFalse();
        }

        @Test
        @DisplayName("Doit échouer si aucun mot de passe n'est stocké en base")
        void shouldReturnFalseIfNoStoredPassword() {
            aPersonne.setPassword(null);
            assertThat(passwordService.verifyPassword(personneDTO, strongPassword, true)).isFalse();

            aPersonne.setPassword("");
            assertThat(passwordService.verifyPassword(personneDTO, strongPassword, true)).isFalse();
        }

        @Test
        @DisplayName("Doit échouer pour le marqueur ACTIVE_PASSWORD")
        void shouldReturnFalseForActivePasswordMarker() {
            aPersonne.setPassword("{SSHA}Active==================================================");
            assertThat(passwordService.verifyPassword(personneDTO, "any", true)).isFalse();
        }

        @Nested
        @DisplayName("Tests Plain Text (Sans préfixe { )")
        class PlainTextTests {
            @Test
            @DisplayName("Succès si autorisé et match")
            void shouldVerifyPlainSuccess() {
                aPersonne.setPassword("myPlainPass");
                assertThat(passwordService.verifyPassword(personneDTO, "myPlainPass", true)).isTrue();
            }

            @Test
            @DisplayName("Échec si autorisé mais mismatch")
            void shouldVerifyPlainFailure() {
                aPersonne.setPassword("myPlainPass");
                assertThat(passwordService.verifyPassword(personneDTO, "wrong", true)).isFalse();
            }

            @Test
            @DisplayName("Échec si non autorisé")
            void shouldRejectPlainIfNotAllowed() {
                aPersonne.setPassword("myPlainPass");
                assertThat(passwordService.verifyPassword(personneDTO, "myPlainPass", false)).isFalse();
            }
        }

        @Nested
        @DisplayName("Tests ARGON2")
        class Argon2Tests {
            @Test
            @DisplayName("Vérification ARGON2 : Succès")
            void verifyArgon2_Success() {
                String raw = "mySecret";
                String hash = "{ARGON2}" + new Argon2PasswordEncoder().encode(raw);
                aPersonne.setPassword(hash);
                assertThat(passwordService.verifyPassword(personneDTO, raw, false)).isTrue();
            }

            @Test
            @DisplayName("Vérification ARGON2 : Échec")
            void verifyArgon2_Failure() {
                String hash = "{ARGON2}" + new Argon2PasswordEncoder().encode("correct");
                aPersonne.setPassword(hash);
                assertThat(passwordService.verifyPassword(personneDTO, "wrong", false)).isFalse();
            }
        }

        @Nested
        @DisplayName("Tests SSHA & Encodages")
        class SshAndEncodingTests {

            private String createSshaHash(String raw, byte[] salt) throws Exception {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(raw.getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] digest = md.digest();
                byte[] combined = new byte[digest.length + salt.length];
                System.arraycopy(digest, 0, combined, 0, digest.length);
                System.arraycopy(salt, 0, combined, digest.length, salt.length);
                return "{SSHA}" + Base64.encodeBase64String(combined);
            }

            @Test
            @DisplayName("SSHA : Succès standard (UTF-8)")
            void verifySSHA_Success() throws Exception {
                String raw = "secret";
                aPersonne.setPassword(createSshaHash(raw, "salt1234".getBytes()));
                assertThat(passwordService.verifyPassword(personneDTO, raw, false)).isTrue();
            }

            @Test
            @DisplayName("SSHA : Échec (Mauvais mot de passe)")
            void verifySSHA_Failure() throws Exception {
                aPersonne.setPassword(createSshaHash("correct", "salt1234".getBytes()));
                assertThat(passwordService.verifyPassword(personneDTO, "wrong", false)).isFalse();
            }

            @Test
            @DisplayName("SSHA : Fallback encodage 1 (UTF-8 vers ISO)")
            void verifySSHA_Fallback1() throws Exception {
                String raw = "ééàà";
                byte[] salt = "salt1234".getBytes();
                String v2 = new String(raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(v2.getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] digest = md.digest();
                byte[] combined = new byte[digest.length + salt.length];
                System.arraycopy(digest, 0, combined, 0, digest.length);
                System.arraycopy(salt, 0, combined, digest.length, salt.length);

                aPersonne.setPassword("{SSHA}" + Base64.encodeBase64String(combined));
                assertThat(passwordService.verifyPassword(personneDTO, raw, false)).isTrue();
            }

            @Test
            @DisplayName("SSHA : Fallback encodage 2 (ISO vers UTF-8)")
            void verifySSHA_Fallback2() throws Exception {
                String raw = "ééàà";
                byte[] salt = "salt1234".getBytes();
                String v3 = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(v3.getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] digest = md.digest();
                byte[] combined = new byte[digest.length + salt.length];
                System.arraycopy(digest, 0, combined, 0, digest.length);
                System.arraycopy(salt, 0, combined, digest.length, salt.length);

                aPersonne.setPassword("{SSHA}" + Base64.encodeBase64String(combined));
                assertThat(passwordService.verifyPassword(personneDTO, raw, false)).isTrue();
            }
        }

        @Nested
        @DisplayName("Tests Hash corrompus ou inconnus")
        class ErrorFormatTests {
            @Test
            @DisplayName("Algo inconnu (ex: {MD5})")
            void shouldFailForUnknownAlgo() {
                aPersonne.setPassword("{MD5}somehash");
                assertThat(passwordService.verifyPassword(personneDTO, "any", false)).isFalse();
            }

            @Test
            @DisplayName("Format SSHA invalide (Trop court)")
            void shouldFailForShortSsha() {
                aPersonne.setPassword("{SSHA}YWJj");
                assertThat(passwordService.verifyPassword(personneDTO, "any", false)).isFalse();
            }

            @Test
            @DisplayName("Hash ne respectant pas le pattern {ALGO}...")
            void shouldFailForBadPattern() {
                aPersonne.setPassword("{BADFORMAT}abc");
                assertThat(passwordService.verifyPassword(personneDTO, "any", false)).isFalse();
            }

            @Test
            @DisplayName("Algo reconnu par le pattern mais non supporté par le switch de vérification")
            void shouldFailForRecognizedButUnsupportedAlgo() {
                aPersonne.setPassword("{OTHER}somehash");
                assertThat(passwordService.verifyPassword(personneDTO, "any", false)).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Tests de l'Historique")
    class HistoryTests {

        @Nested
        @DisplayName("Tests de isPasswordAlreadyUsed")
        class AlreadyUsedTests {
            @Test
            @DisplayName("Doit retourner true si le MDP en clair match un MDP en clair de l'historique")
            void shouldReturnTrueForPlainMatch() {
                String pass = "myOldPass";
                CerberePassword cp = new CerberePassword();
                cp.setPassword(pass); // Pas de préfixe { -> plain

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, pass)).isTrue();
            }

            @Test
            @DisplayName("Doit retourner true si le MDP en clair match un hash Argon2 de l'historique")
            void shouldReturnTrueForArgon2Match() {
                String pass = "myOldPass";
                CerberePassword cp = new CerberePassword();
                cp.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode(pass));

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, pass)).isTrue();
            }

            @Test
            @DisplayName("Doit retourner true si le MDP en clair match un hash SSHA de l'historique")
            void shouldReturnTrueForSshaMatch() throws Exception {
                String pass = "myOldPass";
                byte[] salt = "salt1234".getBytes();
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(pass.getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] digest = md.digest();
                byte[] combined = new byte[digest.length + salt.length];
                System.arraycopy(digest, 0, combined, 0, digest.length);
                System.arraycopy(salt, 0, combined, digest.length, salt.length);

                CerberePassword cp = new CerberePassword();
                cp.setPassword("{SSHA}" + Base64.encodeBase64String(combined));

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, pass)).isTrue();
            }

            @Test
            @DisplayName("Doit retourner false si aucun MDP de l'historique ne match")
            void shouldReturnFalseNoMatch() {
                CerberePassword cp1 = new CerberePassword();
                cp1.setPassword("other1");
                CerberePassword cp2 = new CerberePassword();
                cp2.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode("other2"));

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(cp1, cp2));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "myNewPass")).isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si l'historique est vide")
            void shouldReturnFalseEmptyHistory() {
                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(new ArrayList<>());
                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "any")).isFalse();
            }
        }

        @Nested
        @DisplayName("Tests de savePasswordToHistory")
        class SaveHistoryTests {
            @Test
            @DisplayName("Doit mettre à jour l'entrée existante si une entrée existe déjà le même jour")
            void shouldUpdateExistingEntryIfToday() {
                CerberePassword entryToday = new CerberePassword();
                entryToday.setDebut(new Date()); // Aujourd'hui

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(entryToday));

                passwordService.savePasswordToHistory(personneDTO, "{ARGON2}newHash");

                verify(cerberePasswordRepository, never()).saveAndFlush(any(CerberePassword.class));
                verify(cerberePasswordRepository).updatePasswordForToday(eq(aPersonne.getId()), eq("{ARGON2}newHash"));
            }
        }

        @Nested
        @DisplayName("Tests de closeLastPassword")
        class CloseHistoryTests {
            @Test
            @DisplayName("Doit fermer le dernier MDP s'il date d'avant aujourd'hui")
            void shouldCloseIfOld() {
                CerberePassword last = new CerberePassword();
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -1);
                last.setDebut(cal.getTime());
                last.setFin(null);

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(last));

                passwordService.closeLastPassword(personneDTO);

                assertThat(last.getFin()).isNotNull();
                verify(cerberePasswordRepository).save(last);
            }

            @Test
            @DisplayName("Ne doit pas fermer si le dernier MDP date d'aujourd'hui")
            void shouldNotCloseIfToday() {
                CerberePassword last = new CerberePassword();
                last.setDebut(new Date());
                last.setFin(null);

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(last));

                passwordService.closeLastPassword(personneDTO);

                assertThat(last.getFin()).isNull();
                verify(cerberePasswordRepository, never()).save(any());
            }

            @Test
            @DisplayName("Ne doit rien faire si l'historique est vide")
            void shouldDoNothingIfEmpty() {
                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(null);
                assertThatNoException().isThrownBy(() -> passwordService.closeLastPassword(personneDTO));

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(new ArrayList<>());
                assertThatNoException().isThrownBy(() -> passwordService.closeLastPassword(personneDTO));
            }

            @Test
            @DisplayName("Ne doit pas modifier si la date de fin est déjà renseignée")
            void shouldNotModifyIfAlreadyClosed() {
                CerberePassword last = new CerberePassword();
                last.setDebut(new Date(0)); // Très vieux
                Date alreadySetFin = new Date();
                last.setFin(alreadySetFin);

                when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(last));

                passwordService.closeLastPassword(personneDTO);

                assertThat(last.getFin()).isEqualTo(alreadySetFin);
                verify(cerberePasswordRepository, never()).save(any());
            }
        }

        @Nested
        @DisplayName("Tests de verifyPasswordInternal (via isPasswordAlreadyUsed)")
        class VerifyPasswordInternalTests {

            @Test
            @DisplayName("Doit retourner false si input est null")
            void shouldReturnFalseIfInputNull() {
                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of());
                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, null))
                        .isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si storedHash est null")
            void shouldReturnFalseIfStoredHashNull() {
                CerberePassword cp = new CerberePassword();
                cp.setPassword(null); // storedHash null → return false

                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "anyPass"))
                        .isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si storedHash est vide")
            void shouldReturnFalseIfStoredHashBlank() {
                CerberePassword cp = new CerberePassword();
                cp.setPassword("   "); // storedHash blank → return false

                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "anyPass"))
                        .isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si le hash est corrompu (parse retourne null)")
            void shouldReturnFalseIfHashCorrupted() {
                CerberePassword cp = new CerberePassword();
                cp.setPassword("{SSHA}!!!invalid base64!!!"); // parse() → null

                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "anyPass"))
                        .isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si le mot de passe en clair ne correspond pas")
            void shouldReturnFalseForPlainMismatch() {
                CerberePassword cp = new CerberePassword();
                cp.setPassword("storedPlainPass"); // pas de { → comparaison directe

                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "wrongPass"))
                        .isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si le hash Argon2 ne correspond pas")
            void shouldReturnFalseForArgon2Mismatch() {
                CerberePassword cp = new CerberePassword();
                cp.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode("correctPass"));

                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "wrongPass"))
                        .isFalse();
            }

            @Test
            @DisplayName("Doit retourner false si le hash SSHA ne correspond pas")
            void shouldReturnFalseForSshaMismatch() throws Exception {
                byte[] salt = "salt1234".getBytes();
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update("correctPass".getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] digest = md.digest();
                byte[] combined = new byte[digest.length + salt.length];
                System.arraycopy(digest, 0, combined, 0, digest.length);
                System.arraycopy(salt,   0, combined, digest.length, salt.length);

                CerberePassword cp = new CerberePassword();
                cp.setPassword("{SSHA}" + Base64.encodeBase64String(combined));

                when(cerberePasswordRepository.findByAPersonne(aPersonne))
                        .thenReturn(List.of(cp));

                assertThat(passwordService.isPasswordAlreadyUsed(personneDTO, "wrongPass"))
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Tests changePassword")
    class ChangePasswordFlowTests {

        @Test
        @DisplayName("Succès Nominal : Argon2 sans Samba")
        void changePassword_Success_Argon2() {
            String oldPass = "OldStrong123!";
            String newPass = "NewStrong456@";
            aPersonne.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode(oldPass));

            PasswordChangeRequestDTO req = createRequest(oldPass, newPass, newPass);

            when(aPersonneRepository.findById(100L)).thenReturn(Optional.of(aPersonne));
            when(aPersonneRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            passwordService.changePassword(personneDTO, req);

            verify(aPersonneRepository).saveAndFlush(aPersonne);
            verify(externalUserDao).updatePassword(eq(uid), startsWith("{ARGON2}"));
            verify(cerberePasswordRepository).saveAndFlush(any(CerberePassword.class));

            assertThat(aPersonne.getPassword()).startsWith("{ARGON2}");
        }

        @Test
        @DisplayName("Erreur : Utilisateur nul")
        void changePassword_NullUser() {
            assertThatThrownBy(() -> passwordService.changePassword(null, new PasswordChangeRequestDTO()))
                    .isInstanceOf(PersonneNotFoundException.class);
        }

        @Nested
        @DisplayName("Tests de validation de la requête")
        class ValidationRequestTests {
            @Test
            @DisplayName("Échec si requête nulle")
            void shouldFailIfRequestIsNull() {
                assertThatThrownBy(() -> passwordService.changePassword(personneDTO, null))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("Échec si nouveau mot de passe identique à l'ancien")
            void shouldFailIfPasswordsAreSame() {
                String pass = strongPassword;
                PasswordChangeRequestDTO req = createRequest(pass, pass, pass);
                assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("différent de l'ancien");
            }

            @Test
            @DisplayName("Échec si la confirmation ne match pas")
            void shouldFailIfConfirmationMismatch() {
                PasswordChangeRequestDTO req = createRequest(strongPassword, "NewPass123456!", "WrongConf123456!");
                assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("confirmation");
            }

            @Test
            @DisplayName("Échec si le nouveau mot de passe est trop faible")
            void shouldFailIfNewPasswordIsWeak() {
                PasswordChangeRequestDTO req = createRequest(strongPassword, "weak", "weak");
                assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                        .isInstanceOf(WeakPasswordException.class);
            }
        }

        @Test
        @DisplayName("Erreur : Ancien mot de passe incorrect")
        void changePassword_WrongOld() {
            aPersonne.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode("correctOne"));
            PasswordChangeRequestDTO req = createRequest("wrongOne", strongPassword, strongPassword);

            assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ancien mot de passe incorrect");
        }

        @Test
        @DisplayName("Erreur : Mot de passe déjà utilisé")
        void changePassword_AlreadyUsed() {
            String oldPass = "OldPass123456!";
            String reusedPass = "Reused123456!";
            aPersonne.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode(oldPass));

            PasswordChangeRequestDTO req = createRequest(oldPass, reusedPass, reusedPass);

            CerberePassword cp = new CerberePassword();
            cp.setPassword(reusedPass);
            when(cerberePasswordRepository.findByAPersonne(aPersonne)).thenReturn(List.of(cp));

            assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                    .isInstanceOf(WeakPasswordException.class)
                    .hasMessage("Ce mot de passe a déjà été utilisé");
        }

        @Test
        @DisplayName("Erreur : Utilisateur disparu de la base pendant l'exécution")
        void changePassword_UserMissingInDb() {
            String pass = strongPassword;
            aPersonne.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode(pass));
            PasswordChangeRequestDTO req = createRequest(pass, "AnotherNew123!", "AnotherNew123!");

            when(aPersonneRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Erreur technique");
        }

        @Test
        @DisplayName("Erreur : Panne LDAP")
        void changePassword_LdapFailure() {
            String pass = strongPassword;
            aPersonne.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode(pass));
            PasswordChangeRequestDTO req = createRequest(pass, "AnotherNew123!", "AnotherNew123!");

            when(aPersonneRepository.findById(anyLong())).thenReturn(Optional.of(aPersonne));

            assertThatThrownBy(() -> passwordService.changePassword(personneDTO, req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Erreur technique");
        }
    }


    @Nested
    @DisplayName("Tests de makeSSHA")
    class MakeSshaTests {

        @Test
        @DisplayName("makeSSHA : Produit un hash {SSHA} valide et vérifiable")
        void makeSSHA_producesValidHash() throws Exception {
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("makeSSHA", String.class);
            m.setAccessible(true);
            String hash = (String) m.invoke(passwordService, "myPassword");

            assertThat(hash).startsWith("{SSHA}");

            // Vérifier que le hash est cohérent (re-vérification via verifyPassword)
            aPersonne.setPassword(hash);
            assertThat(passwordService.verifyPassword(personneDTO, "myPassword", false)).isTrue();
            assertThat(passwordService.verifyPassword(personneDTO, "wrongPassword", false)).isFalse();
        }

        @Test
        @DisplayName("makeSSHA : Deux appels produisent des hashes différents (sel aléatoire)")
        void makeSSHA_randomSalt() throws Exception {
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("makeSSHA", String.class);
            m.setAccessible(true);
            String hash1 = (String) m.invoke(passwordService, "samePassword");
            String hash2 = (String) m.invoke(passwordService, "samePassword");

            assertThat(hash1).isNotEqualTo(hash2); // sels différents à chaque appel
        }

        @Test
        @DisplayName("makeSSHA : Doit lancer RuntimeException si SHA-1 n'est pas disponible")
        void makeSSHA_shouldThrowRuntimeExceptionWhenSha1NotFound() throws Exception {
            try (MockedStatic<MessageDigest> mockedMd = mockStatic(MessageDigest.class)) {
                mockedMd.when(() -> MessageDigest.getInstance("SHA-1"))
                        .thenThrow(new NoSuchAlgorithmException("SHA-1 not found"));

                java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("makeSSHA", String.class);
                m.setAccessible(true);

                assertThatThrownBy(() -> {
                    try {
                        m.invoke(passwordService, "anyPassword");
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause(); // déballer l'exception réelle
                    }
                })
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Erreur SSHA")
                        .hasCauseInstanceOf(NoSuchAlgorithmException.class);
            }
        }
    }


    @Nested
    @DisplayName("Tests de requiresSSHA")
    class SshaRequirementTests {

        @Test
        @DisplayName("SSHA non requis : Regex nulle")
        void requiresSSHA_nullRegex() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn(null);
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("SSHA non requis : Regex vide")
        void requiresSSHA_blankRegex() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn("   ");
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("SSHA non requis : ExtUser nul")
        void requiresSSHA_extUserNull() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn(".*ssha.*");
            personneDTO.setExtUser(null);
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("SSHA non requis : Aucun groupe ne correspond")
        void requiresSSHA_noGroupMatch() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn(".*ssha.*");
            when(extUser.getAttribute("memberOf")).thenReturn(List.of("cn=other-group"));
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("SSHA non requis : Liste de groupes vide")
        void requiresSSHA_emptyGroups() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn(".*ssha.*");
            when(extUser.getAttribute("memberOf")).thenReturn(Collections.emptyList());
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("SSHA requis : Un groupe correspond à la regex")
        void requiresSSHA_groupMatches() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn(".*ssha.*");
            when(extUser.getAttribute("memberOf")).thenReturn(List.of("cn=ssha-users,ou=groups"));
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("SSHA non requis : Liste de groupes nulle")
        void requiresSSHA_nullGroups() throws Exception {
            when(mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass()).thenReturn(".*ssha.*");
            when(extUser.getAttribute("memberOf")).thenReturn(null);
            java.lang.reflect.Method m = PasswordService.class.getDeclaredMethod("requiresSSHA", PersonneDTO.class);
            m.setAccessible(true);
            boolean result = (boolean) m.invoke(passwordService, personneDTO);
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Tests des pré-requis LDAP")
    class LdapRequirementTests {

        private void setupMockForChangePassword(String oldPass) {
            aPersonne.setPassword("{ARGON2}" + new Argon2PasswordEncoder().encode(oldPass));
            when(aPersonneRepository.findById(anyLong())).thenReturn(Optional.of(aPersonne));
            when(aPersonneRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        }

        @Nested
        @DisplayName("Tests de requiresSamba")
        class SambaTests {
            @Test
            @DisplayName("Samba requis : Succès (Groupe match)")
            void shouldRequireSambaWhenGroupMatches() {
                when(mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt()).thenReturn(".*samba.*");
                when(extUser.getAttribute("memberOf")).thenReturn(List.of("cn=samba-users,ou=groups"));

                setupMockForChangePassword(strongPassword);
                passwordService.changePassword(personneDTO, createRequest(strongPassword, "NewPass123456!", "NewPass123456!"));

                assertThat(aPersonne.getSambaNtpassword()).isNotNull();
                assertThat(aPersonne.getSambaLmpassword()).isNotNull();
            }

            @Test
            @DisplayName("Samba non requis : Regex non configurée")
            void shouldNotRequireSambaWhenRegexIsNull() {
                when(mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt()).thenReturn(null);

                setupMockForChangePassword(strongPassword);
                passwordService.changePassword(personneDTO, createRequest(strongPassword, "NewPass123456!", "NewPass123456!"));

                assertThat(aPersonne.getSambaNtpassword()).isNull();
            }

            @Test
            @DisplayName("Samba non requis : ExtUser est nul")
            void shouldNotRequireSambaWhenExtUserIsNull() {
                personneDTO.setExtUser(null);
                when(mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt()).thenReturn(".*samba.*");

                setupMockForChangePassword(strongPassword);
                passwordService.changePassword(personneDTO, createRequest(strongPassword, "NewPass123456!", "NewPass123456!"));

                assertThat(aPersonne.getSambaNtpassword()).isNull();
            }

            @Test
            @DisplayName("Samba non requis : Aucun groupe ne match")
            void shouldNotRequireSambaWhenNoGroupMatches() {
                when(mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt()).thenReturn(".*samba.*");
                when(extUser.getAttribute("memberOf")).thenReturn(List.of("cn=other-group", "cn=standard-users"));

                setupMockForChangePassword(strongPassword);
                passwordService.changePassword(personneDTO, createRequest(strongPassword, "NewPass123456!", "NewPass123456!"));

                assertThat(aPersonne.getSambaNtpassword()).isNull();
            }

            @Test
            @DisplayName("Samba non requis : Liste de groupes vide")
            void shouldNotRequireSambaWhenGroupListIsEmpty() {
                when(mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt()).thenReturn(".*samba.*");
                when(extUser.getAttribute("memberOf")).thenReturn(Collections.emptyList());

                setupMockForChangePassword(strongPassword);
                passwordService.changePassword(personneDTO, createRequest(strongPassword, "NewPass123456!", "NewPass123456!"));

                assertThat(aPersonne.getSambaNtpassword()).isNull();
            }

            @Test
            @DisplayName("Samba non requis : Regex invalide (PatternSyntaxException)")
            void shouldNotRequireSambaWhenRegexIsInvalid() {
                when(mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt()).thenReturn("[invalid(regex");

                setupMockForChangePassword(strongPassword);
                passwordService.changePassword(personneDTO, createRequest(strongPassword, "NewPass123456!", "NewPass123456!"));

                assertThat(aPersonne.getSambaNtpassword()).isNull();
            }
        }
    }



}
