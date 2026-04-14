package fr.recia.mce.api.escomceapi.service;

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
        aPersonne.setId(123L);
        aPersonne.setPassword("{SSHA}SomeValidHashHere==");
        aPersonne.setUid("test.user");

        personneDTO = new PersonneDTO(aPersonne);
    }


    @Test
    @DisplayName("Devrait changer le mot de passe avec succès")
    void shouldChangePasswordSuccessfully() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("AncienPass123!");
        request.setNewPass("NouveauPass456!");

        PasswordService spiedService = spy(passwordService);
        doReturn(true).when(spiedService)
                .verifyPassword(any(PersonneDTO.class), eq("AncienPass123!"), eq(false));

        given(aPersonneRepository.findById(123L)).willReturn(Optional.of(aPersonne));
        doNothing().when(externalUserDao).updatePassword(anyString(), anyString());

        assertThatCode(() -> spiedService.changePassword(personneDTO, request))
                .doesNotThrowAnyException();

        verify(aPersonneRepository).saveAndFlush(aPersonne);
        verify(externalUserDao).updatePassword(eq("test.user"), anyString());
        assertThat(aPersonne.getDateModification()).isNotNull();
    }

    @Test
    @DisplayName("Devrait échouer si l'ancien mot de passe est incorrect")
    void shouldThrowWhenOldPasswordIsWrong() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("MauvaisPass");
        request.setNewPass("NouveauPass456!");

        PasswordService spiedService = spy(passwordService);
        doReturn(false).when(spiedService)
                .verifyPassword(any(PersonneDTO.class), eq("MauvaisPass"), eq(false));

        assertThatThrownBy(() -> spiedService.changePassword(personneDTO, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ancien mot de passe incorrect");
    }

    @Test
    @DisplayName("Devrait échouer si le nouveau mot de passe est trop faible")
    void shouldThrowWhenNewPasswordIsWeak() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("AncienPass123!");
        request.setNewPass("123");

        assertThatThrownBy(() -> passwordService.changePassword(personneDTO, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mot de passe trop faible");
    }

    @Test
    @DisplayName("Devrait échouer si oldPass == newPass")
    void shouldThrowWhenOldAndNewPasswordAreIdentical() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("Same123!");
        request.setNewPass("Same123!");

        assertThatThrownBy(() -> passwordService.changePassword(personneDTO, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le nouveau mot de passe doit être différent");
    }


    @Test
    @DisplayName("isPasswordStrongEnough → doit accepter les mots de passe forts")
    void shouldAcceptStrongPassword() {
        assertThat(PasswordService.isPasswordStrongEnough("MonSuperMotDePasse2026!")).isTrue();
        assertThat(PasswordService.isPasswordStrongEnough("Azerty123!Secure2026")).isTrue();
        assertThat(PasswordService.isPasswordStrongEnough("P@ssw0rd2026Secure!")).isTrue();
    }

    @Test
    @DisplayName("isPasswordStrongEnough → doit rejeter les mots de passe faibles")
    void shouldRejectWeakPassword() {
        assertThat(PasswordService.isPasswordStrongEnough(null)).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("")).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("123456")).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("abcdef")).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("Password")).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("pass123")).isFalse();
        assertThat(PasswordService.isPasswordStrongEnough("Azerty123!")).isFalse();
    }


    @Test
    @DisplayName("hasValidStoredPassword → cas valides")
    void shouldReturnTrueForValidStoredPassword() {
        assertThat(passwordService.hasValidStoredPassword("{SSHA}abc123==")).isTrue();
        assertThat(passwordService.hasValidStoredPassword("plainText123")).isTrue();
    }

    @Test
    @DisplayName("hasValidStoredPassword → cas invalides")
    void shouldReturnFalseForInvalidStoredPassword() {
        assertThat(passwordService.hasValidStoredPassword(null)).isFalse();
        assertThat(passwordService.hasValidStoredPassword("")).isFalse();
        assertThat(passwordService.hasValidStoredPassword("{SSHA}Active=blocked")).isFalse();
    }

    @Test
    @DisplayName("verifyPassword → doit retourner true pour un mot de passe correct")
    void shouldVerifyCorrectPassword() {
        LdapPassword ldapPasswordMock = mock(LdapPassword.class);
        given(ldapPasswordMock.test("CorrectPass123!")).willReturn(true);

        personneDTO.setLdapPassword(ldapPasswordMock);

        boolean result = passwordService.verifyPassword(personneDTO, "CorrectPass123!", false);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyPassword → doit retourner false pour un mot de passe incorrect")
    void shouldVerifyIncorrectPassword() {
        LdapPassword ldapPasswordMock = mock(LdapPassword.class);
        given(ldapPasswordMock.test(anyString())).willReturn(false);

        personneDTO.setLdapPassword(ldapPasswordMock);

        boolean result = passwordService.verifyPassword(personneDTO, "WrongPass", false);
        assertThat(result).isFalse();
    }
}