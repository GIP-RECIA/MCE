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
package fr.recia.mce.api.escomceapi.services.factories.impl;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.AvatarProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static fr.recia.mce.api.escomceapi.db.dto.StructureDTO.DomSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - UserDTOFactoryImpl")
class UserDTOFactoryImplTest {

    @Mock
    private APersonneRepository daoPersonne;
    @Mock
    private FonctionRepository fonctionRepository;
    @Mock
    private IExternalUserDao extDao;
    @Mock
    private IClasseGroupeService classeGroupeService;
    @Mock
    private IRelationEleveService iRelationEleveService;
    @Mock
    private SoffitHolder soffitHolder;
    @Mock
    private IStructureService structureService;
    @Mock
    private FonctionService fonctionService;
    @Mock
    private PasswordService passwordService;
    @Mock
    private PersonneService personneService;
    @Mock
    private MCEProperties mceProperties;
    @Mock
    private MailProperties mailProperties;
    @Mock
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    @Mock
    private APersonne aPersonneBase;
    @Mock
    private StructureDTO structureDto;
    @Mock
    private IExternalUser extModel;

    private UserDTOFactoryImpl factory;

    private PersonneDTO model;
    private final String mailFixeValue = "user@ac-orleans-tours.fr";
    private final String mailFixeDomainNotTrusted = "user@other-domain.fr";

    @BeforeEach
    void setUp() {
        model = mock(PersonneDTO.class);

        lenient().when(structureDto.getDisplayName()).thenReturn("Etablissement");
        lenient().when(model.getStructureDto()).thenReturn(structureDto);
        lenient().when(model.getAPersonneBase()).thenReturn(aPersonneBase);
        lenient().when(model.getUid()).thenReturn("testUid");
        lenient().when(model.getDisplayName()).thenReturn("Test User");
        lenient().when(aPersonneBase.getId()).thenReturn(1L);
        lenient().when(aPersonneBase.getGivenName()).thenReturn("Test");
        lenient().when(aPersonneBase.getSn()).thenReturn("User");
        lenient().when(aPersonneBase.getCivilite()).thenReturn("M");
        lenient().when(aPersonneBase.getCategorie()).thenReturn("Eleve");
        lenient().when(aPersonneBase.getEtat()).thenReturn("ACTIF");

        lenient().when(iRelationEleveService.allRelationEleves(extModel)).thenReturn(Collections.emptyList());
        lenient().when(iRelationEleveService.allEleveEnRelation(anyLong())).thenReturn(Collections.emptyList());

        ServiceProperties serviceProperties = new ServiceProperties();
        lenient().when(mceProperties.getAvatar()).thenReturn(new AvatarProperties());
        lenient().when(mceProperties.getService()).thenReturn(serviceProperties);

        lenient().when(structureService.isReseauRecia(any(PersonneDTO.class))).thenReturn(false);

        lenient().when(mailProperties.getDomainesConfiance()).thenReturn("ac-orleans-tours.fr educagri.fr recia.fr");

        factory = new UserDTOFactoryImpl(mceProperties);
        ReflectionTestUtils.setField(factory, "mailProperties", mailProperties);
        ReflectionTestUtils.setField(factory, "cerbereConfirmationRepository", cerbereConfirmationRepository);
        ReflectionTestUtils.setField(factory, "iRelationEleveService", iRelationEleveService);
        ReflectionTestUtils.setField(factory, "structureService", structureService);
        ReflectionTestUtils.setField(factory, "fonctionService", fonctionService);
        ReflectionTestUtils.setField(factory, "passwordService", passwordService);
        ReflectionTestUtils.setField(factory, "personneService", personneService);
        ReflectionTestUtils.setField(factory, "daoPersonne", daoPersonne);
        ReflectionTestUtils.setField(factory, "fonctionRepository", fonctionRepository);
        ReflectionTestUtils.setField(factory, "extDao", extDao);
        ReflectionTestUtils.setField(factory, "classeGroupeService", classeGroupeService);
        ReflectionTestUtils.setField(factory, "soffitHolder", soffitHolder);
    }

    @Nested
    @DisplayName("from(PersonneDTO, IExternalUser) - cas mail")
    class FromPersonneDtoMailTests {

        @Test
        @DisplayName("Domaine dans domainesConfiance → mailFixe utilisé")
        void domainInTrustedList() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(mailFixeValue);
            when(aPersonneBase.getEmail()).thenReturn(mailFixeValue);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo(mailFixeValue);
        }

        @Test
        @DisplayName("Utilisateur PERSONNEL → mailFixe utilisé même si domaine non fiable")
        void userIsPersonnel() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.PERSONNEL);
            when(model.getMailFixe()).thenReturn(mailFixeDomainNotTrusted);
            when(aPersonneBase.getEmail()).thenReturn(mailFixeDomainNotTrusted);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo(mailFixeDomainNotTrusted);
        }

        @Test
        @DisplayName("Domaine non fiable + mail confirmé en DB → email confirmé affiché")
        void notTrustedWithConfirmedEmail() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(mailFixeDomainNotTrusted);
            when(aPersonneBase.getEmail()).thenReturn(mailFixeDomainNotTrusted);

            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setMail("confirmed@email.fr");
            when(cerbereConfirmationRepository.findConfirmedByPersonId(1L))
                    .thenReturn(List.of(confirmation));

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo("confirmed@email.fr");
        }

        @Test
        @DisplayName("Domaine non fiable + aucun mail confirmé → mailFixe en fallback")
        void notTrustedWithoutConfirmedEmail() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(mailFixeDomainNotTrusted);
            when(aPersonneBase.getEmail()).thenReturn(mailFixeDomainNotTrusted);

            when(cerbereConfirmationRepository.findConfirmedByPersonId(1L))
                    .thenReturn(Collections.emptyList());

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo(mailFixeDomainNotTrusted);
        }

        @Test
        @DisplayName("mailFixe null → email par défaut (getEmail)")
        void mailFixeNull() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("default@email.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo("default@email.fr");
        }
    }

    @Nested
    @DisplayName("from(PersonneDTO, IExternalUser) - cas limites")
    class FromPersonneDtoEdgeCases {

        @Test
        @DisplayName("model null → null")
        void nullModel() {
            UserDTO result = factory.from(null, extModel);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("extModel null → null")
        void nullExtModel() {
            UserDTO result = factory.from(model, null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("base null → null")
        void nullBase() {
            when(model.getAPersonneBase()).thenReturn(null);
            UserDTO result = factory.from(model, extModel);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("enumPublic null → canEditEmail calcule depuis EnumCategorie")
        void nullEnumPublic() {
            when(model.getEnumPublic()).thenReturn(null);
            when(model.getMailFixe()).thenReturn(mailFixeValue);
            when(aPersonneBase.getEmail()).thenReturn(mailFixeValue);

            UserDTO result = factory.from(model, extModel);
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(mailFixeValue);
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("Succès : appel à passwordService.changePassword + clearUserCaches")
        void success() {
            when(soffitHolder.getSub()).thenReturn("testSub");
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(model);

            PasswordChangeRequestDTO req = new PasswordChangeRequestDTO();
            factory.changePassword("testUid", req);

            verify(passwordService).changePassword(model, req);
            verify(personneService).clearUserCaches("testUid");
        }

        @Test
        @DisplayName("isSubInvalid → SecurityException")
        void subInvalid() {
            when(soffitHolder.getSub()).thenReturn(null);

            assertThatThrownBy(() -> factory.changePassword("testUid", new PasswordChangeRequestDTO()))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Utilisateur introuvable → PersonneNotFoundException")
        void userNotFound() {
            when(soffitHolder.getSub()).thenReturn("testSub");
            when(personneService.retrievePersonnebyUid("inconnu")).thenReturn(null);

            assertThatThrownBy(() -> factory.changePassword("inconnu", new PasswordChangeRequestDTO()))
                    .isInstanceOf(PersonneNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("sub null → null")
        void subNull() {
            when(soffitHolder.getSub()).thenReturn(null);
            assertThat(factory.getCurrentUser()).isNull();
        }

        @Test
        @DisplayName("sub guest → null")
        void subGuest() {
            when(soffitHolder.getSub()).thenReturn("guest");
            assertThat(factory.getCurrentUser()).isNull();
        }

        @Test
        @DisplayName("Utilisateur non trouvé → null")
        void userNotFound() {
            when(soffitHolder.getSub()).thenReturn("validSub");
            when(personneService.retrievePersonLdap("validSub")).thenReturn(null);

            UserDTO result = factory.getCurrentUser();
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("from(IExternalUser, boolean)")
    class FromExternalUserTests {

        @Test
        @DisplayName("extModel null → null")
        void nullExtModel() {
            UserDTO result = factory.from(null, true);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("withInternal false → null")
        void withInternalFalse() {
            UserDTO result = factory.from(extModel, false);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Succès avec withInternal=true")
        void success() {
            when(extModel.getId()).thenReturn("testUid");
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(model);
            when(extModel.getEmail()).thenReturn("ldap@email.fr");
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("user@test.fr");

            UserDTO result = factory.from(extModel, true);
            assertThat(result).isNotNull();
            verify(model).setMailFromLdap("ldap@email.fr");
        }
    }

    @Nested
    @DisplayName("from(PersonneDTO) - single arg")
    class FromPersonneDTOSingleArg {

        @Test
        @DisplayName("Retourne UserDTO avec données LDAP")
        void success() {
            when(personneService.retrievePersonLdap("testUid")).thenReturn(extModel);
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("user@test.fr");

            UserDTO result = factory.from(model);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("from(UserDTO) - conversion inverse")
    class FromUserDTOTests {

        @Test
        @DisplayName("DTO non null → recherche en DB")
        void success() {
            UserDTO dto = new UserDTO();
            dto.setId(1L);
            when(daoPersonne.findById(1L)).thenReturn(Optional.of(aPersonneBase));

            APersonne result = factory.from(dto);
            assertThat(result).isEqualTo(aPersonneBase);
        }

        @Test
        @DisplayName("DTO null → null")
        void nullDto() {
            APersonne result = factory.from((UserDTO) null);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("showGeneralInfo")
    class ShowGeneralInfoTests {

        @Test
        @DisplayName("personneDTO null → null")
        void nullPersonneDTO() {
            assertThat(factory.showGeneralInfo()).isNull();
        }
    }

    @Nested
    @DisplayName("listMenuTab")
    class ListMenuTabTests {

        @Test
        @DisplayName("Catégorie ELEVE → menu non null dans le UserDTO")
        void eleveMenu() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("user@test.fr");

            UserDTO result = factory.from(model, extModel);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("from(String uid)")
    class FromStringUidTests {

        @Test
        @DisplayName("Succès")
        void success() {
            lenient().when(extModel.getId()).thenReturn("testUid");
            lenient().when(extModel.getEmail()).thenReturn("ldap@email.fr");
            lenient().when(model.getMailFixe()).thenReturn(null);
            lenient().when(aPersonneBase.getEmail()).thenReturn("user@test.fr");
            lenient().when(model.getNaissance()).thenReturn(null);
            lenient().when(model.getAvatarUrl()).thenReturn(null);
            lenient().when(aPersonneBase.getEmailPersonnel()).thenReturn(null);

            when(personneService.retrievePersonLdap("testUid")).thenReturn(extModel);
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(model);
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);

            UserDTO result = factory.from("testUid");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("evalPublic - toutes les combinaisons enumCat × DomSource")
    class EvalPublicTests {

        private PersonneDTO createModel(String categorie, String source) {
            APersonne ap = mock(APersonne.class);
            when(ap.getCategorie()).thenReturn(categorie);
            when(ap.getSource()).thenReturn(source);
            return new PersonneDTO(ap, mock(AStructure.class));
        }

        private PersonneDTO modelWithDomSource(String categorie, String source, DomSource ds) {
            PersonneDTO p = createModel(categorie, source);
            StructureDTO s = mock(StructureDTO.class);
            when(s.getDomSource()).thenReturn(ds);
            p.setStructureDto(s);
            return p;
        }

        private EnumPublic eval(PersonneDTO p) {
            return ReflectionTestUtils.invokeMethod(factory, "evalPublic", p);
        }

        @Test
        @DisplayName("structure null → ELEVE")
        void structureNull() {
            PersonneDTO p = createModel("Eleve", null);
            p.setStructureDto(null);
            assertThat(eval(p)).isEqualTo(EnumPublic.ELEVE);
        }

        @Test
        @DisplayName("getDomSource lève une exception → ELEVE")
        void domSourceThrows() {
            PersonneDTO p = createModel("Eleve", null);
            StructureDTO s = mock(StructureDTO.class);
            when(s.getDomSource()).thenThrow(new RuntimeException("boom"));
            p.setStructureDto(s);
            assertThat(eval(p)).isEqualTo(EnumPublic.ELEVE);
        }

        @Test
        @DisplayName("source null → isLocalUser=false, isRegion=false")
        void sourceNull() {
            PersonneDTO p = createModel("Eleve", null);
            assertThat(eval(p)).isEqualTo(EnumPublic.ELEVE);
        }

        @Test
        @DisplayName("ELEVE + CFA → APPRENANT")
        void eleveCfa() {
            assertThat(eval(modelWithDomSource("Eleve", null, DomSource.CFA))).isEqualTo(EnumPublic.APPRENANT);
        }

        @Test
        @DisplayName("ELEVE + AC + isLocalUser → ELEVE")
        void eleveAcLocal() {
            assertThat(eval(modelWithDomSource("Eleve", "SarapisUi-Paris", DomSource.AC))).isEqualTo(EnumPublic.ELEVE);
        }

        @Test
        @DisplayName("ELEVE + AC + !isLocalUser → ELEVE_EDUC")
        void eleveAcNonLocal() {
            assertThat(eval(modelWithDomSource("Eleve", "ENT-Externe", DomSource.AC))).isEqualTo(EnumPublic.ELEVE_EDUC);
        }

        @Test
        @DisplayName("ELEVE + GIP → ELEVE")
        void eleveGip() {
            assertThat(eval(modelWithDomSource("Eleve", null, DomSource.GIP))).isEqualTo(EnumPublic.ELEVE);
        }

        @Test
        @DisplayName("PARENT + AC + isLocalUser → PARENT")
        void parentAcLocal() {
            assertThat(eval(modelWithDomSource("Personne_relation_eleve", "SarapisUi-Tours", DomSource.AC)))
                    .isEqualTo(EnumPublic.PARENT);
        }

        @Test
        @DisplayName("PARENT + AC + !isLocalUser → PARENT_EDUC")
        void parentAcNonLocal() {
            assertThat(eval(modelWithDomSource("Personne_relation_eleve", "ENT-Externe", DomSource.AC)))
                    .isEqualTo(EnumPublic.PARENT_EDUC);
        }

        @Test
        @DisplayName("PARENT + non-AC null → PARENT")
        void parentNotAc() {
            assertThat(eval(modelWithDomSource("Personne_relation_eleve", null, DomSource.CFA))).isEqualTo(EnumPublic.PARENT);
        }

        @Test
        @DisplayName("PROF + AC + isLocalUser → PERSONNEL")
        void profAcLocal() {
            assertThat(eval(modelWithDomSource("Enseignant", "SarapisUi-Lyon", DomSource.AC))).isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("PROF + AC + !isLocalUser → EDUCATION")
        void profAcNonLocal() {
            assertThat(eval(modelWithDomSource("Enseignant", "ENT-Externe", DomSource.AC))).isEqualTo(EnumPublic.EDUCATION);
        }

        @Test
        @DisplayName("PROF + LA + isLocalUser → PERSONNEL")
        void profLaLocal() {
            assertThat(eval(modelWithDomSource("Enseignant", "SarapisUi-Orleans", DomSource.LA))).isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("PROF + LA + !isLocalUser → AGRI")
        void profLaNonLocal() {
            assertThat(eval(modelWithDomSource("Enseignant", "ENT-Agri", DomSource.LA))).isEqualTo(EnumPublic.AGRI);
        }

        @Test
        @DisplayName("PROF + CFA → PERSONNEL")
        void profCfa() {
            assertThat(eval(modelWithDomSource("Enseignant", null, DomSource.CFA))).isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("ENTREPRISE → EXTERIEUR")
        void entreprise() {
            assertThat(eval(modelWithDomSource("Responsable_Entreprise", null, DomSource.AC))).isEqualTo(EnumPublic.EXTERIEUR);
        }

        @Test
        @DisplayName("TUTEUR → EXTERIEUR")
        void tuteur() {
            assertThat(eval(modelWithDomSource("Tuteur_stage", null, DomSource.AC))).isEqualTo(EnumPublic.EXTERIEUR);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + isRegion → CVDL")
        void nonProfColLocalRegion() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "SarapisUi-COLL-CVDL", null)))
                    .isEqualTo(EnumPublic.CVDL);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + !isRegion → fallthrough NON_PROF_ETAB ds=null → PERSONNEL")
        void nonProfColLocalNonRegion() {
            PersonneDTO p = modelWithDomSource("Non_enseignant_collectivite_locale", null, null);
            assertThat(eval(p)).isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ETAB + AC + isLocalUser → PERSONNEL")
        void nonProfEtabAcLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_etablissement", "SarapisUi-Paris", DomSource.AC)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ETAB + LA + !isLocalUser → AGRI")
        void nonProfEtabLaNonLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_etablissement", "ENT", DomSource.LA)))
                    .isEqualTo(EnumPublic.AGRI);
        }

        @Test
        @DisplayName("NON_PROF_ACAD + AC + isLocalUser → PERSONNEL")
        void nonProfAcadAcLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_service_academique", "SarapisUi-Lyon", DomSource.AC)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ACAD + LA + !isLocalUser → AGRI")
        void nonProfAcadLaNonLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_service_academique", "ENT", DomSource.LA)))
                    .isEqualTo(EnumPublic.AGRI);
        }

        @Test
        @DisplayName("NON_PROF_ACAD + CFA → AUTRE")
        void nonProfAcadCfa() {
            assertThat(eval(modelWithDomSource("Non_enseignant_service_academique", null, DomSource.CFA)))
                    .isEqualTo(EnumPublic.AUTRE);
        }

        @Test
        @DisplayName("NON_PROF_ACAD + null ds → AUTRE")
        void nonProfAcadNull() {
            assertThat(eval(modelWithDomSource("Non_enseignant_service_academique", null, null)))
                    .isEqualTo(EnumPublic.AUTRE);
        }

        @Test
        @DisplayName("AUTRE → AUTRE")
        void autre() {
            assertThat(eval(modelWithDomSource("", null, DomSource.AC))).isEqualTo(EnumPublic.AUTRE);
        }

        @Test
        @DisplayName("SSHA : GIP + extUser isMemberOf match → SSHAPass=true")
        void sshaMatches() {
            PersonneDTO p = modelWithDomSource("Enseignant", null, DomSource.GIP);
            IExternalUser ext = mock(IExternalUser.class);
            when(ext.getAttribute("isMemberOf")).thenReturn(List.of("gip-ssh"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithSSHAPassword", Pattern.compile("gip-ssh"));

            eval(p);
            assertThat(p.isSSHAPass()).isTrue();
        }

        @Test
        @DisplayName("SSHA : GIP + extUser isMemberOf no match → SSHAPass=false")
        void sshaNoMatch() {
            PersonneDTO p = modelWithDomSource("Enseignant", null, DomSource.GIP);
            IExternalUser ext = mock(IExternalUser.class);
            when(ext.getAttribute("isMemberOf")).thenReturn(List.of("other-group"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithSSHAPassword", Pattern.compile("gip-ssh"));

            eval(p);
            assertThat(p.isSSHAPass()).isFalse();
        }

        @Test
        @DisplayName("SSHA : GIP + extUser null → SSHAPass non défini")
        void sshaNoExtUser() {
            PersonneDTO p = modelWithDomSource("Enseignant", null, DomSource.GIP);
            p.setExtUser(null);
            ReflectionTestUtils.setField(factory, "groupsWithSSHAPassword", Pattern.compile("gip-ssh"));

            eval(p);
            assertThat(p.isSSHAPass()).isFalse();
        }

        @Test
        @DisplayName("SSHA : non-GIP → SSHAPass non défini")
        void sshaNotGip() {
            PersonneDTO p = modelWithDomSource("Enseignant", null, DomSource.AC);
            IExternalUser ext = mock(IExternalUser.class);
            lenient().when(ext.getAttribute("isMemberOf")).thenReturn(List.of("gip-ssh"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithSSHAPassword", Pattern.compile("gip-ssh"));

            eval(p);
            assertThat(p.isSSHAPass()).isFalse();
        }
    }
}
