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
package fr.recia.mce.api.escomceapi;

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.entities.Login;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class PasswordResetIntegrationTest {

    private static final String LOGIN_NOM = "Integration.User";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private APersonneRepository personneRepository;

    @Autowired
    private CerbereConfirmationRepository confirmationRepository;

    @Autowired
    private MCEProperties mceProperties;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockBean
    private PersonneService personneService;

    @MockBean
    private fr.recia.mce.api.escomceapi.services.PasswordService passwordService;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    private APersonne person;

    @BeforeEach
    void setUp() {
        mceProperties.getSecurity().getRateLimit().setPermitsPerSecond(1_000_000.0);
        confirmationRepository.deleteAll();
        new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.createQuery("DELETE FROM Login l WHERE l.nom = :nom")
                    .setParameter("nom", LOGIN_NOM).executeUpdate();
            return null;
        });
        personneRepository.deleteAll();

        person = new APersonne();
        person.setUid("integration-user");
        person.setSource("test");
        person.setCle("integration-cle");
        person.setVersion(0L);
        person.setEtat("Valide");
        person.setCategorie("Enseignant");
        person.setEmail("integration@example.fr");
        person = personneRepository.saveAndFlush(person);

        Login login = new Login();
        login.setNom(LOGIN_NOM);
        login.setAPersonneByAPersonneLogin(person);
        login.setAPersonneByAPersonneAlias(person);
        new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.persist(login);
            entityManager.flush();
            return null;
        });

        PersonneDTO dto = org.mockito.Mockito.mock(PersonneDTO.class);
        when(dto.getEnumPublic()).thenReturn(EnumPublic.PERSONNEL);
        when(dto.isNtPass()).thenReturn(true);
        when(dto.isCharteValide()).thenReturn(true);
        when(personneService.getUserByUid(person.getUid())).thenReturn(dto);
        when(userDTOFactory.canResetPassword(any(PersonneDTO.class))).thenReturn(true);
    }

    @Test
    void forgotPasswordPersistsCodeAndSendsMailThroughRealControllerAndRepositories() throws Exception {
        mockMvc.perform(post("/api/personne/mce/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"Integration.User\",\"email\":\"integration@example.fr\","
                        + "\"profil\":\"Enseignant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RESET_CODE_SENT"));

        List<CerbereConfirmation> confirmations = confirmationRepository.findPendingPasswordResetByPersonId(person.getId());
        assertThat(confirmations).hasSize(1);
        assertThat(confirmations.get(0).getCode()).startsWith("RESET:");
        assertThat(confirmations.get(0).getMail()).isEqualTo("integration@example.fr");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetPasswordConsumesPersistedCodeAfterForgotPassword() throws Exception {
        mockMvc.perform(post("/api/personne/mce/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"Integration.User\",\"email\":\"integration@example.fr\","
                        + "\"profil\":\"Enseignant\"}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<SimpleMailMessage> mailCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        Matcher codeMatcher = Pattern.compile("\\b\\d{6}\\b").matcher(mailCaptor.getValue().getText());
        assertThat(codeMatcher.find()).isTrue();
        String code = codeMatcher.group();

        mockMvc.perform(post("/api/personne/mce/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"Integration.User\",\"code\":\"" + code
                        + "\",\"charteAccepted\":true,\"newPassword\":\"N3wPassw0rd!X\","
                        + "\"confirmPassword\":\"N3wPassw0rd!X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_SUCCESS"));

        verify(passwordService).resetPassword(any(PersonneDTO.class), any(String.class), any(String.class));
        assertThat(confirmationRepository.findPendingPasswordResetByPersonId(person.getId())).isEmpty();
        assertThat(confirmationRepository.findLatestPasswordResetByPersonId(person.getId()))
                .singleElement()
                .satisfies(confirmation -> assertThat(confirmation.getConfirmation()).isNotNull());
    }
}
