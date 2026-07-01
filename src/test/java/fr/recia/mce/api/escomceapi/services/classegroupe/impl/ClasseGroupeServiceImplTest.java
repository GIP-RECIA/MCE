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
package fr.recia.mce.api.escomceapi.services.classegroupe.impl;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - ClasseGroupeServiceImpl")
class ClasseGroupeServiceImplTest {

    @Mock
    private PersonneService personneService;
    @Mock
    private ExternalUserHelper extUserHelper;
    @Mock
    private IStructureService structureService;
    @Mock
    private MCEProperties mceProperties;
    @Mock
    private IExternalUser person;
    @Mock
    private IExternalStructure externalStructure;

    private ClasseGroupeServiceImpl service;

    @BeforeEach
    void setUp() {
        ServiceProperties sp = new ServiceProperties();
        ServiceProperties.ClasseCalculatorProperties classeProps = sp.getClasseProperties();
        classeProps.setLdapAttributsClasse("ENTEleveClasses ENTAuxEnsClassesMatieres");
        classeProps.setRegexSirenAndClasse("ENTStructureSIREN=(\\w+),[^$]+\\$([^$]+)(\\$([^$]+))?");
        classeProps.setGroupSiren(1);
        classeProps.setGroupClasse(2);
        classeProps.setGroupMatiere(4);

        ServiceProperties.GrpPedagoCalculator grpProps = sp.getGrpPedagoProperties();
        grpProps.setLdapAttributsClasse("ENTEleveGroupes ENTAuxEnsGroupesMatieres ENTAuxEnsGroupes");
        grpProps.setRegexSirenAndClasse("ENTStructureSIREN=(\\w+),[^$]+\\$([^$]+)(\\$([^$]+))?");
        grpProps.setGroupSiren(1);
        grpProps.setGroupClasse(2);
        grpProps.setGroupMatiere(4);

        when(mceProperties.getService()).thenReturn(sp);

        lenient().when(extUserHelper.getUserEleveEnseignement()).thenReturn("ENTEleveEnseignements");
        lenient().when(extUserHelper.getUserCodeMatiereEnseignement()).thenReturn("ENTAuxEnsMatiereEnseignEtab");

        lenient().when(externalStructure.getName()).thenReturn("Etablissement Test");

        lenient().when(person.getAttribute("ENTAuxEnsMatiereEnseignEtab")).thenReturn(null);
        lenient().when(person.getAttribute("ENTEleveEnseignements")).thenReturn(null);

        service = new ClasseGroupeServiceImpl(mceProperties);
        ReflectionTestUtils.setField(service, "personneService", personneService);
        ReflectionTestUtils.setField(service, "extUserHelper", extUserHelper);
        ReflectionTestUtils.setField(service, "structureService", structureService);
    }

    @Test
    @DisplayName("calculCG(null) → null")
    void nullPerson() {
        assertThat(service.calculCG(null)).isNull();
    }

    @Nested
    @DisplayName("Avec profil non-ENS (Élève)")
    class NonEnsProfil {

        @BeforeEach
        void setUp() {
            when(person.getAttribute("ENTPersonProfils"))
                    .thenReturn(List.of("Eleve"));
            when(person.getId()).thenReturn("F20102xc");
        }

        @Test
        @DisplayName("Aucun attribut LDAP → sectionEleve et sectionProf vides")
        void noLdapAttributes() {
            when(person.getAttribute("ENTEleveClasses")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsClassesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTEleveGroupes")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupes")).thenReturn(null);

            ClasseGroupeDTO result = service.calculCG(person);

            assertThat(result).isNotNull();
            assertThat(result.getSectionEleve()).isNotNull();
            assertThat(result.getSectionEleve().getEtabs()).isEmpty();
            assertThat(result.getSectionEleve().getEnseignementSuivis()).isNotNull();
        }

        @Test
        @DisplayName("Attributs de classe présents → sectionEleve remplie")
        void withClassAttributes() {
            when(person.getAttribute("ENTEleveClasses"))
                    .thenReturn(List.of("ENTStructureSIREN=0370074E,ou=classes$2NDE2"));
            when(person.getAttribute("ENTAuxEnsClassesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTEleveGroupes")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupes")).thenReturn(null);
            when(person.getAttribute("ENTEleveEnseignements")).thenReturn(List.of("MATHS", "FRANCAIS"));
            when(structureService.findStructureBySiren("0370074E")).thenReturn(externalStructure);

            ClasseGroupeDTO result = service.calculCG(person);

            assertThat(result).isNotNull();
            assertThat(result.getSectionEleve().getEtabs()).isNotEmpty();
            assertThat(result.getSectionEleve().getEnseignementSuivis())
                    .contains("MATHS", "FRANCAIS");
        }

        @Test
        @DisplayName("Attributs de groupe présents → sectionEleve avec groupes")
        void withGroupAttributes() {
            when(person.getAttribute("ENTEleveClasses")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsClassesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTEleveGroupes"))
                    .thenReturn(List.of("ENTStructureSIREN=0370074E,ou=groupes$GroupeA"));
            when(person.getAttribute("ENTAuxEnsGroupesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupes")).thenReturn(null);
            when(person.getAttribute("ENTEleveEnseignements")).thenReturn(null);
            when(structureService.findStructureBySiren("0370074E")).thenReturn(externalStructure);

            ClasseGroupeDTO result = service.calculCG(person);

            assertThat(result).isNotNull();
            assertThat(result.getSectionEleve().getEtabs()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Avec profil ENS (Enseignant)")
    class EnsProfil {

        @BeforeEach
        void setUp() {
            when(person.getAttribute("ENTPersonProfils"))
                    .thenReturn(List.of("ENS"));
            when(person.getId()).thenReturn("teacher01");
            when(person.getAttribute("ENTEleveEnseignements")).thenReturn(null);
        }

        @Test
        @DisplayName("Attributs ENS avec matière → sectionProf remplie")
        void withMatiere() {
            when(person.getAttribute("ENTAuxEnsClasses"))
                    .thenReturn(List.of("ENTStructureSIREN=0370074E,ou=classes$2NDE2$MATHS01"));
            when(person.getAttribute("ENTAuxEnsClassesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupes")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsMatiereEnseignEtab"))
                    .thenReturn(List.of("ENTStructureSIREN=0370074E,ou=matieres$MATHEMATIQUES"));
            when(structureService.findStructureBySiren("0370074E")).thenReturn(externalStructure);

            ClasseGroupeDTO result = service.calculCG(person);

            assertThat(result).isNotNull();
            assertThat(result.getSectionEleve()).isNotNull();
            assertThat(result.getSectionEleve().getEtabs()).isEmpty();

            verify(structureService, atLeastOnce()).findStructureBySiren(anyString());
        }

        @Test
        @DisplayName("Aucun attribut ENS → sections vides")
        void noAttributes() {
            when(person.getAttribute("ENTAuxEnsClasses")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsClassesMatieres")).thenReturn(null);
            when(person.getAttribute("ENTAuxEnsGroupes")).thenReturn(null);
            when(person.getAttribute("ENTEleveEnseignements")).thenReturn(null);

            ClasseGroupeDTO result = service.calculCG(person);

            assertThat(result).isNotNull();
        }
    }
}
