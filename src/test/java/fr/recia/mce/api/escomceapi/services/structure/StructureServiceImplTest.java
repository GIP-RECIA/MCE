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
package fr.recia.mce.api.escomceapi.services.structure;

import fr.recia.mce.api.escomceapi.configuration.bean.DomaineProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalStructDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - StructureServiceImpl.isReseauRecia")
class StructureServiceImplTest {

    @Mock
    private IExternalStructDao externalStructDao;

    @Mock
    private CacheManager cacheManager;

    private StructureServiceImpl service;

    @BeforeEach
    void setUp() {
        DomaineProperties domaineProperties = new DomaineProperties();
        domaineProperties.setGestionRecia(List.of("lycees.netocentre.fr", "www.chercan.fr"));
        domaineProperties.setGestionInclude(List.of("0360718K"));
        domaineProperties.setGestionExclude(List.of("0370074E"));

        service = new StructureServiceImpl(domaineProperties);
        ReflectionTestUtils.setField(service, "externalStructDao", externalStructDao);
        ReflectionTestUtils.setField(service, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(service, "allStructures", new ArrayList<>());
    }

    private void givenUaiMapping(String uai, IExternalStructure structure) {
        @SuppressWarnings("unchecked")
        Map<String, IExternalStructure> map = (Map<String, IExternalStructure>) ReflectionTestUtils.getField(service, "uai2structure");
        Map<String, IExternalStructure> newMap = new HashMap<>();
        if (map != null)
            newMap.putAll(map);
        newMap.put(uai, structure);
        ReflectionTestUtils.setField(service, "uai2structure", newMap);
    }

    private IExternalStructure createStructure(String uai, String domaine) {
        IExternalStructure struct = mock(IExternalStructure.class);
        lenient().when(struct.getUai()).thenReturn(uai);
        lenient().when(struct.getDomaines()).thenReturn(new String[]{domaine});
        lenient().when(struct.getId()).thenReturn("SIREN_" + uai);
        lenient().when(struct.getDisplayName()).thenReturn("Struct_" + uai);
        return struct;
    }

    private PersonneDTO createPersonne(String uid, List<String> uais) {
        PersonneDTO p = mock(PersonneDTO.class);
        IExternalUser extUser = mock(IExternalUser.class);
        lenient().when(p.getUid()).thenReturn(uid);
        lenient().when(p.getExtUser()).thenReturn(extUser);
        lenient().when(extUser.getAttribute("ESCOUAI")).thenReturn(uais);
        return p;
    }

    @Nested
    @DisplayName("isReseauRecia(PersonneDTO)")
    class IsReseauReciaPersonne {

        @Test
        @DisplayName("Pas d'UAI → false")
        void noUai() {
            PersonneDTO p = createPersonne("test", null);
            assertThat(service.isReseauRecia(p)).isFalse();
        }

        @Test
        @DisplayName("UAI présente mais aucune structure trouvée → false")
        void noStructureFound() {
            PersonneDTO p = createPersonne("test", List.of("0450822X"));
            assertThat(service.isReseauRecia(p)).isFalse();
        }

        @Test
        @DisplayName("Domaine RECIA + UAI non exclue → true")
        void domaineReciaUaiNonExclue() {
            IExternalStructure struct = createStructure("0450822X", "lycees.netocentre.fr");
            givenUaiMapping("0450822X", struct);

            PersonneDTO p = createPersonne("testUid", List.of("0450822X"));
            assertThat(service.isReseauRecia(p)).isTrue();
        }

        @Test
        @DisplayName("Domaine RECIA + UAI exclue → false")
        void domaineReciaUaiExclue() {
            IExternalStructure struct = createStructure("0370074E", "lycees.netocentre.fr");
            givenUaiMapping("0370074E", struct);

            PersonneDTO p = createPersonne("testUid", List.of("0370074E"));
            assertThat(service.isReseauRecia(p)).isFalse();
        }

        @Test
        @DisplayName("Domaine NON RECIA + UAI dans include → true")
        void domaineNonReciaMaisInclus() {
            IExternalStructure struct = createStructure("0360718K", "e-college.indre.fr");
            givenUaiMapping("0360718K", struct);

            PersonneDTO p = createPersonne("testUid", List.of("0360718K"));
            assertThat(service.isReseauRecia(p)).isTrue();
        }

        @Test
        @DisplayName("Domaine NON RECIA + UAI pas dans include → false")
        void domaineNonReciaNonInclus() {
            IExternalStructure struct = createStructure("0280957N", "autre-domaine.fr");
            givenUaiMapping("0280957N", struct);

            PersonneDTO p = createPersonne("testUid", List.of("0280957N"));
            assertThat(service.isReseauRecia(p)).isFalse();
        }

        @Test
        @DisplayName("Plusieurs UAI : première exclue, deuxième incluse → true (edge case)")
        void multiUaiFirstExcludedSecondIncluded() {
            IExternalStructure structExclu = createStructure("0370074E", "lycees.netocentre.fr");
            IExternalStructure structInclus = createStructure("0360718K", "e-college.indre.fr");
            givenUaiMapping("0370074E", structExclu);
            givenUaiMapping("0360718K", structInclus);

            PersonneDTO p = createPersonne("testUid", List.of("0370074E", "0360718K"));
            assertThat(service.isReseauRecia(p)).isTrue();
        }

        @Test
        @DisplayName("Plusieurs UAI : toutes exclues → false")
        void multiUaiAllExcluded() {
            IExternalStructure struct1 = createStructure("0370074E", "lycees.netocentre.fr");
            givenUaiMapping("0370074E", struct1);

            PersonneDTO p = createPersonne("testUid", List.of("0370074E"));
            assertThat(service.isReseauRecia(p)).isFalse();
        }
    }

    @Nested
    @DisplayName("isReseauRecia(IExternalStructure)")
    class IsReseauReciaStructure {

        @Test
        @DisplayName("Domaine RECIA + UAI non exclue → true")
        void domaineReciaNonExclu() {
            IExternalStructure struct = createStructure("0450822X", "lycees.netocentre.fr");
            assertThat(service.isReseauRecia(struct)).isTrue();
        }

        @Test
        @DisplayName("Domaine RECIA + UAI exclue → false")
        void domaineReciaExclu() {
            IExternalStructure struct = createStructure("0370074E", "www.chercan.fr");
            assertThat(service.isReseauRecia(struct)).isFalse();
        }

        @Test
        @DisplayName("Domaine NON RECIA + UAI dans include → true")
        void domaineNonReciaInclus() {
            IExternalStructure struct = createStructure("0360718K", "autre-domaine.fr");
            assertThat(service.isReseauRecia(struct)).isTrue();
        }

        @Test
        @DisplayName("Domaine NON RECIA + UAI pas dans include → false")
        void domaineNonReciaNonInclus() {
            IExternalStructure struct = createStructure("9999999A", "autre-domaine.fr");
            assertThat(service.isReseauRecia(struct)).isFalse();
        }

        @Test
        @DisplayName("Structure sans UAI mais domaine RECIA → true")
        void structureSansUai() {
            IExternalStructure struct = mock(IExternalStructure.class);
            lenient().when(struct.getUai()).thenReturn(null);
            lenient().when(struct.getDomaines()).thenReturn(new String[]{"lycees.netocentre.fr"});
            assertThat(service.isReseauRecia(struct)).isTrue();
        }
    }
}
