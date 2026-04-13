package fr.recia.mce.api.escomceapi;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.utils.LdapPassword;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests unitaires - PasswordService")
class PasswordServiceTest {

    private static final Long USER_ID = 123L;
    private static final String USER_UID = "valentine.seine";

    private static final String VALID_OLD_PASSWORD = "AncienPass123!";
    private static final String VALID_NEW_PASSWORD = "NouveauPass456!";
    private static final String WRONG_PASSWORD = "MauvaisPass";
    private static final String WEAK_PASSWORD = "123";
    private static final String SAME_PASSWORD = "Same123!";

    private static final String STRONG_PASSWORD_1 = "MonSuperMotDePasse2026!";
    private static final String STRONG_PASSWORD_2 = "Azerty123!Secure2026";
    private static final String STRONG_PASSWORD_3 = "P@ssw0rd2026Secure!";

    private static final String WEAK_PASSWORD_1 = "123456";
    private static final String WEAK_PASSWORD_2 = "abcdef";
    private static final String WEAK_PASSWORD_3 = "Password";
    private static final String WEAK_PASSWORD_4 = "pass123";
    private static final String WEAK_PASSWORD_5 = "Azerty123!";

    private static final String VALID_SSHA_PASSWORD = "{SSHA}abc123==";
    private static final String INVALID_SSHA_PASSWORD = "{SSHA}Active=blocked";
    private static final String PLAINTEXT_PASSWORD = "plainText123";

    private static final String INITIAL_PASSWORD_HASH = "{SSHA}eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.fakeHash";

    @Mock
    private IExternalUserDao externalUserDao;

    @Mock
    private APersonneRepository aPersonneRepository;

    @InjectMocks
    private PasswordService passwordService;

    private PersonneDTO personneDTO;
    private APersonne aPersonne;

    @BeforeEach
    void setUp() {
        aPersonne = new APersonne();
        aPersonne.setId(USER_ID);
        aPersonne.setPassword(INITIAL_PASSWORD_HASH);
        aPersonne.setUid(USER_UID);

        personneDTO = new PersonneDTO(aPersonne);
    }

    @Test
    @DisplayName("Devrait changer le mot de passe avec succès")
    void shouldChangePasswordSuccessfully() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass(VALID_OLD_PASSWORD);
        request.setNewPass(VALID_NEW_PASSWORD);

        PasswordService spiedService = spy(passwordService);
        doReturn(true).when(spiedService)
                .verifyPassword(any(PersonneDTO.class), eq(VALID_OLD_PASSWORD), eq(false));

        given(aPersonneRepository.findById(USER_ID)).willReturn(Optional.of(aPersonne));
        doNothing().when(externalUserDao).updatePassword(anyString(), anyString());

        assertThatCode(() -> spiedService.changePassword(personneDTO, request))
                .doesNotThrowAnyException();

        verify(aPersonneRepository).saveAndFlush(aPersonne);
        verify(externalUserDao).updatePassword(eq(USER_UID), anyString());
        assertThat(aPersonne.getDateModification()).isNotNull();
    }

    @Test
    @DisplayName("Devrait échouer si l'ancien mot de passe est incorrect")
    void shouldThrowWhenOldPasswordIsWrong() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass(WRONG_PASSWORD);
        request.setNewPass(VALID_NEW_PASSWORD);

        PasswordService spiedService = spy(passwordService);
        doReturn(false).when(spiedService)
                .verifyPassword(any(PersonneDTO.class), eq(WRONG_PASSWORD), eq(false));

        assertThatThrownBy(() -> spiedService.changePassword(personneDTO, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ancien mot de passe incorrect");
    }

    @Test
    @DisplayName("Devrait échouer si le nouveau mot de passe est trop faible")
    void shouldThrowWhenNewPasswordIsWeak() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass(VALID_OLD_PASSWORD);
        request.setNewPass(WEAK_PASSWORD);

        assertThatThrownBy(() -> passwordService.changePassword(personneDTO, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mot de passe trop faible");
    }

    @Test
    @DisplayName("Devrait échouer si oldPass == newPass")
    void shouldThrowWhenOldAndNewPasswordAreIdentical() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass(SAME_PASSWORD);
        request.setNewPass(SAME_PASSWORD);

        assertThatThrownBy(() -> passwordService.changePassword(personneDTO, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le nouveau mot de passe doit être différent");
    }

    @Test
    @DisplayName("isPasswordStrongEnough → doit accepter les mots de passe forts")
    void shouldAcceptStrongPassword() {
        assertThat(PasswordService.isPasswordStrongEnough(STRONG_PASSWORD_1)).isTrue();
        assertThat(PasswordService.isPasswordStrongEnough(STRONG_PASSWORD_2)).isTrue();
        assertThat(PasswordService.isPasswordStrongEnough(STRONG_PASSWORD_3)).isTrue();
    }

    @Test
    @DisplayName("isPasswordStrongEnough → doit rejeter les mots de passe faibles")
    void shouldRejectWeakPassword() {
        assertThat(PasswordService.isPasswordStrongEnough(null)).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("")).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough(WEAK_PASSWORD_1)).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough(WEAK_PASSWORD_2)).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough(WEAK_PASSWORD_3)).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough(WEAK_PASSWORD_4)).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough(WEAK_PASSWORD_5)).isFalse();
    }

    @Test
    @DisplayName("hasValidStoredPassword → cas valides")
    void shouldReturnTrueForValidStoredPassword() {
        assertThat(passwordService.hasValidStoredPassword(VALID_SSHA_PASSWORD)).isTrue();
        assertThat(passwordService.hasValidStoredPassword(PLAINTEXT_PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("hasValidStoredPassword → cas invalides")
    void shouldReturnFalseForInvalidStoredPassword() {
        assertThat(passwordService.hasValidStoredPassword(null)).isFalse();
        assertThat(passwordService.hasValidStoredPassword("")).isFalse();
        assertThat(passwordService.hasValidStoredPassword(INVALID_SSHA_PASSWORD)).isFalse();
    }

    @Test
    @DisplayName("verifyPassword → doit retourner true pour un mot de passe correct")
    void shouldVerifyCorrectPassword() {
        LdapPassword ldapPasswordMock = mock(LdapPassword.class);
        given(ldapPasswordMock.test(VALID_OLD_PASSWORD)).willReturn(true);

        personneDTO.setLdapPassword(ldapPasswordMock);

        boolean result = passwordService.verifyPassword(personneDTO, VALID_OLD_PASSWORD, false);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyPassword → doit retourner false pour un mot de passe incorrect")
    void shouldVerifyIncorrectPassword() {
        LdapPassword ldapPasswordMock = mock(LdapPassword.class);
        given(ldapPasswordMock.test(anyString())).willReturn(false);

        personneDTO.setLdapPassword(ldapPasswordMock);

        boolean result = passwordService.verifyPassword(personneDTO, WRONG_PASSWORD, false);
        assertThat(result).isFalse();
    }

    @Test
    void shouldFullyChangePassword() {
        given(aPersonneRepository.findById(USER_ID)).willReturn(Optional.of(aPersonne));

        LdapPassword ldapPasswordMock = mock(LdapPassword.class);
        given(ldapPasswordMock.test(VALID_OLD_PASSWORD)).willReturn(true);
        personneDTO.setLdapPassword(ldapPasswordMock);

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass(VALID_OLD_PASSWORD);
        request.setNewPass(VALID_NEW_PASSWORD);

        passwordService.changePassword(personneDTO, request);

        assertThat(aPersonne.getPassword()).isNotEqualTo(INITIAL_PASSWORD_HASH);
        verify(externalUserDao).updatePassword(eq(USER_UID), anyString());
    }
}