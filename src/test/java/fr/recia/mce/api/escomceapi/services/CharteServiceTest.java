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

import fr.recia.mce.api.escomceapi.configuration.bean.CharteProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - CharteService")
class CharteServiceTest {

    @Mock
    private CharteProperties charteProperties;

    @Mock
    private PersonneService personneService;

    @InjectMocks
    private CharteService service;

    private Map<String, String> urls;

    @BeforeEach
    void setUp() {
        urls = new HashMap<>();
        urls.put("AC-ORLEANS-TOURS", "https://charte/ac-orleans-tours");
        urls.put("TIL", "https://charte/til");
        lenient().when(charteProperties.getUrls()).thenReturn(urls);
        lenient().when(charteProperties.getDefaultUrl()).thenReturn("https://charte/default");
    }

    private PersonneDTO mockPersonne(String source) {
        PersonneDTO dto = mock(PersonneDTO.class);
        lenient().when(dto.getStructureDto()).thenReturn(new StructureDTO(null));
        lenient().when(dto.getSource()).thenReturn(source);
        return dto;
    }

    // ── isCharteRequired ────────────────────────────────────────────────

    @Test
    @DisplayName("isCharteRequired : uid null ou vide → requis")
    void blankUidRequiresCharte() {
        assertThat(service.isCharteRequired(null)).isTrue();
        assertThat(service.isCharteRequired("")).isTrue();
        assertThat(service.isCharteRequired("   ")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : personne introuvable → requis")
    void unknownPersonRequiresCharte() {
        when(personneService.getUserByUid("ghost")).thenReturn(null);

        assertThat(service.isCharteRequired("ghost")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : charte déjà signée → non requise")
    void signedCharteNotRequired() {
        PersonneDTO dto = mock(PersonneDTO.class);
        when(personneService.getUserByUid("alice")).thenReturn(dto);
        when(dto.isCharteValide()).thenReturn(true);

        assertThat(service.isCharteRequired("alice")).isFalse();
    }

    @Test
    @DisplayName("isCharteRequired : erreur de chargement → requis par précaution")
    void loadErrorRequiresCharte() {
        when(personneService.getUserByUid("broken")).thenThrow(new RuntimeException("LDAP down"));

        assertThat(service.isCharteRequired("broken")).isTrue();
    }

    // ── getCharteUrl ────────────────────────────────────────────────────

    @Test
    @DisplayName("getCharteUrl : uid vide → URL par défaut")
    void blankUidReturnsDefaultUrl() {
        assertThat(service.getCharteUrl(null)).isEqualTo("https://charte/default");
        assertThat(service.getCharteUrl("")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : source exacte trouvée dans la map")
    void resolvesExactSource() {
        PersonneDTO dto = mockPersonne("AC-ORLEANS-TOURS");
        when(personneService.getUserByUid("bob")).thenReturn(dto);

        assertThat(service.getCharteUrl("bob")).isEqualTo("https://charte/ac-orleans-tours");
    }

    @Test
    @DisplayName("getCharteUrl : repli sur le préfixe de la source (avant le tiret)")
    void resolvesSourcePrefix() {
        urls.remove("AC-ORLEANS-TOURS");
        urls.put("AC", "https://charte/ac");
        PersonneDTO dto = mockPersonne("AC-ORLEANS-TOURS");
        when(personneService.getUserByUid("carol")).thenReturn(dto);

        assertThat(service.getCharteUrl("carol")).isEqualTo("https://charte/ac");
    }

    @Test
    @DisplayName("getCharteUrl : source inconnue → URL par défaut")
    void unknownSourceReturnsDefaultUrl() {
        PersonneDTO dto = mockPersonne("INCONNU");
        when(personneService.getUserByUid("dave")).thenReturn(dto);

        assertThat(service.getCharteUrl("dave")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : source null → URL par défaut")
    void nullSourceReturnsDefaultUrl() {
        PersonneDTO dto = mockPersonne(null);
        when(personneService.getUserByUid("eve")).thenReturn(dto);

        assertThat(service.getCharteUrl("eve")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : erreur de chargement → URL par défaut")
    void loadErrorReturnsDefaultUrl() {
        when(personneService.getUserByUid("broken")).thenThrow(new RuntimeException("boom"));

        assertThat(service.getCharteUrl("broken")).isEqualTo("https://charte/default");
    }
}
