/*
 * Copyright (C) 2026 GIP-RECIA, Inc.
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
import fr.recia.mce.api.escomceapi.services.FonctionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.ldap.core.LdapTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import fr.recia.mce.api.escomceapi.configuration.interceptor.SoffitInterceptor;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;

@WebMvcTest(FonctionRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class FonctionRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FonctionService fonctionService;

    @MockBean
    private LdapTemplate ldapTemplate;

    @MockBean
    private SoffitHolder soffitHolder;

    @MockBean
    private SoffitInterceptor soffitInterceptor;

    @MockBean
    private fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository aPersonneRepository;

    @MockBean
    private fr.recia.mce.api.escomceapi.db.repositories.CerberePasswordRepository cerberePasswordRepository;

    @MockBean
    private fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository fonctionRepository;

    @MockBean
    private fr.recia.mce.api.escomceapi.configuration.bean.MailProperties mailProperties;

    @MockBean
    private fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository cerbereConfirmationRepository;

    @Test
    @DisplayName("Mise à jour de dateFin réussie")
    void shouldUpdateDateFinSuccessfully() throws Exception {
        Long fonctionId = 1L;
        boolean active = true;

        doNothing().when(fonctionService).updateDateFin(eq(fonctionId), eq(active));

        mockMvc.perform(put("/api/personne/fonction/" + fonctionId + "/dateFin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(active)))
                .andExpect(status().isOk());
    }
}
