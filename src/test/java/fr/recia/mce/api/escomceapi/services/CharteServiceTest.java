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
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - CharteService")
class CharteServiceTest {

    @Mock
    private CharteProperties charteProperties;

    @Mock
    private APersonneRepository aPersonneRepository;

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

    private APersonne mockPersonne(String source) {
        APersonne p = new APersonne();
        p.setSource(source);
        return p;
    }

    // ── isCharteRequired ────────────────────────────────────────────────

    @Test
    @DisplayName("isCharteRequired : uid null ou vide → requis")
    void blankUidRequiresCharte() {
        assertThat(service.isCharteRequired((String) null)).isTrue();
        assertThat(service.isCharteRequired("")).isTrue();
        assertThat(service.isCharteRequired("   ")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : personne introuvable → requis")
    void unknownPersonRequiresCharte() {
        when(aPersonneRepository.findByUid("ghost")).thenReturn(null);

        assertThat(service.isCharteRequired("ghost")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : charte déjà signée (date en base) → non requise")
    void signedCharteNotRequired() {
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        p.setValidationCharte(new Date());
        when(aPersonneRepository.findByUid("alice")).thenReturn(p);

        assertThat(service.isCharteRequired("alice")).isFalse();
    }

    @Test
    @DisplayName("isCharteRequired : aucune date de signature en base → requise")
    void unsignedCharteRequired() {
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        when(aPersonneRepository.findByUid("bob")).thenReturn(p);

        assertThat(service.isCharteRequired("bob")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : erreur de chargement → requis par précaution")
    void loadErrorRequiresCharte() {
        when(aPersonneRepository.findByUid("broken")).thenThrow(new RuntimeException("DB down"));

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
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        when(aPersonneRepository.findByUid("bob")).thenReturn(p);

        assertThat(service.getCharteUrl("bob")).isEqualTo("https://charte/ac-orleans-tours");
    }

    @Test
    @DisplayName("getCharteUrl : repli sur le préfixe de la source (avant le tiret)")
    void resolvesSourcePrefix() {
        urls.remove("AC-ORLEANS-TOURS");
        urls.put("AC", "https://charte/ac");
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        when(aPersonneRepository.findByUid("carol")).thenReturn(p);

        assertThat(service.getCharteUrl("carol")).isEqualTo("https://charte/ac");
    }

    @Test
    @DisplayName("getCharteUrl : source inconnue → URL par défaut")
    void unknownSourceReturnsDefaultUrl() {
        APersonne p = mockPersonne("INCONNU");
        when(aPersonneRepository.findByUid("dave")).thenReturn(p);

        assertThat(service.getCharteUrl("dave")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : source null → URL par défaut")
    void nullSourceReturnsDefaultUrl() {
        APersonne p = mockPersonne(null);
        when(aPersonneRepository.findByUid("eve")).thenReturn(p);

        assertThat(service.getCharteUrl("eve")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : personne introuvable → URL par défaut")
    void unknownPersonReturnsDefaultUrl() {
        when(aPersonneRepository.findByUid("ghost")).thenReturn(null);

        assertThat(service.getCharteUrl("ghost")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : erreur de chargement → URL par défaut")
    void loadErrorReturnsDefaultUrl() {
        when(aPersonneRepository.findByUid("broken")).thenThrow(new RuntimeException("boom"));

        assertThat(service.getCharteUrl("broken")).isEqualTo("https://charte/default");
    }
}
