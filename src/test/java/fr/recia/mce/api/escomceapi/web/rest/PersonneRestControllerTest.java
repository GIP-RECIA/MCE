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

package fr.recia.mce.api.escomceapi.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.recia.mce.api.escomceapi.configuration.interceptor.SoffitInterceptor;
import java.util.List;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.db.entities.Login;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import fr.recia.mce.api.escomceapi.web.dto.VerifyEmailRequestDTO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for {@link PersonneRestController}. Organized using @Nested and @DisplayName for better readability and structure.
 */
@WebMvcTest(PersonneRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PersonneRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersonneService personneService;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    @MockBean
    private SoffitHolder soffitHolder;

    @MockBean
    private SoffitInterceptor soffitInterceptor;

    @MockBean
    @SuppressWarnings("unused")
    private LdapTemplate ldapTemplate;

    @MockBean
    @SuppressWarnings("unused")
    private FonctionService fonctionService;

    @MockBean
    @SuppressWarnings("unused")
    private PasswordService passwordService;

    @MockBean
    @SuppressWarnings("unused")
    private RelationEleveServiceImpl relationEleveServiceImpl;

    @MockBean
    @SuppressWarnings("unused")
    private EmailVerificationService emailVerificationService;

    @MockBean
    @SuppressWarnings("unused")
    private CharteService charteService;

    @MockBean
    @SuppressWarnings("unused")
    private fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository aPersonneRepository;

    @MockBean
    @SuppressWarnings("unused")
    private fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository cerbereConfirmationRepository;

    private static final String BASE_URL = "/api/personne/mce/";
    private static final String USER = "test.user";

    @BeforeEach
    void setUp() throws Exception {
        when(soffitHolder.getSub()).thenReturn(USER);
        when(soffitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    private PasswordChangeRequestDTO buildValidPasswordChangeRequest() {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO();
        request.setOldPass("OldPass123!");
        request.setNewPass("NewPass456!");
        request.setConfirmPass("NewPass456!");
        return request;
    }

    private EmailUpdateRequestDTO buildValidEmailUpdateRequest() {
        EmailUpdateRequestDTO request = new EmailUpdateRequestDTO();
        request.setEmail("test@example.com");
        request.setConfirmEmail("test@example.com");
        return request;
    }

    @Nested
    @DisplayName("Tests du point d'accès /change-password")
    class PasswordChangeTests {

        @Test
        @DisplayName("Changement de mot de passe réussi")
        void shouldChangePasswordSuccessfully() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();
            doNothing().when(userDTOFactory).changePassword(eq(USER), any());

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(userDTOFactory).changePassword(eq(USER), any());
        }

        @Test
        @DisplayName("Interdiction de changer le mot de passe d'un autre utilisateur")
        void shouldReturnForbiddenWhenChangingAnotherUserPassword() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();

            mockMvc.perform(post(BASE_URL + "autre.user/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(userDTOFactory, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("Interdiction lorsque l'utilisateur n'est pas authentifié")
        void shouldReturnForbiddenWhenNotAuthenticated() throws Exception {
            when(soffitHolder.getSub()).thenReturn(null);

            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(userDTOFactory, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("Mauvaise requête lorsque le corps de la requête est invalide")
        void shouldReturnBadRequestWhenBodyIsInvalid() throws Exception {
            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(userDTOFactory, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("Erreur interne du serveur lorsque le service lance une exception")
        void shouldReturnInternalServerErrorWhenServiceThrows() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();

            doThrow(new RuntimeException("Erreur LDAP"))
                    .when(userDTOFactory).changePassword(eq(USER), any());

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());

            verify(userDTOFactory).changePassword(eq(USER), any());
        }

        @Test
        @DisplayName("Mauvaise requête lorsque l'ancien mot de passe est manquant")
        void shouldReturnBadRequestWhenOldPassMissing() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();
            request.setOldPass(null);

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userDTOFactory, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("Mauvaise requête lorsque le nouveau mot de passe est manquant")
        void shouldReturnBadRequestWhenNewPassMissing() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();
            request.setNewPass(null);

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userDTOFactory, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("Mauvaise requête lorsque le nouveau mot de passe est trop faible")
        void shouldReturnBadRequestWhenNewPassIsWeak() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();
            request.setNewPass("123");

            doThrow(new IllegalArgumentException("Mot de passe trop faible"))
                    .when(userDTOFactory).changePassword(eq(USER), any());

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userDTOFactory).changePassword(eq(USER), any());
        }

        @Test
        @DisplayName("Non trouvé lorsque l'utilisateur n'existe pas")
        void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();

            doThrow(new PersonneNotFoundException("Utilisateur introuvable"))
                    .when(userDTOFactory).changePassword(eq(USER), any());

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            verify(userDTOFactory).changePassword(eq(USER), any());
        }

        @Test
        @DisplayName("Mauvaise requête pour JSON malformé")
        void shouldReturnBadRequestForMalformedJson() throws Exception {
            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("invalid json"))
                    .andExpect(status().isBadRequest());

            verify(userDTOFactory, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("Mauvaise requête lorsque la validation du nouveau mot de passe échoue")
        void shouldReturnBadRequestWhenNewPassFailsValidation() throws Exception {
            PasswordChangeRequestDTO request = buildValidPasswordChangeRequest();
            request.setNewPass("weak"); // mot de passe trop faible

            // Simule la vraie validation dans PasswordService
            doAnswer(invocation -> {
                String newPass = ((PasswordChangeRequestDTO) invocation.getArgument(1)).getNewPass();
                if (newPass.length() < 8) {
                    throw new IllegalArgumentException("Mot de passe trop faible");
                }
                return null;
            }).when(userDTOFactory).changePassword(eq(USER), any());

            mockMvc.perform(post(BASE_URL + USER + "/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(userDTOFactory).changePassword(eq(USER), any());
        }
    }

    @Nested
    @DisplayName("Tests de récupération d'informations utilisateur")
    class UserInformationRetrievalTests {

        @Test
        @DisplayName("Obtenir l'ID utilisateur actuel (debug)")
        void shouldGetCurrentUserId() throws Exception {
            mockMvc.perform(get(BASE_URL + "debug-id"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(USER));
        }

        private PersonneDTO buildPersonneDTO() {
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(USER);
            aPersonne.setSn("Test");
            aPersonne.setGivenName("User");
            aPersonne.setDisplayName("Test User");
            aPersonne.setCategorie("Staff");
            aPersonne.setEmail("test@test.com");

            AStructure aStructure = new AStructure();
            aStructure.setNom("Structure");
            aPersonne.setAStructure(aStructure);
            Login login = new Login();
            login.setNom(USER);

            return new PersonneDTO(aPersonne, aStructure, login);
        }

        @Test
        void shouldGetPersonneByUidSuccessfully() throws Exception {
            PersonneDTO personne = buildPersonneDTO();

            when(personneService.retrievePersonnebyUid(USER)).thenReturn(personne);

            mockMvc.perform(get(BASE_URL + "getuser"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uid").value(USER));
        }

        @Test
        @DisplayName("Non trouvé lorsque la personne par UID est nulle")
        void shouldReturnNotFoundWhenPersonneByUidIsNull() throws Exception {
            when(personneService.retrievePersonnebyUid(USER)).thenReturn(null);

            mockMvc.perform(get(BASE_URL + "getuser"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Obtenir la personne LDAP avec succès")
        void shouldGetPersonLdapSuccessfully() throws Exception {
            IExternalUser user = new IExternalUser() {
                @Override
                public String getEmail() {
                    return "test@example.com";
                }

                @Override
                public String getId() {
                    return USER;
                }

                @Override
                public String getDisplayName() {
                    return "Test User";
                }

                @Override
                public java.util.Map<String, java.util.List<String>> getAttributes() {
                    return java.util.Collections.emptyMap();
                }

                @Override
                public java.util.List<String> getAttribute(String name) {
                    return java.util.Collections.emptyList();
                }
            };
            when(personneService.retrievePersonLdap(USER)).thenReturn(user);

            mockMvc.perform(get(BASE_URL + "ldap"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non trouvé lorsque la personne LDAP est nulle")
        void shouldReturnNotFoundWhenPersonLdapIsNull() throws Exception {
            when(personneService.retrievePersonLdap(USER)).thenReturn(null);

            mockMvc.perform(get(BASE_URL + "ldap"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Interdiction d'accéder au debug-id si non authentifié")
        void shouldReturnForbiddenWhenNotAuthenticatedForDebugId() throws Exception {
            when(soffitHolder.getSub()).thenReturn(null);
            mockMvc.perform(get(BASE_URL + "debug-id"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Obtenir les informations MCE")
        void shouldGetMCE() throws Exception {
            UserDTO user = new UserDTO();
            when(userDTOFactory.from(USER)).thenReturn(user);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non trouvé lorsque les informations MCE sont nulles")
        void shouldReturnNotFoundWhenMCEIsNull() throws Exception {
            when(userDTOFactory.from(USER)).thenReturn(null);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Obtenir les détails d'un enfant")
        void shouldGetDetailEnfant() throws Exception {
            String enfantId = "enfant.user";
            UserDTO enfant = new UserDTO();
            when(userDTOFactory.from(enfantId)).thenReturn(enfant);

            mockMvc.perform(get(BASE_URL + enfantId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non trouvé lorsque les détails d'un enfant sont nuls")
        void shouldReturnNotFoundWhenEnfantIsNull() throws Exception {
            String enfantId = "enfant.user";
            when(userDTOFactory.from(enfantId)).thenReturn(null);

            mockMvc.perform(get(BASE_URL + enfantId))
                    .andExpect(status().isNotFound());
        }

        private RelationEleveContact buildParentRelation(String enfantId) {
            APersonne childEntity = new APersonne();
            childEntity.setId(1L);
            childEntity.setUid(enfantId);
            childEntity.setSn("VAR");
            childEntity.setGivenName("Sara");
            childEntity.setDisplayName("Sara VAR");
            childEntity.setCategorie("ELEVE");

            AStructure structure = new AStructure();
            structure.setNom("College");
            childEntity.setAStructure(structure);

            PersonneDTO personneDTO = new PersonneDTO(childEntity);

            RelationEleveContact rel = new RelationEleveContact(RelationEleveContact.SensRel.ELEVE2CONTACT);
            rel.setUidRelation("pierrevar");
            rel.setDisplayNameRelation("Pierre VAR");
            rel.setTypeRelation("Autorite_parentale");
            rel.setEleve(personneDTO);

            return rel;
        }

        @Test
        @DisplayName("Sérialisation UserDTO avec parentEleve contenant PersonneDTO + APersonne")
        void shouldSerializeUserDtoWithParentEleve() throws Exception {
            String enfantId = "F20102xc";

            RelationEleveContact rel = buildParentRelation(enfantId);

            UserDTO enfant = new UserDTO();
            enfant.setUid(enfantId);
            enfant.setUserName("Sara VAR");
            enfant.setCategorie("ELEVE");
            enfant.setParentEleve(List.of(rel));

            when(userDTOFactory.from(enfantId)).thenReturn(enfant);

            mockMvc.perform(get(BASE_URL + enfantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uid").value(enfantId))
                    .andExpect(jsonPath("$.parentEleve[0].uidRelation").value("pierrevar"))
                    .andExpect(jsonPath("$.parentEleve[0].eleve.uid").value(enfantId));
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /update-email")
    class EmailUpdateTests {

        @Test
        @DisplayName("Mise à jour de l'email réussie")
        void shouldUpdateEmailSuccessfully() throws Exception {
            EmailUpdateRequestDTO request = buildValidEmailUpdateRequest();

            mockMvc.perform(put(BASE_URL + USER + "/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());

            verify(personneService).validateEmailForUpdate(USER, "test@example.com");
            verify(emailVerificationService).sendVerificationEmail(USER, "test@example.com");
        }

        @Test
        @DisplayName("Interdiction de mettre à jour l'email d'un autre utilisateur")
        void shouldReturnForbiddenWhenUpdatingAnotherUserEmail() throws Exception {
            EmailUpdateRequestDTO request = buildValidEmailUpdateRequest();

            mockMvc.perform(put(BASE_URL + "autre.user/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Mauvaise requête lorsque les emails ne correspondent pas")
        void shouldReturnBadRequestWhenEmailsDoNotMatch() throws Exception {
            EmailUpdateRequestDTO request = buildValidEmailUpdateRequest();
            request.setConfirmEmail("wrong@example.com");

            mockMvc.perform(put(BASE_URL + USER + "/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value("Les adresses email ne correspondent pas"));
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /verify-email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Vérification d'email réussie → retourne SUCCESS")
        void shouldVerifyEmailSuccessfully() throws Exception {
            VerifyEmailRequestDTO request = new VerifyEmailRequestDTO();
            request.setUid(USER);
            request.setCode("123456");

            mockMvc.perform(post(BASE_URL + "verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("Email verifié avec succès"));

            verify(emailVerificationService).verifyEmail(USER, "123456");
        }

        @Test
        @DisplayName("Échec : code invalide → retourne 400")
        void shouldFailWhenCodeInvalid() throws Exception {
            VerifyEmailRequestDTO request = new VerifyEmailRequestDTO();
            request.setUid(USER);
            request.setCode("000000");

            doThrow(new InvalidCodeException("Code de verification invalide ou deja utilise"))
                    .when(emailVerificationService).verifyEmail(USER, "000000");

            mockMvc.perform(post(BASE_URL + "verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                    .andExpect(jsonPath("$.message").value("Code de verification invalide ou deja utilise"));
        }

        @Test
        @DisplayName("Échec : code non conforme (pas 6 chiffres) → 400")
        void shouldFailWhenCodeNot6Digits() throws Exception {
            VerifyEmailRequestDTO request = new VerifyEmailRequestDTO();
            request.setUid(USER);
            request.setCode("abc");

            mockMvc.perform(post(BASE_URL + "verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès avatar")
    class AvatarTests {

        @Test
        @DisplayName("Avatar non trouvé retourne 404 avec PersonneNotFoundException")
        void shouldReturnNotFoundWhenAvatarIsNull() throws Exception {
            when(personneService.getAvatar(USER)).thenReturn(null);

            mockMvc.perform(get(BASE_URL + USER + "/avatar.jpg"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Récupération d'avatar réussie")
        void shouldGetAvatarSuccessfully() throws Exception {
            byte[] imageBytes = new byte[]{1, 2, 3};
            when(personneService.getAvatar(USER)).thenReturn(imageBytes);

            mockMvc.perform(get(BASE_URL + USER + "/avatar.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/jpeg"))
                    .andExpect(content().bytes(imageBytes));
        }

        @Test
        @DisplayName("Upload d'avatar réussi")
        void shouldUpdateAvatarSuccessfully() throws Exception {
            mockMvc.perform(multipart(BASE_URL + USER + "/avatar")
                    .file("file", "fake-image-content".getBytes()))
                    .andExpect(status().isNoContent());

            verify(personneService).updateAvatar(eq(USER), any(byte[].class));
        }

        @Test
        @DisplayName("Upload d'avatar refusé pour un autre utilisateur")
        void shouldReturnForbiddenWhenUpdatingAnotherUserAvatar() throws Exception {
            mockMvc.perform(multipart(BASE_URL + "other/avatar")
                    .file("file", "content".getBytes()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Tests d'authentification et d'autorisation généraux")
    class GeneralAuthorizationTests {

        @Test
        @DisplayName("Interdiction lorsque l'utilisateur est un invité")
        void shouldReturnForbiddenWhenUserIsGuest() throws Exception {
            when(soffitHolder.getSub()).thenReturn("guest");

            mockMvc.perform(get(BASE_URL + "getuser"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Interdiction lorsque l'identifiant utilisateur (sub) est vide ou nul")
        void shouldReturnForbiddenWhenSubIsBlank() throws Exception {
            when(soffitHolder.getSub()).thenReturn(" "); // Test with blank space

            mockMvc.perform(get(BASE_URL + "getuser"))
                    .andExpect(status().isForbidden());
        }
    }
}
