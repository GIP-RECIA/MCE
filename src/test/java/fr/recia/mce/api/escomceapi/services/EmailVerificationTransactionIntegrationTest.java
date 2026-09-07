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

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("it")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class EmailVerificationTransactionIntegrationTest {

    @Autowired
    private EmailVerificationService service;

    @Autowired
    private APersonneRepository personneRepository;

    @Autowired
    private CerbereConfirmationRepository confirmationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private PersonneService personneService;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    @MockBean
    private JavaMailSender mailSender;

    private APersonne person;

    @BeforeEach
    void setUp() {
        confirmationRepository.deleteAll();
        personneRepository.deleteAll();

        person = new APersonne();
        person.setUid("transaction-user");
        person.setSource("test");
        person.setCle("transaction-cle");
        person.setVersion(0L);
        person.setEtat("Valide");
        person.setCategorie("Enseignant");
        person.setEmail("transaction@example.fr");
        person = personneRepository.saveAndFlush(person);

        PersonneDTO dto = org.mockito.Mockito.mock(PersonneDTO.class);
        when(dto.getEnumPublic()).thenReturn(EnumPublic.PERSONNEL);
        when(dto.isNtPass()).thenReturn(true);
        when(personneService.getUserByUid(person.getUid())).thenReturn(dto);
        when(userDTOFactory.canResetPassword(any(PersonneDTO.class))).thenReturn(true);
        reset(mailSender);
    }

    @Test
    void sendsMailOnlyAfterARealCommit() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> service.sendPasswordResetCode(person.getUid(), person.getEmail(), "Enseignant"));

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(confirmationRepository.findPendingPasswordResetByPersonId(person.getId())).hasSize(1);
    }

    @Test
    void doesNotSendMailAndRollsBackConfirmationOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.sendPasswordResetCode(person.getUid(), person.getEmail(), "Enseignant");
            status.setRollbackOnly();
        });

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        List<CerbereConfirmation> confirmations = confirmationRepository.findPendingPasswordResetByPersonId(person.getId());
        assertThat(confirmations).isEmpty();
    }
}
