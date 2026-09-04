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

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.interceptor.SoffitInterceptor;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.ActivationService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PersonneRestController.class)
@AutoConfigureMockMvc
class PersonneRestControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MCEProperties mceProperties;

    @MockBean
    private PersonneService personneService;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    @MockBean
    private SoffitHolder soffitHolder;

    @MockBean
    private SoffitInterceptor soffitInterceptor;

    @MockBean
    private LdapTemplate ldapTemplate;

    @MockBean
    private FonctionService fonctionService;

    @MockBean
    private PasswordService passwordService;

    @MockBean
    private RelationEleveServiceImpl relationEleveServiceImpl;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private CharteService charteService;

    @MockBean
    private ActivationService activationService;

    @MockBean
    private APersonneRepository aPersonneRepository;

    @MockBean
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    @MockBean
    private IStructureService structureService;

    @BeforeEach
    void setUp() {
        mceProperties.getSecurity().getRateLimit().setPermitsPerSecond(1_000_000.0);
    }

    @Test
    void passwordRecoveryRemainsPublicWithoutAuthentication() throws Exception {
        doNothing().when(emailVerificationService)
                .sendPasswordResetCode(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/personne/mce/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uid\":\"user1\",\"email\":\"user1@example.fr\",\"profil\":\"PERSONNEL\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRemainsPublicWithoutAuthentication() throws Exception {
        doNothing().when(emailVerificationService)
                .processResetPassword(anyString(), any(), anyString(), anyString(), anyString(), anyBoolean());

        mockMvc.perform(post("/api/personne/mce/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uid\":\"user1\",\"code\":\"123456\",\"charteAccepted\":true,"
                        + "\"newPassword\":\"N3wPassw0rd!X\",\"confirmPassword\":\"N3wPassw0rd!X\"}"))
                .andExpect(status().isOk());
    }

}
