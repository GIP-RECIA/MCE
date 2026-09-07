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
import fr.recia.mce.api.escomceapi.services.ActivationService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.VerificationCodeService;
import fr.recia.mce.api.escomceapi.services.ConfirmationMailSender;
import fr.recia.mce.api.escomceapi.services.AttemptGuardService;
import fr.recia.mce.api.escomceapi.services.AccountEmailService;
import fr.recia.mce.api.escomceapi.services.PasswordResetPolicyService;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.CodeExpiredException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.exception.MaxAttemptsExceededException;
import fr.recia.mce.api.escomceapi.services.exception.ResendCooldownActiveException;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidAvatarException;
import org.springframework.security.access.AccessDeniedException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationResultDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationStatusResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.RecoverUidRequestDTO;
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
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
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
    private VerificationCodeService verificationCodeService;

    @MockBean
    @SuppressWarnings("unused")
    private ConfirmationMailSender confirmationMailSender;

    @MockBean
    @SuppressWarnings("unused")
    private AttemptGuardService attemptGuardService;

    @MockBean
    @SuppressWarnings("unused")
    private AccountEmailService accountEmailService;

    @MockBean
    @SuppressWarnings("unused")
    private PasswordResetPolicyService passwordResetPolicyService;

    @MockBean
    @SuppressWarnings("unused")
    private CharteService charteService;

    @MockBean
    @SuppressWarnings("unused")
    private ActivationService activationService;

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
            when(relationEleveServiceImpl.allRelationEleves(anyString())).thenReturn(relationsOf(enfantId));

            mockMvc.perform(get(BASE_URL + enfantId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non trouvé lorsque les détails d'un enfant sont nuls")
        void shouldReturnNotFoundWhenEnfantIsNull() throws Exception {
            String enfantId = "enfant.user";
            when(userDTOFactory.from(enfantId)).thenReturn(null);
            when(relationEleveServiceImpl.allRelationEleves(anyString())).thenReturn(relationsOf(enfantId));

            mockMvc.perform(get(BASE_URL + enfantId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Interdiction de consulter le profil d'un utilisateur sans lien")
        void shouldReturnForbiddenWhenAccessingUnrelatedProfile() throws Exception {
            String otherId = "autre.user";
            when(userDTOFactory.from(otherId)).thenReturn(new UserDTO());
            when(relationEleveServiceImpl.allRelationEleves(anyString())).thenReturn(relationsOf("enfant.user"));

            mockMvc.perform(get(BASE_URL + otherId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Autorisation de consulter son propre profil via /{id}")
        void shouldGetOwnDetail() throws Exception {
            UserDTO self = new UserDTO();
            when(userDTOFactory.from(USER)).thenReturn(self);

            mockMvc.perform(get(BASE_URL + USER))
                    .andExpect(status().isOk());
        }

        private List<RelationEleveContact> relationsOf(String uidRelation) {
            RelationEleveContact rel = new RelationEleveContact(RelationEleveContact.SensRel.CONTACT2ELEVE);
            rel.setUidRelation(uidRelation);
            rel.setDisplayNameRelation("Sara VAR");
            return List.of(rel);
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
            when(relationEleveServiceImpl.allRelationEleves(anyString())).thenReturn(relationsOf(enfantId));

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
    private static final String RECOVER_UID_URL = BASE_URL + "recover-uid";

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
        @DisplayName("Anti-double-clic (cooldown actif) → 429 RESEND_COOLDOWN avec retryAfterSeconds")
        void shouldReturnResendCooldown() throws Exception {
            doThrow(new ResendCooldownActiveException("Un code a déjà été envoyé récemment pour ce compte.", 42_000L))
                    .when(emailVerificationService).sendPasswordResetCode(anyString(), anyString(), anyString());

            mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RESEND_COOLDOWN"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("42 seconde(s)")))
                    .andExpect(jsonPath("$.retryAfterSeconds").value(42));
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
                    eq("dupontj"), any(), eq("123456"), anyString(), anyString(), eq(true));
        }

        @Test
        @DisplayName("Succès → 200 PASSWORD_RESET_SUCCESS")
        void shouldReturnPasswordResetSuccess() throws Exception {
            doNothing().when(emailVerificationService).processResetPassword(
                    anyString(), any(), anyString(), anyString(), anyString(), anyBoolean());

            mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("PASSWORD_RESET_SUCCESS"));

            verify(emailVerificationService).processResetPassword(
                    "dupontj", null, "123456", "N3wPassw0rd!X", "N3wPassw0rd!X", true);
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
    @DisplayName("Tests du point d'accès /activation")
    class ActivationEndpointTests {

        private static final String CONNEXION_URL = BASE_URL + "activation/connexion";
        private static final String STATUS_URL = BASE_URL + "activation/status";
        private static final String ACTIVATE_URL = BASE_URL + "activation/password";

        @Test
        @DisplayName("Connexion (login + mdp temporaire) réussie → 200 avec l'uid")
        void connexionShouldReturnUid() throws Exception {
            when(activationService.connexion("dupontj", "TempPass1!")).thenReturn("dupontj");

            mockMvc.perform(post(CONNEXION_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"dupontj\",\"password\":\"TempPass1!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uid").value("dupontj"));

            verify(activationService).connexion("dupontj", "TempPass1!");
        }

        @Test
        @DisplayName("Identifiants invalides → 400 BAD_REQUEST")
        void connexionShouldRejectInvalidCredentials() throws Exception {
            when(activationService.connexion(anyString(), anyString()))
                    .thenThrow(new IllegalArgumentException("Identifiants incorrects"));

            mockMvc.perform(post(CONNEXION_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"dupontj\",\"password\":\"Mauvais\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("Login manquant → 400 VALIDATION_ERROR")
        void connexionShouldValidateMissingLogin() throws Exception {
            mockMvc.perform(post(CONNEXION_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"TempPass1!\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(activationService);
        }

        @Test
        @DisplayName("Statut d'activation → 200 avec le parcours")
        void statusShouldReturnParcours() throws Exception {
            ActivationStatusResponseDTO status = ActivationStatusResponseDTO.builder()
                    .uid("dupontj")
                    .etat("Invalide")
                    .charteRequise(true)
                    .charteSignee(false)
                    .emailRequise(true)
                    .passwordRequise(true)
                    .etapeSuivante("CHARTE")
                    .build();
            when(activationService.getActivationStatus("dupontj")).thenReturn(status);

            mockMvc.perform(get(STATUS_URL).param("uid", "dupontj"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uid").value("dupontj"))
                    .andExpect(jsonPath("$.charteRequise").value(true))
                    .andExpect(jsonPath("$.etapeSuivante").value("CHARTE"));

            verify(activationService).getActivationStatus("dupontj");
        }

        @Test
        @DisplayName("Activation (charte + mot de passe) réussie → 200, compte Valide")
        void activateShouldSucceed() throws Exception {
            ActivationResultDTO result = new ActivationResultDTO("dupontj", "Valide", false);
            when(activationService.activate(any())).thenReturn(result);

            mockMvc.perform(post(ACTIVATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"uid\":\"dupontj\",\"charteAccepted\":true,"
                                    + "\"newPassword\":\"N3wPassw0rd!X\",\"confirmPassword\":\"N3wPassw0rd!X\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uid").value("dupontj"))
                    .andExpect(jsonPath("$.etat").value("Valide"));

            ArgumentCaptor<ActivationRequestDTO> captor = ArgumentCaptor.forClass(ActivationRequestDTO.class);
            verify(activationService).activate(captor.capture());
            assertThat(captor.getValue().getUid()).isEqualTo("dupontj");
            assertThat(captor.getValue().isCharteAccepted()).isTrue();
        }

        @Test
        @DisplayName("Activation sans uid → 400 VALIDATION_ERROR")
        void activateShouldValidateMissingUid() throws Exception {
            mockMvc.perform(post(ACTIVATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"charteAccepted\":true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verifyNoInteractions(activationService);
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /recover-uid")
    class RecoverUidEndpointTests {

        private final String validBody = "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\","
                + "\"email\":\"jean.dupont@ac-orleans-tours.fr\",\"profil\":\"ELEVE\","
                + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                + "\"etablissement\":\"19450023200014\"}";

        @Test
        @DisplayName("Réponse générique RECOVER_CODE_SENT et aucune fuite d'uid dans le corps")
        void shouldReturnGenericResponseWithoutUid() throws Exception {
            mockMvc.perform(post(RECOVER_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("RECOVER_CODE_SENT"));

            ArgumentCaptor<RecoverUidRequestDTO> captor = ArgumentCaptor.forClass(RecoverUidRequestDTO.class);
            verify(emailVerificationService).recoverUid(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("jean.dupont@ac-orleans-tours.fr");
            assertThat(captor.getValue().getProfil()).isEqualTo("ELEVE");
            assertThat(captor.getValue().getEtablissement()).isEqualTo("19450023200014");
        }

        @Test
        @DisplayName("Aucun résultat → quand même 200 RECOVER_CODE_SENT (pas d'énumération)")
        void shouldReturnGenericResponseEvenWithoutAccount() throws Exception {
            when(emailVerificationService.recoverUid(any(RecoverUidRequestDTO.class))).thenReturn(null);

            mockMvc.perform(post(RECOVER_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("RECOVER_CODE_SENT"));
        }

        @Test
        @DisplayName("Charte requise → RECOVER_CODE_SENT avec charteRequired + charteUrl, sans uid")
        void shouldReturnCharteInfoWhenRequired() throws Exception {
            when(emailVerificationService.recoverUid(any(RecoverUidRequestDTO.class)))
                    .thenReturn(new EmailVerificationService.RecoverUidResult(true, "https://example.test/charte"));

            mockMvc.perform(post(RECOVER_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("RECOVER_CODE_SENT"))
                    .andExpect(jsonPath("$.charteRequired").value(true))
                    .andExpect(jsonPath("$.charteUrl").value("https://example.test/charte"))
                    .andExpect(jsonPath("$.uid").doesNotExist());
        }

        @Test
        @DisplayName("Charte déjà signée → charteRequired=false et charteUrl absent")
        void shouldReturnCharteNotRequiredWhenSigned() throws Exception {
            when(emailVerificationService.recoverUid(any(RecoverUidRequestDTO.class)))
                    .thenReturn(new EmailVerificationService.RecoverUidResult(false, null));

            mockMvc.perform(post(RECOVER_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("RECOVER_CODE_SENT"))
                    .andExpect(jsonPath("$.charteRequired").value(false))
                    .andExpect(jsonPath("$.charteUrl").doesNotExist());
        }

        @Test
        @DisplayName("Réponse générique : jamais de champ uid/displayName")
        void shouldNeverExposeUid() throws Exception {
            mockMvc.perform(post(RECOVER_UID_URL).contentType(MediaType.APPLICATION_JSON).content(validBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").doesNotExist())
                    .andExpect(jsonPath("$.uid").doesNotExist())
                    .andExpect(jsonPath("$.displayName").doesNotExist());
        }

        @Test
        @DisplayName("Champ identité manquant/vide → 400 VALIDATION_ERROR, service jamais appelé")
        void shouldRejectMissingIdentityField() throws Exception {
            String[] bodies = {
                    // email invalide
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"pas-un-email\","
                            + "\"profil\":\"ELEVE\",\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                            + "\"etablissement\":\"19450023200014\"}",
                    // nom manquant
                    "{\"prenom\":\"Jean\",\"email\":\"jean.dupont@ac-orleans-tours.fr\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                            + "\"etablissement\":\"19450023200014\"}",
                    // prénom manquant
                    "{\"nom\":\"DUPONT\",\"email\":\"jean.dupont@ac-orleans-tours.fr\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                            + "\"etablissement\":\"19450023200014\"}",
                    // email manquant
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"profil\":\"ELEVE\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                            + "\"etablissement\":\"19450023200014\"}",
                    // profil manquant
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"jean.dupont@ac-orleans-tours.fr\","
                            + "\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\","
                            + "\"etablissement\":\"19450023200014\"}",
                    // typeEtablissement manquant
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"jean.dupont@ac-orleans-tours.fr\","
                            + "\"profil\":\"ELEVE\",\"ville\":\"ORLEANS\",\"etablissement\":\"19450023200014\"}",
                    // ville manquante
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"jean.dupont@ac-orleans-tours.fr\","
                            + "\"profil\":\"ELEVE\",\"typeEtablissement\":\"COLLEGE\","
                            + "\"etablissement\":\"19450023200014\"}",
                    // etablissement manquant
                    "{\"nom\":\"DUPONT\",\"prenom\":\"Jean\",\"email\":\"jean.dupont@ac-orleans-tours.fr\","
                            + "\"profil\":\"ELEVE\",\"typeEtablissement\":\"COLLEGE\",\"ville\":\"ORLEANS\"}"
            };
            for (String body : bodies) {
                mockMvc.perform(post(RECOVER_UID_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
            verify(emailVerificationService, never()).recoverUid(any(RecoverUidRequestDTO.class));
        }
    }

    @Nested
    @DisplayName("Tests du point d'accès /charte-status")
    class CharteStatusEndpointTests {

        @Test
        @DisplayName("Charte requise → charteSignee=false")
        void shouldReportCharteRequired() throws Exception {
            when(charteService.isCharteRequired("dupontj")).thenReturn(true);
            when(charteService.getCharteUrl("dupontj")).thenReturn("https://charte.example.fr/ac");

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
            when(charteService.getCharteUrl("dupontj")).thenReturn("https://charte.example.fr/ac");

            mockMvc.perform(get(BASE_URL + "charte-status?uid=dupontj").header("Host", "charte.example.fr"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.charteRequired").value(false))
                    .andExpect(jsonPath("$.charteUrl").value("https://charte.example.fr/ac"))
                    .andExpect(jsonPath("$.charteSignee").value(true));
        }

        @Test
        @DisplayName("Charte-status : source inconnue → URL par défaut")
        void shouldFallbackToDefaultUrlWithoutHost() throws Exception {
            when(charteService.isCharteRequired("dupontj")).thenReturn(true);
            when(charteService.getCharteUrl("dupontj")).thenReturn("https://lycees.netocentre.fr/files/textes/droits_usage.html");

            mockMvc.perform(get(BASE_URL + "charte-status?uid=dupontj"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.charteUrl").value("https://lycees.netocentre.fr/files/textes/droits_usage.html"));
        }
    }
}
