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

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.ActivationService;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.VerificationCodeService;
import fr.recia.mce.api.escomceapi.services.ConfirmationMailSender;
import fr.recia.mce.api.escomceapi.services.AttemptGuardService;
import fr.recia.mce.api.escomceapi.services.AccountEmailService;
import fr.recia.mce.api.escomceapi.services.PasswordResetPolicyService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 0 du plan de reprise (docs/secu.md).
 * <p>
 * Prouve le comportement sur lequel repose l'essentiel du plan : l'intercepteur
 * {@code SoffitInterceptor} extrait le {@code sub} du JWT en base64 sans
 * vérifier la signature. En environnement de test, sans la clé CAS, le token du
 * front est donc exploitable par {@code getCurrentUid()} (mais jamais validé par
 * le filtre → les endpoints « authentifiés par le filtre » restent impossibles).
 */
@WebMvcTest(controllers = PersonneRestController.class)
@AutoConfigureMockMvc
class SoffitEtape0IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonneService personneService;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private VerificationCodeService verificationCodeService;

    @MockBean
    private ConfirmationMailSender confirmationMailSender;

    @MockBean
    private AttemptGuardService attemptGuardService;

    @MockBean
    private AccountEmailService accountEmailService;

    @MockBean
    private PasswordResetPolicyService passwordResetPolicyService;

    @MockBean
    private PasswordService passwordService;

    @MockBean
    private FonctionService fonctionService;

    @MockBean
    private RelationEleveServiceImpl relationEleveServiceImpl;

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

    @MockBean
    private org.springframework.ldap.core.LdapTemplate ldapTemplate;

    @Test
    void subIsExtractedAndUsedDespiteInvalidSignature() throws Exception {
        String payload = Base64.getUrlEncoder()
                .encodeToString("{\"sub\":\"user1\",\"exp\":9999999999}".getBytes());
        String bogusJwt = "eyJhbGciOiJIUzUxMiJ9." + payload + ".signature-clairement-invalide";

        mockMvc.perform(get("/api/personne/mce/debug-id")
                .header("Authorization", "Bearer " + bogusJwt))
                .andExpect(status().isOk())
                .andExpect(content().string("user1"));
    }

    @Test
    void noTokenMeansNoSubAndRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/personne/mce/debug-id"))
                .andExpect(status().isForbidden());
    }

}