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
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidAvatarException;
import org.springframework.security.access.AccessDeniedException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import fr.recia.mce.api.escomceapi.configuration.MCEProperties;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
    private fr.recia.mce.api.escomceapi.services.CharteUrlResolver charteUrlResolver;

    @MockBean
    @SuppressWarnings("unused")
    private fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository aPersonneRepository;

    @MockBean
    @SuppressWarnings("unused")
    private fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository cerbereConfirmationRepository;

    @MockBean
    private fr.recia.mce.api.escomceapi.services.structure.IStructureService structureService;

    @Autowired
    private MCEProperties mceProperties;

    private static final String BASE_URL = "/api/personne/mce/";
    private static final String USER = "test.user";

    @BeforeEach
    void setUp() throws Exception {
        when(soffitHolder.getSub()).thenReturn(USER);
        when(soffitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        mceProperties.getSecurity().getRateLimit().setPermitsPerSecond(1_000_000.0);
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

        @Test
        @DisplayName("Domaine de l'adresse email exclu → 400 BAD_REQUEST, aucun email envoyé")
        void shouldReturnBadRequestWhenDomainExcluded() throws Exception {
            doThrow(new IllegalArgumentException("Le domaine de l'adresse email est exclu"))
                    .when(personneService).validateEmailForUpdate(USER, "test@example.com");

            mockMvc.perform(put(BASE_URL + USER + "/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildValidEmailUpdateRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value("Le domaine de l'adresse email est exclu"));

            verify(emailVerificationService, never()).sendVerificationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Format de l'adresse email invalide (service) → 400 BAD_REQUEST")
        void shouldReturnBadRequestWhenEmailFormatInvalid() throws Exception {
            doThrow(new IllegalArgumentException("Le format de l'adresse email n'est pas valide"))
                    .when(personneService).validateEmailForUpdate(USER, "test@example.com");

            mockMvc.perform(put(BASE_URL + USER + "/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildValidEmailUpdateRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

            verify(emailVerificationService, never()).sendVerificationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Email personnel non modifiable pour ce profil → 403 FORBIDDEN")
        void shouldReturnForbiddenWhenEmailNotEditable() throws Exception {
            doThrow(new AccessDeniedException("Vous ne pouvez pas modifier votre email personnel"))
                    .when(personneService).validateEmailForUpdate(USER, "test@example.com");

            mockMvc.perform(put(BASE_URL + USER + "/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildValidEmailUpdateRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            verify(emailVerificationService, never()).sendVerificationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Utilisateur introuvable → 404 NOT_FOUND")
        void shouldReturnNotFoundWhenUserUnknown() throws Exception {
            doThrow(new PersonneNotFoundException("Utilisateur introuvable en base : " + USER))
                    .when(personneService).validateEmailForUpdate(USER, "test@example.com");

            mockMvc.perform(put(BASE_URL + USER + "/update-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildValidEmailUpdateRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));

            verify(emailVerificationService, never()).sendVerificationEmail(anyString(), anyString());
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

        @Test
        @DisplayName("Échec : code expiré → 400 CODE_EXPIRED")
        void shouldFailWhenCodeExpired() throws Exception {
            VerifyEmailRequestDTO request = new VerifyEmailRequestDTO();
            request.setUid(USER);
            request.setCode("123456");

            doThrow(new CodeExpiredException("Le code de vérification a expiré. Veuillez en demander un nouveau."))
                    .when(emailVerificationService).verifyEmail(USER, "123456");

            mockMvc.perform(post(BASE_URL + "verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        }

        @Test
        @DisplayName("Échec : trop de tentatives → 429 MAX_ATTEMPTS_EXCEEDED")
        void shouldFailWhenMaxAttemptsExceeded() throws Exception {
            VerifyEmailRequestDTO request = new VerifyEmailRequestDTO();
            request.setUid(USER);
            request.setCode("123456");

            doThrow(new MaxAttemptsExceededException("Trop de tentatives échouées. Veuillez demander un nouveau code de vérification."))
                    .when(emailVerificationService).verifyEmail(USER, "123456");

            mockMvc.perform(post(BASE_URL + "verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("MAX_ATTEMPTS_EXCEEDED"));
        }

        @Test
        @DisplayName("Échec : uid manquant → 400 VALIDATION_ERROR")
        void shouldFailWhenUidMissing() throws Exception {
            VerifyEmailRequestDTO request = new VerifyEmailRequestDTO();
            request.setCode("123456");

            mockMvc.perform(post(BASE_URL + "verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
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

        @Test
        @DisplayName("Upload d'avatar invalide (format/taille) → 400 INVALID_AVATAR")
        void shouldReturnBadRequestWhenAvatarInvalid() throws Exception {
            doThrow(new InvalidAvatarException("Avatar invalide : format non supporté ou fichier trop volumineux"))
                    .when(personneService).updateAvatar(eq(USER), any(byte[].class));

            mockMvc.perform(multipart(BASE_URL + USER + "/avatar")
                    .file("file", "fake-image-content".getBytes()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_AVATAR"));
        }

        @Test
        @DisplayName("Upload sans fichier → 500 INTERNAL_SERVER_ERROR (file.getBytes() sur null)")
        void shouldFailWhenNoFileProvided() throws Exception {
            mockMvc.perform(multipart(BASE_URL + USER + "/avatar"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
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

    // ─────────────────────────────────────────────────────────────────────
    // Flux mot de passe oublié
    // ─────────────────────────────────────────────────────────────────────

    private static final String FORGOT_URL = BASE_URL + "forgot-password";
    private static final String RESET_URL = BASE_URL + "reset-password";
    private static final String SEARCH_UID_URL = BASE_URL + "search-uid";

    @Nested
    @DisplayName("Tests du point d'accès /forgot-password")
    class ForgotPasswordEndpointTests {

        private final String validBody = "{\"uid\":\"dupontj\",\"email\":\"jean.dupont@example.fr\",\"profil\":\"ELEVE\"}";

        @Test
        @DisplayName("Succès → 200 RESET_CODE_SENT")
        void shouldReturnResetCodeSent() throws Exception {
            doNothing().when(emailVerificationService)
                    .sendPasswordResetCode("dupontj", "jean.dupont@example.fr", "ELEVE");

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("RESET_CODE_SENT"));

            verify(emailVerificationService).sendPasswordResetCode("dupontj", "jean.dupont@example.fr", "ELEVE");
        }

        @Test
        @DisplayName("Uid inconnu (IllegalArgumentException) → 400 FORGOT_PASSWORD_FAILED")
        void shouldReturnForgotPasswordFailed() throws Exception {
            doThrow(new InvalidCodeException("Aucun compte associé à cet identifiant"))
                    .when(emailVerificationService).sendPasswordResetCode(anyString(), anyString(), anyString());

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FORGOT_PASSWORD_FAILED"));
        }

        @Test
        @DisplayName("Erreur technique (RuntimeException) → 500 INTERNAL_ERROR")
        void shouldReturnInternalError() throws Exception {
            doThrow(new RuntimeException("SMTP down"))
                    .when(emailVerificationService).sendPasswordResetCode(anyString(), anyString(), anyString());

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }

        @Test
        @DisplayName("Uid manquant → 400 VALIDATION_ERROR")
        void shouldValidateMissingUid() throws Exception {
            String body = "{\"email\":\"jean.dupont@example.fr\"}";

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("Email mal formé → 400 VALIDATION_ERROR")
        void shouldValidateEmailFormat() throws Exception {
            String body = "{\"uid\":\"dupontj\",\"email\":\"pas-un-email\"}";

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("Email absent (obligatoire) → 400 VALIDATION_ERROR")
        void shouldRejectMissingEmail() throws Exception {
            String body = "{\"uid\":\"dupontj\",\"profil\":\"ELEVE\"}";

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("Email vide (obligatoire) → 400 VALIDATION_ERROR")
        void shouldRejectBlankEmail() throws Exception {
            String body = "{\"uid\":\"dupontj\",\"email\":\"\",\"profil\":\"ELEVE\"}";

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("Exception ContactAdminException → 400 CONTACT_ADMIN_REQUIRED")
        void shouldReturnContactAdminRequired() throws Exception {
            doThrow(new ContactAdminException(
                    "Aucune adresse email n'est associée à votre compte. Veuillez contacter un administrateur de votre établissement."))
                    .when(emailVerificationService).sendPasswordResetCode(anyString(), anyString(), anyString());

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTACT_ADMIN_REQUIRED"));
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /reset-password")
    class ResetPasswordEndpointTests {

        private final String validBody = "{\"uid\":\"dupontj\",\"code\":\"123456\",\"charteAccepted\":true,"
                + "\"newPassword\":\"N3wPassw0rd!X\",\"confirmPassword\":\"N3wPassw0rd!X\"}";

        private void serviceThrows(RuntimeException ex) {
            doThrow(ex).when(emailVerificationService).processResetPassword(
                    eq("dupontj"), eq("123456"), anyString(), anyString(), eq(true));
        }

        @Test
        @DisplayName("Succès → 200 PASSWORD_RESET_SUCCESS")
        void shouldReturnPasswordResetSuccess() throws Exception {
            doNothing().when(emailVerificationService).processResetPassword(
                    anyString(), anyString(), anyString(), anyString(), anyBoolean());

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_SUCCESS"));

            verify(emailVerificationService).processResetPassword(
                    "dupontj", "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", true);
        }

        @Test
        @DisplayName("Code invalide → 400 INVALID_CODE")
        void shouldMapInvalidCode() throws Exception {
            serviceThrows(new InvalidCodeException("Le code de réinitialisation est incorrect"));

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }

        @Test
        @DisplayName("Code expiré → 400 CODE_EXPIRED")
        void shouldMapCodeExpired() throws Exception {
            serviceThrows(new CodeExpiredException("Le code de réinitialisation a expiré"));

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        }

        @Test
        @DisplayName("Trop de tentatives → 429 MAX_ATTEMPTS_EXCEEDED")
        void shouldMapMaxAttempts() throws Exception {
            serviceThrows(new MaxAttemptsExceededException("Trop de tentatives échouées"));

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("MAX_ATTEMPTS_EXCEEDED"));
        }

        @Test
        @DisplayName("Charte non acceptée → 400 CHARTE_REQUIRED")
        void shouldMapCharteRequired() throws Exception {
            serviceThrows(new CharteNotAcceptedException("Vous devez accepter les conditions générales"));

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHARTE_REQUIRED"));
        }

        @Test
        @DisplayName("Mot de passe faible → 400 WEAK_PASSWORD")
        void shouldMapWeakPassword() throws Exception {
            serviceThrows(new WeakPasswordException("Le mot de passe doit contenir au moins 12 caractères"));

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        }

        @Test
        @DisplayName("Mots de passe différents → 400 BAD_REQUEST")
        void shouldMapPasswordMismatch() throws Exception {
            serviceThrows(new IllegalArgumentException("La confirmation du mot de passe ne correspond pas"));

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("Code non conforme (≠ 6 chiffres) → 400 VALIDATION_ERROR")
        void shouldValidateSixDigitCode() throws Exception {
            String body = "{\"uid\":\"dupontj\",\"code\":\"12ab56\",\"charteAccepted\":true,"
                    + "\"newPassword\":\"N3wPassw0rd!X\",\"confirmPassword\":\"N3wPassw0rd!X\"}";

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("newPassword manquant → 400 VALIDATION_ERROR")
        void shouldValidateMissingNewPassword() throws Exception {
            String body = "{\"uid\":\"dupontj\",\"code\":\"123456\",\"charteAccepted\":true,"
                    + "\"confirmPassword\":\"N3wPassw0rd!X\"}";

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(emailVerificationService);
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /search-uid")
    class SearchUidEndpointTests {

        private final String validBody = "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"jean.dupont@ac-orleans-tours.fr\","
                + "\"profil\":\"ELEVE\",\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                + "\"etablissement\":\"19450023200014\"}";

        private IExternalStructure structure(String id, String type, String ville) {
            IExternalStructure s = mock(IExternalStructure.class);
            when(s.getId()).thenReturn(id);
            when(s.getType()).thenReturn(type);
            when(s.getVille()).thenReturn(ville);
            return s;
        }

        @Test
        @DisplayName("Résultats trouvés → 200 avec la liste uid/displayName")
        void shouldReturnMatchingUids() throws Exception {
            IExternalStructure etab = structure("19450023200014", "COLLEGE", "ORLEANS");
            when(structureService.getAllStructures()).thenReturn(List.of(etab));
            when(aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(
                    eq("DUPONT"), eq("Jean"), eq("ELEVE"), any()))
                    .thenReturn(List.<Object[]>of(new Object[]{
                            "dupontj", "DUPONT Jean", 7L, "jean.dupont@ac-orleans-tours.fr", null}));

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].uid").value("dupontj"))
                    .andExpect(jsonPath("$[0].displayName").value("DUPONT Jean"));
        }

        @Test
        @DisplayName("Filtrage email : une ligne dont l'email ne correspond pas est exclue")
        void shouldFilterRowsByEmail() throws Exception {
            IExternalStructure etab = structure("19450023200014", "COLLEGE", "ORLEANS");
            when(structureService.getAllStructures()).thenReturn(List.of(etab));
            when(aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[]{
                            "autreuid", "AUTRE User", 8L, "autre@example.fr", null}));

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SEARCH_NO_RESULT"));
        }

        @Test
        @DisplayName("Aucun résultat en base → 200 SEARCH_NO_RESULT")
        void shouldReturnSearchNoResult() throws Exception {
            IExternalStructure etab = structure("19450023200014", "COLLEGE", "ORLEANS");
            when(structureService.getAllStructures()).thenReturn(List.of(etab));
            when(aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(any(), any(), any(), any()))
                    .thenReturn(List.of());

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SEARCH_NO_RESULT"));
        }

        @Test
        @DisplayName("Aucun SIREN résolu → SEARCH_NO_RESULT sans interroger la base (IN () vide)")
        void emptySirenFilterShortCircuitsWithoutDbCall() throws Exception {
            when(structureService.getAllStructures()).thenReturn(List.of());

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SEARCH_NO_RESULT"));

            verify(aPersonneRepository, never()).searchByNomPrenomAndCategorieAndSirens(
                    any(), any(), any(), any());
        }

        @Test
        @DisplayName("Champ obligatoire manquant → 400 VALIDATION_ERROR")
        void shouldValidateRequiredFields() throws Exception {
            String body = "{\"nom\":\"\",\"prenom\":\"Jean\",\"email\":\"j@x.fr\",\"profil\":\"ELEVE\","
                    + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"}";

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Chaque champ obligatoire manquant → 400 VALIDATION_ERROR, base jamais interrogée")
        void shouldRejectMissingFieldForEachRequiredField() throws Exception {
            String base = "\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                    + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"";
            String[] bodies = {
                    // nom manquant (clé absente)
                    "{\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"}",
                    // nom null
                    "{\"nom\":null," + base + "}",
                    // nom vide
                    "{\"nom\":\"\"," + base + "}",
                    // prenom manquant
                    "{\"nom\":\"DUPONT\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"}",
                    // profil manquant
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"}",
                    // typeEtablissement vide
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"}",
                    // ville manquante
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"etablissement\":\"1\"}",
                    // etablissement null
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":null}"
            };

            for (String body : bodies) {
                mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }

            verify(aPersonneRepository, never()).searchByNomPrenomAndCategorieAndSirens(
                    any(), any(), any(), any());
        }

        @Test
        @DisplayName("Type d'établissement inconnu → 400 VALIDATION_ERROR")
        void shouldRejectUnknownSurType() throws Exception {
            String body = "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"j@x.fr\",\"profil\":\"ELEVE\","
                    + "\"typeEtablissement\":\"PRIMAIRE\",\"ville\":\"ORLEANS\",\"etablissement\":\"1\"}";

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Plusieurs homonymes → 400 SEARCH_MULTIPLE_RESULTS")
        void shouldReturnSearchMultipleResults() throws Exception {
            IExternalStructure etab = structure("19450023200014", "COLLEGE", "ORLEANS");
            when(structureService.getAllStructures()).thenReturn(List.of(etab));
            when(aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(
                    eq("DUPONT"), eq("Jean"), eq("ELEVE"), any()))
                    .thenReturn(List.<Object[]>of(
                            new Object[]{"dupontj", "DUPONT Jean", 7L, "jean.dupont@ac-orleans-tours.fr", null},
                            new Object[]{"dupontj2", "DUPONT Jean", 8L, "jean.dupont@ac-orleans-tours.fr", null}));

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SEARCH_MULTIPLE_RESULTS"));
        }

        @Test
        @DisplayName("Email optionnel : search-uid sans email → fonctionne normalement")
        void shouldWorkWithoutEmail() throws Exception {
            String body = "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                    + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"19450023200014\"}";
            IExternalStructure etab = structure("19450023200014", "COLLEGE", "ORLEANS");
            when(structureService.getAllStructures()).thenReturn(List.of(etab));
            when(aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(
                    eq("DUPONT"), eq("Jean"), eq("ELEVE"), any()))
                    .thenReturn(List.<Object[]>of(new Object[]{
                            "dupontj", "DUPONT Jean", 7L, "jean.dupont@ac-orleans-tours.fr", null}));

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].uid").value("dupontj"));
        }

        @Test
        @DisplayName("Email optionnel : search-uid sans email → ne filtre pas par email")
        void shouldNotFilterByEmailWhenEmailIsBlank() throws Exception {
            String body = "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                    + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\",\"etablissement\":\"19450023200014\"}";
            IExternalStructure etab = structure("19450023200014", "COLLEGE", "ORLEANS");
            when(structureService.getAllStructures()).thenReturn(List.of(etab));
            when(aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(
                    eq("DUPONT"), eq("Jean"), eq("ELEVE"), any()))
                    .thenReturn(List.<Object[]>of(new Object[]{
                            "dupontj", "DUPONT Jean", 7L, "autre@autre.fr", null}));

            mockMvc.perform(post(SEARCH_UID_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].uid").value("dupontj"));
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /charte-status")
    class CharteStatusEndpointTests {

        @Test
        @DisplayName("Charte requise → charteSignee=false")
        void shouldReportCharteRequired() throws Exception {
            when(charteService.isCharteRequired("dupontj")).thenReturn(true);
            when(charteUrlResolver.resolve("charte.example.fr")).thenReturn("https://charte.example.fr/ac");

            mockMvc.perform(get(BASE_URL + "charte-status?uid=dupontj").header("Host", "charte.example.fr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.charteRequired").value(true))
                    .andExpect(jsonPath("$.charteUrl").value("https://charte.example.fr/ac"))
                    .andExpect(jsonPath("$.charteSignee").value(false));
        }

        @Test
        @DisplayName("Charte déjà signée → charteSignee=true")
        void shouldReportCharteSigned() throws Exception {
            when(charteService.isCharteRequired("dupontj")).thenReturn(false);
            when(charteUrlResolver.resolve("charte.example.fr")).thenReturn("https://charte.example.fr/ac");

            mockMvc.perform(get(BASE_URL + "charte-status?uid=dupontj").header("Host", "charte.example.fr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.charteRequired").value(false))
                    .andExpect(jsonPath("$.charteSignee").value(true));
        }

        @Test
        @DisplayName("Charte-status sans Host → URL par défaut")
        void shouldFallbackToDefaultUrlWithoutHost() throws Exception {
            when(charteService.isCharteRequired("dupontj")).thenReturn(true);
            when(charteUrlResolver.resolve(null)).thenReturn("https://lycees.netocentre.fr/files/textes/droits_usage.html");

            mockMvc.perform(get(BASE_URL + "charte-status?uid=dupontj"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.charteUrl").value("https://lycees.netocentre.fr/files/textes/droits_usage.html"));
        }
    }
}
