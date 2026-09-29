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
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.ValidationCharte;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.ValidationCharteRepository;
import fr.recia.mce.api.escomceapi.ldap.ExternalUser;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - CharteService")
class CharteServiceTest {

    @Mock
    private CharteProperties charteProperties;

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private ValidationCharteRepository validationCharteRepository;

    @Mock
    private SoffitHolder soffitHolder;

    @Mock
    private IExternalUserDao externalUserDao;

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
        lenient().when(charteProperties.getDomains()).thenReturn(Map.of());
    }

    private APersonne mockPersonne(String source) {
        APersonne p = new APersonne();
        p.setId(1L);
        p.setSource(source);
        return p;
    }

    private IExternalUser externalUserWithDomains(String... hosts) {
        ExternalUser user = new ExternalUser();
        user.setAttributes(Map.of("ESCODomaines", Arrays.asList(hosts)));
        return user;
    }

    private APersonne personWithDomains(String uid, String source, String... hosts) {
        APersonne p = mockPersonne(source);
        p.setUid(uid);
        when(externalUserDao.getUserByUid(uid)).thenReturn(externalUserWithDomains(hosts));
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
        when(aPersonneRepository.findByLogin("ghost")).thenReturn(null);

        assertThat(service.isCharteRequired("ghost")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : charte déjà validée (ligne en base) → non requise")
    void signedCharteNotRequired() {
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        p.setUid("alice");
        when(aPersonneRepository.findByLogin("alice")).thenReturn(p);
        when(validationCharteRepository.findByApersonneIdAndServiceId(1L, "AC-ORLEANS-TOURS"))
                .thenReturn(new ValidationCharte());

        assertThat(service.isCharteRequired("alice")).isFalse();
    }

    @Test
    @DisplayName("isCharteRequired : version signée identique à celle du CSV → non requise")
    void signedCharteMatchingVersionNotRequired() {
        ValidationCharte v = new ValidationCharte();
        v.setCharterVersionDate(java.sql.Date.valueOf(CharteService.DEFAULT_CHARTE_VERSION_DATE));
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        p.setUid("alice");
        when(aPersonneRepository.findByLogin("alice")).thenReturn(p);
        when(validationCharteRepository.findByApersonneIdAndServiceId(1L, "AC-ORLEANS-TOURS")).thenReturn(v);

        assertThat(service.isCharteRequired("alice")).isFalse();
    }

    @Test
    @DisplayName("isCharteRequired : charte mise à jour dans le CSV → re-signature requise")
    void signedCharteObsoleteVersionRequiresResign() {
        ValidationCharte v = new ValidationCharte();
        v.setCharterVersionDate(java.sql.Date.valueOf(java.time.LocalDate.of(2023, 1, 1)));
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        p.setUid("alice");
        when(aPersonneRepository.findByLogin("alice")).thenReturn(p);
        when(validationCharteRepository.findByApersonneIdAndServiceId(1L, "AC-ORLEANS-TOURS")).thenReturn(v);

        assertThat(service.isCharteRequired("alice")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : aucune validation en base → requise")
    void unsignedCharteRequired() {
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        p.setUid("bob");
        when(aPersonneRepository.findByLogin("bob")).thenReturn(p);
        when(validationCharteRepository.findByApersonneIdAndServiceId(1L, "AC-ORLEANS-TOURS")).thenReturn(null);

        assertThat(service.isCharteRequired("bob")).isTrue();
    }

    @Test
    @DisplayName("isCharteRequired : personne sans source → service 'default'")
    void blankSourceUsesDefaultDom() {
        APersonne p = new APersonne();
        p.setId(2L);
        p.setUid("carol");
        when(aPersonneRepository.findByLogin("carol")).thenReturn(p);
        when(validationCharteRepository.findByApersonneIdAndServiceId(2L, "default")).thenReturn(new ValidationCharte());

        assertThat(service.isCharteRequired("carol")).isFalse();
        verify(validationCharteRepository).findByApersonneIdAndServiceId(2L, "default");
    }

    @Test
    @DisplayName("isCharteRequired : erreur de chargement → requis par précaution")
    void loadErrorRequiresCharte() {
        when(aPersonneRepository.findByLogin("broken")).thenThrow(new RuntimeException("DB down"));

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
        when(aPersonneRepository.findByLogin("bob")).thenReturn(p);

        assertThat(service.getCharteUrl("bob")).isEqualTo("https://charte/ac-orleans-tours");
    }

    @Test
    @DisplayName("getCharteUrl : repli sur le préfixe de la source (avant le tiret)")
    void resolvesSourcePrefix() {
        urls.remove("AC-ORLEANS-TOURS");
        urls.put("AC", "https://charte/ac");
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");
        when(aPersonneRepository.findByLogin("carol")).thenReturn(p);

        assertThat(service.getCharteUrl("carol")).isEqualTo("https://charte/ac");
    }

    @Test
    @DisplayName("getCharteUrl : source inconnue → URL par défaut")
    void unknownSourceReturnsDefaultUrl() {
        APersonne p = mockPersonne("INCONNU");
        when(aPersonneRepository.findByLogin("dave")).thenReturn(p);

        assertThat(service.getCharteUrl("dave")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : source null → URL par défaut")
    void nullSourceReturnsDefaultUrl() {
        APersonne p = mockPersonne(null);
        when(aPersonneRepository.findByLogin("eve")).thenReturn(p);

        assertThat(service.getCharteUrl("eve")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : personne introuvable → URL par défaut")
    void unknownPersonReturnsDefaultUrl() {
        when(aPersonneRepository.findByLogin("ghost")).thenReturn(null);

        assertThat(service.getCharteUrl("ghost")).isEqualTo("https://charte/default");
    }

    @Test
    @DisplayName("getCharteUrl : erreur de chargement → URL par défaut")
    void loadErrorReturnsDefaultUrl() {
        when(aPersonneRepository.findByLogin("broken")).thenThrow(new RuntimeException("boom"));

        assertThat(service.getCharteUrl("broken")).isEqualTo("https://charte/default");
    }

    // ── getCharteVersionDate ────────────────────────────────────────────

    @Test
    @DisplayName("getCharteVersionDate : version fournie par le CSV pour le domaine d'arrivée")
    void versionFromCsv() {
        when(charteProperties.getVersions()).thenReturn(new HashMap<>(Map.of("www.touraine-eschool.fr", "2023-09-01")));

        assertThat(service.getCharteVersionDate("www.touraine-eschool.fr"))
                .isEqualTo(java.sql.Date.valueOf(java.time.LocalDate.of(2023, 9, 1)));
    }

    @Test
    @DisplayName("getCharteVersionDate : le préfixe www. est indifférent entre la clé du CSV et le domaine")
    void versionIgnoresWwwPrefix() {
        when(charteProperties.getVersions()).thenReturn(new HashMap<>(Map.of("touraine-eschool.fr", "2023-09-01")));

        assertThat(service.getCharteVersionDate("www.touraine-eschool.fr"))
                .isEqualTo(java.sql.Date.valueOf(java.time.LocalDate.of(2023, 9, 1)));
    }

    @Test
    @DisplayName("getCharteVersionDate : domaine absent du CSV → repli sur la ligne 'default'")
    void versionFallsBackToDefaultRow() {
        when(charteProperties.getVersions()).thenReturn(new HashMap<>(Map.of("default", "2024-01-02")));

        assertThat(service.getCharteVersionDate("www.domaine-inconnu.fr"))
                .isEqualTo(java.sql.Date.valueOf(java.time.LocalDate.of(2024, 1, 2)));
    }

    @Test
    @DisplayName("getCharteVersionDate : aucune ligne pour le domaine ni 'default' → défaut applicatif")
    void versionFallsBackToDefault() {
        assertThat(service.getCharteVersionDate("www.touraine-eschool.fr"))
                .isEqualTo(java.sql.Date.valueOf(CharteService.DEFAULT_CHARTE_VERSION_DATE));
    }

    @Test
    @DisplayName("getCharteVersionDate : date invalide → défaut")
    void invalidVersionFallsBackToDefault() {
        when(charteProperties.getVersions()).thenReturn(new HashMap<>(Map.of("www.touraine-eschool.fr", "pas-une-date")));

        assertThat(service.getCharteVersionDate("www.touraine-eschool.fr"))
                .isEqualTo(java.sql.Date.valueOf(CharteService.DEFAULT_CHARTE_VERSION_DATE));
    }

    // ── Domaine du site d'arrivée (charte.domains / charte.urls + ESCODomaines) ──

    @Test
    @DisplayName("resolveService : hôte d'arrivée référencé ET rattaché à la personne → clé du domaine")
    void arrivalHostMappedAndInPersonDomainsWins() {
        when(soffitHolder.getArrivalHost()).thenReturn("lycees.test.recia.dev");
        when(charteProperties.getDomains()).thenReturn(Map.of("lycees.test.recia.dev", "LYCEE"));
        APersonne p = personWithDomains("fio", "COLL-45", "lycees.test.recia.dev", "cfa.netocentre.fr");

        assertThat(service.resolveService(p)).isEqualTo("LYCEE");
    }

    @Test
    @DisplayName("resolveService : hôte référencé mais absent des domaines de la personne → service 'default'")
    void arrivalHostNotInPersonDomainsFallsBackToDefault() {
        when(soffitHolder.getArrivalHost()).thenReturn("lycees.test.recia.dev");
        when(charteProperties.getDomains()).thenReturn(Map.of("lycees.test.recia.dev", "LYCEE"));
        APersonne p = personWithDomains("fio", "COLL-45", "www.chercan.fr");

        assertThat(service.resolveService(p)).isEqualTo("default");
    }

    @Test
    @DisplayName("resolveService : hôte inconnu (ni charte.domains ni charte.urls) → service 'default'")
    void unknownArrivalHostFallsBackToDefault() {
        when(soffitHolder.getArrivalHost()).thenReturn("inconnu.example.fr");
        APersonne p = mockPersonne("COLL-45");

        assertThat(service.resolveService(p)).isEqualTo("default");
    }

    @Test
    @DisplayName("resolveService : hôte déduit des URL (charte.urls) → clé du domaine")
    void arrivalHostResolvedFromUrls() {
        urls.put("COLL-18", "https://col18.lycees.netocentre.fr/files/textes/droits_usage.html");
        when(soffitHolder.getArrivalHost()).thenReturn("col18.lycees.netocentre.fr");
        lenient().when(charteProperties.getDomains())
                .thenReturn(new HashMap<>(Map.of("another.test.recia.dev", "LYCEE")));
        APersonne p = personWithDomains("col18user", "COLL-18", "col18.lycees.netocentre.fr");

        assertThat(service.resolveService(p)).isEqualTo("COLL-18");
    }

    @Test
    @DisplayName("resolveService : aucun hôte d'arrivée (hors requête web) → source, comme avant")
    void noArrivalHostUsesSource() {
        APersonne p = mockPersonne("AC-ORLEANS-TOURS");

        assertThat(service.resolveService(p)).isEqualTo("AC-ORLEANS-TOURS");
        verify(externalUserDao, org.mockito.Mockito.never()).getUserByUid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("isCharteRequired : charte signée pour le domaine d'arrivée → non requise")
    void signedCharteForArrivalDomainNotRequired() {
        when(soffitHolder.getArrivalHost()).thenReturn("lycees.test.recia.dev");
        when(charteProperties.getDomains()).thenReturn(Map.of("lycees.test.recia.dev", "LYCEE"));
        APersonne p = personWithDomains("fio", "COLL-45", "lycees.test.recia.dev");
        when(validationCharteRepository.findByApersonneIdAndServiceId(1L, "LYCEE"))
                .thenReturn(new ValidationCharte());

        assertThat(service.isCharteRequired(p)).isFalse();
        verify(validationCharteRepository).findByApersonneIdAndServiceId(1L, "LYCEE");
    }

    @Test
    @DisplayName("getCharteUrl : arrive sur un domaine → URL de la charte de ce domaine")
    void charteUrlForArrivalDomain() {
        urls.put("LYCEE", "https://charte/lycee");
        when(soffitHolder.getArrivalHost()).thenReturn("lycees.test.recia.dev");
        when(charteProperties.getDomains()).thenReturn(Map.of("lycees.test.recia.dev", "LYCEE"));
        APersonne p = personWithDomains("fio", "COLL-45", "lycees.test.recia.dev");
        when(aPersonneRepository.findByLogin("fio")).thenReturn(p);

        assertThat(service.getCharteUrl("fio")).isEqualTo("https://charte/lycee");
    }

    @Test
    @DisplayName("getCharteUrl : domaine d'arrivée non rattaché à la personne → URL par défaut")
    void charteUrlFallsBackToDefaultWhenHostNotInPersonDomains() {
        when(soffitHolder.getArrivalHost()).thenReturn("lycees.test.recia.dev");
        when(charteProperties.getDomains()).thenReturn(Map.of("lycees.test.recia.dev", "LYCEE"));
        APersonne p = personWithDomains("fio", "COLL-45", "www.chercan.fr");
        when(aPersonneRepository.findByLogin("fio")).thenReturn(p);

        assertThat(service.getCharteUrl("fio")).isEqualTo("https://charte/default");
    }

    // ── Domaine de charte (colonne dom du CSV) ──

    @Test
    @DisplayName("resolveCharteDomain : hôte d'arrivée rattaché à la personne → l'hôte lui-même")
    void charteDomainIsArrivalHost() {
        when(soffitHolder.getArrivalHost()).thenReturn("www.touraine-eschool.fr");
        APersonne p = personWithDomains("fio", "COLL-37", "touraine-eschool.fr");

        assertThat(service.resolveCharteDomain(p)).isEqualTo("www.touraine-eschool.fr");
    }

    @Test
    @DisplayName("resolveCharteDomain : hôte d'arrivée absent des domaines de la personne → 'default'")
    void charteDomainFallsBackToDefault() {
        when(soffitHolder.getArrivalHost()).thenReturn("lycees.test.recia.dev");
        APersonne p = personWithDomains("fio", "COLL-45", "www.chercan.fr");

        assertThat(service.resolveCharteDomain(p)).isEqualTo(CharteService.DEFAULT_CHARTE_DOMAIN);
    }

    @Test
    @DisplayName("resolveCharteDomain : aucun hôte d'arrivée (hors requête web) → 'default'")
    void charteDomainWithoutArrivalHost() {
        assertThat(service.resolveCharteDomain(mockPersonne("COLL-45")))
                .isEqualTo(CharteService.DEFAULT_CHARTE_DOMAIN);
    }

    @Test
    @DisplayName("getCharteVersionDateFor : version du domaine d'arrivée rattaché")
    void versionForPersonUsesArrivalDomain() {
        when(charteProperties.getVersions()).thenReturn(new HashMap<>(Map.of(
                "www.touraine-eschool.fr", "2024-05-01",
                "default", "2024-01-02")));
        when(soffitHolder.getArrivalHost()).thenReturn("www.touraine-eschool.fr");
        APersonne p = personWithDomains("fio", "COLL-37", "touraine-eschool.fr");

        assertThat(service.getCharteVersionDateFor(p))
                .isEqualTo(java.sql.Date.valueOf(java.time.LocalDate.of(2024, 5, 1)));
    }

    @Test
    @DisplayName("getCharteVersionDateFor : hors requête web → version 'default'")
    void versionForPersonWithoutArrivalHostUsesDefault() {
        when(charteProperties.getVersions()).thenReturn(new HashMap<>(Map.of(
                "www.touraine-eschool.fr", "2024-05-01",
                "default", "2024-01-02")));

        assertThat(service.getCharteVersionDateFor(mockPersonne("COLL-37")))
                .isEqualTo(java.sql.Date.valueOf(java.time.LocalDate.of(2024, 1, 2)));
    }
}
