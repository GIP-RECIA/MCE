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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.exception.GlobalExceptionHandler;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;

@ExtendWith(MockitoExtension.class)
class FonctionRestControllerTest {

    private static final String USER = "test.user";

    @Mock
    private FonctionService fonctionService;

    @Mock
    private PersonneService personneService;

    @Mock
    private IRelationEleveService relationEleveService;

    @Mock
    private SoffitHolder soffitHolder;

    @InjectMocks
    private FonctionRestController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        when(soffitHolder.getSub()).thenReturn(USER);
    }

    private PersonneDTO selfPersonne(Long id) {
        APersonne aPersonne = new APersonne();
        aPersonne.setId(id);
        aPersonne.setUid(USER);
        aPersonne.setAStructure(new AStructure());
        return new PersonneDTO(aPersonne);
    }

    @Test
    @DisplayName("Mise à jour de dateFin réussie sur sa propre fonction")
    void shouldUpdateDateFinSuccessfully() throws Exception {
        Long fonctionId = 1L;
        boolean active = true;
        when(personneService.retrievePersonnebyUid(USER)).thenReturn(selfPersonne(100L));
        when(fonctionService.getPersonIdOfFonction(fonctionId)).thenReturn(100L);
        doNothing().when(fonctionService).updateDateFin(eq(fonctionId), eq(active));

        mockMvc.perform(put("/api/personne/fonction/" + fonctionId + "/dateFin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(active)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mise à jour de dateFin refusée sur la fonction d'autrui")
    void shouldReturnForbiddenWhenUpdatingOthersFonction() throws Exception {
        Long fonctionId = 2L;
        boolean active = true;
        when(personneService.retrievePersonnebyUid(USER)).thenReturn(selfPersonne(100L));
        when(fonctionService.getPersonIdOfFonction(fonctionId)).thenReturn(999L);

        mockMvc.perform(put("/api/personne/fonction/" + fonctionId + "/dateFin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(active)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lecture des fonctions autorisée pour soi")
    void shouldGetOwnFonctions() throws Exception {
        when(personneService.retrievePersonnebyUid(USER)).thenReturn(selfPersonne(100L));

        mockMvc.perform(get("/api/personne/fonction/100"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lecture des fonctions refusée pour une personne sans lien")
    void shouldReturnForbiddenForUnrelatedFonctions() throws Exception {
        when(personneService.retrievePersonnebyUid(USER)).thenReturn(selfPersonne(100L));
        when(relationEleveService.allRelationEleves(anyString())).thenReturn(Collections.emptyList());
        when(relationEleveService.allEleveEnRelation(eq(100L))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/personne/fonction/500"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lecture des fonctions refusée sans authentification")
    void shouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        when(soffitHolder.getSub()).thenReturn(null);

        mockMvc.perform(get("/api/personne/fonction/100"))
                .andExpect(status().isForbidden());
    }

}
