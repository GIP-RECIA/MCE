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
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private ExternalUserHelper extUserHelper;
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
        MailProperties mailProperties = new MailProperties();
        mailProperties.setAcMailPattern("[^@]+@ac-orleans-tours.fr");

        lenient().when(structureService.isReseauRecia(any(PersonneDTO.class))).thenReturn(false);

        lenient().when(cerbereConfirmationRepository.findConfirmedByPersonId(anyLong()))
                .thenReturn(Collections.emptyList());

        factory = new UserDTOFactoryImpl(mceProperties);
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
        ReflectionTestUtils.setField(factory, "extUserHelper", extUserHelper);
        ReflectionTestUtils.setField(factory, "mailProperties", mailProperties);
    }

    private UserDTO buildUserDto(EnumPublic pub, String mailFixe) {
        when(model.getEnumPublic()).thenReturn(pub);
        when(model.getMailFixe()).thenReturn(mailFixe);
        when(aPersonneBase.getEmail()).thenReturn(mailFixe);
        return factory.from(model, extModel);
    }

    static Stream<Arguments> mdpMatrixWithMailFixe() {
        return Stream.of(
                Arguments.of(EnumPublic.EDUCATION, "user@ac-orleans-tours.fr", false),
                Arguments.of(EnumPublic.EDUCATION, "user@other.fr", false),
                Arguments.of(EnumPublic.AGRI, "user@educagri.fr", false),
                Arguments.of(EnumPublic.CVDL, "user@region.fr", false),
                Arguments.of(EnumPublic.ELEVE_EDUC, "eleve@ac-orleans-tours.fr", false),
                Arguments.of(EnumPublic.PARENT_EDUC, "parent@ac-orleans-tours.fr", false),
                Arguments.of(EnumPublic.PERSONNEL, "user@ac-orleans-tours.fr", true),
                Arguments.of(EnumPublic.PARENT, "parent@test.fr", true),
                Arguments.of(EnumPublic.ELEVE, "eleve@test.fr", true),
                Arguments.of(EnumPublic.APPRENANT, "apprenant@cfa.fr", true),
                Arguments.of(EnumPublic.EXTERIEUR, "externe@test.fr", true),
                Arguments.of(EnumPublic.AUTRE, "autre@test.fr", true));
    }

    static Stream<Arguments> mdpMatrixWithoutMailFixe() {
        return Stream.of(
                Arguments.of(EnumPublic.EDUCATION, false),
                Arguments.of(EnumPublic.AGRI, false),
                Arguments.of(EnumPublic.PERSONNEL, true),
                Arguments.of(EnumPublic.ELEVE, true));
    }

    static Stream<Arguments> blockedProfilesForPasswordChange() {
        return Stream.of(
                Arguments.of(EnumPublic.EDUCATION, "user@ac-orleans-tours.fr"),
                Arguments.of(EnumPublic.EDUCATION, "user@other.fr"),
                Arguments.of(EnumPublic.AGRI, "user@educagri.fr"),
                Arguments.of(EnumPublic.CVDL, "user@region.fr"),
                Arguments.of(EnumPublic.ELEVE_EDUC, "eleve@test.fr"),
                Arguments.of(EnumPublic.PARENT_EDUC, "parent@test.fr"));
    }

    static Stream<Arguments> allowedProfilesForPasswordChange() {
        return Stream.of(
                Arguments.of(EnumPublic.PERSONNEL, "user@ac-orleans-tours.fr"),
                Arguments.of(EnumPublic.PARENT, "parent@test.fr"),
                Arguments.of(EnumPublic.ELEVE, null),
                Arguments.of(EnumPublic.APPRENANT, "apprenant@cfa.fr"),
                Arguments.of(EnumPublic.EXTERIEUR, "externe@test.fr"),
                Arguments.of(EnumPublic.AUTRE, "autre@test.fr"));
    }

    @Nested
    @DisplayName("Gestion mot de passe (mdp) - matrice EnumPublic")
    class PasswordMdpMatrixTests {

        @ParameterizedTest(name = "{0} + mailFixe={1} → mdp={2}")
        @MethodSource("fr.recia.mce.api.escomceapi.services.factories.impl.UserDTOFactoryImplTest#mdpMatrixWithMailFixe")
        @DisplayName("mdp suit la spec (isConnectOk + exception EDUCATION ac-orleans-tours.fr)")
        void mdpFollowsSpec(EnumPublic pub, String mailFixe, boolean expectedMdp) {
            UserDTO result = buildUserDto(pub, mailFixe);

            assertThat(result.getMdp())
                    .as("EnumPublic.%s avec mailFixe=%s", pub, mailFixe)
                    .isEqualTo(expectedMdp);
        }

        @ParameterizedTest(name = "{0} sans mailFixe → mdp={1}")
        @MethodSource("fr.recia.mce.api.escomceapi.services.factories.impl.UserDTOFactoryImplTest#mdpMatrixWithoutMailFixe")
        @DisplayName("mdp sans mailFixe")
        void mdpWithoutMailFixe(EnumPublic pub, boolean expectedMdp) {
            UserDTO result = buildUserDto(pub, null);

            assertThat(result.getMdp())
                    .as("EnumPublic.%s sans mailFixe", pub)
                    .isEqualTo(expectedMdp);
        }

        @Test
        @DisplayName("Régression: AGRI avec domaine non ac-orleans-tours.fr reste bloqué")
        void regressionAgriMustStayBlockedRegardlessOfMailDomain() {
            UserDTO result = buildUserDto(EnumPublic.AGRI, "user@other-domain.fr");

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("Régression: mdp ne doit pas ignorer isConnectOk()")
        void regressionMdpMustUseIsConnectOkNotMailDomainOnly() {
            assertThat(buildUserDto(EnumPublic.CVDL, "user@other.fr").getMdp()).isFalse();
            assertThat(buildUserDto(EnumPublic.PARENT_EDUC, "parent@other.fr").getMdp()).isFalse();
            assertThat(buildUserDto(EnumPublic.PERSONNEL, "user@other.fr").getMdp()).isTrue();
        }

        @Test
        @DisplayName("computePassEditable(null) → false")
        void computePassEditableNullProfile() {
            Boolean result = ReflectionTestUtils.invokeMethod(factory, "computePassEditable", model, null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("EDUCATION + ac-orleans-tours.fr bloque aussi les liens EduConnect")
        void educationAcMailBlocksEduConnectLinks() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("user@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@ac-orleans-tours.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getUserPublic()).isEmpty();
        }

        @Test
        @DisplayName("ELEVE_EDUC sans mailFixe → liens EduConnect actifs")
        void eleveEducHasEduConnectLinks() {
            ServiceProperties sp = factory.getServiceProperties();
            sp.getCustomParams().setLienEdu("https://educonnect");
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE_EDUC);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getUserPublic()).contains("https://educonnect");
        }

        @Test
        @DisplayName("EDUCATION + ac-orleans-tours.fr + ntPass=true → mdp=true (ntPass débloque)")
        void educationAcMailWithNtPassIsMdpTrue() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("user@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@ac-orleans-tours.fr");
            when(model.isNtPass()).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("EDUCATION + ac-orleans-tours.fr + ntPass=false → mdp=false")
        void educationAcMailWithoutNtPassIsMdpFalse() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("user@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@ac-orleans-tours.fr");
            when(model.isNtPass()).thenReturn(false);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("CVDL + ntPass=true → mdp=true (ntPass débloque profil bloqué)")
        void cvdlWithNtPassIsMdpTrue() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.CVDL);
            when(model.getMailFixe()).thenReturn("user@region.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@region.fr");
            when(model.isNtPass()).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("CVDL + ntPass=false → mdp=false")
        void cvdlWithoutNtPassIsMdpFalse() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.CVDL);
            when(model.getMailFixe()).thenReturn("user@region.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@region.fr");
            when(model.isNtPass()).thenReturn(false);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("EDUCATION + autre domaine + ntPass=true → mdp=true")
        void educationOtherDomainWithNtPass() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("user@other-domain.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@other-domain.fr");
            when(model.isNtPass()).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("AGRI + ntPass=true → mdp=true (ntPass débloque)")
        void agriWithNtPass() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.AGRI);
            lenient().when(model.getMailFixe()).thenReturn("user@educagri.fr");
            lenient().when(aPersonneBase.getEmail()).thenReturn("user@educagri.fr");
            when(model.isNtPass()).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("ELEVE_EDUC + ntPass=true → mdp=true (ntPass débloque)")
        void eleveEducWithNtPass() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE_EDUC);
            lenient().when(model.getMailFixe()).thenReturn("eleve@ac-orleans-tours.fr");
            lenient().when(aPersonneBase.getEmail()).thenReturn("eleve@ac-orleans-tours.fr");
            when(model.isNtPass()).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("PARENT_EDUC + ntPass=true → mdp=true (ntPass débloque)")
        void parentEducWithNtPass() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.PARENT_EDUC);
            lenient().when(model.getMailFixe()).thenReturn("parent@ac-orleans-tours.fr");
            lenient().when(aPersonneBase.getEmail()).thenReturn("parent@ac-orleans-tours.fr");
            when(model.isNtPass()).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }
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
        @DisplayName("Cerbere confirmé n'affecte pas le mail fixe, seulement emailPersonnel")
        void notTrustedWithConfirmedEmail() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(mailFixeDomainNotTrusted);
            when(model.getMailFromLdap()).thenReturn("ldap@email.fr");

            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setMail("confirmed@email.fr");
            when(cerbereConfirmationRepository.findConfirmedByPersonId(1L))
                    .thenReturn(List.of(confirmation));

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo("ldap@email.fr");
            assertThat(result.getEmailPersonnel()).isEqualTo("confirmed@email.fr");
        }

        @Test
        @DisplayName("Mail fixe : LDAP > apersonne.email, cerbere confirmé pour emailPersonnel uniquement")
        void cerbereConfirmedForPersonalEmailOnly() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(mailFixeDomainNotTrusted);
            when(model.getMailFromLdap()).thenReturn("ldap@email.fr");

            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setMail("confirmed@email.fr");
            when(cerbereConfirmationRepository.findConfirmedByPersonId(1L))
                    .thenReturn(List.of(confirmation));

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo("ldap@email.fr");
            assertThat(result.getEmailPersonnel()).isEqualTo("confirmed@email.fr");
        }

        @Test
        @DisplayName("LDAP fallback quand pas de cerbere confirmed et email DB null")
        void ldapFallbackWhenDbNull() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(model.getMailFromLdap()).thenReturn("ldap@email.fr");
            when(cerbereConfirmationRepository.findConfirmedByPersonId(1L))
                    .thenReturn(Collections.emptyList());
            when(aPersonneBase.getEmailPersonnel()).thenReturn("perso@email.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo("ldap@email.fr");
            assertThat(result.getEmailPersonnel()).isEqualTo("perso@email.fr");
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
            when(aPersonneBase.getEmail()).thenReturn(mailFixeValue);

            UserDTO result = factory.from(model, extModel);
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(mailFixeValue);
        }
    }

    @Nested
    @DisplayName("from(PersonneDTO, IExternalUser) - canEditEmail / passEditable / liens")
    class FromPersonneDtoPermissionsTests {

        private ServiceProperties sp;

        @BeforeEach
        void permissionsSetUp() {
            sp = factory.getServiceProperties();
            sp.getCustomParams().setLienEdu("https://educonnect");
            sp.getCustomParams().setLienPassEtab("https://passetab");
        }

        @Test
        @DisplayName("EDUCATION + domaine ac-orleans-tours.fr → passEditable=false")
        void educationTrustedDomainNotEditable() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("user@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@ac-orleans-tours.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("EDUCATION + domaine non fiable → passEditable=false (isConnectOk)")
        void educationUntrustedDomainNotEditable() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("user@other.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@other.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("AGRI → passEditable=false")
        void agriNotEditable() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.AGRI);
            when(model.getMailFixe()).thenReturn("user@educagri.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@educagri.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("PERSONNEL → passEditable=true")
        void personnelEditable() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.PERSONNEL);
            when(model.getMailFixe()).thenReturn("user@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("user@ac-orleans-tours.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("enumPublic null → passEditable=false")
        void nullEnumPublicNotEditable() {
            when(model.getEnumPublic()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getMdp()).isFalse();
        }

        @Test
        @DisplayName("ELEVE + sans emailFixe → canEditEmail=true, passEditable=true")
        void eleveNoMailFixe() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("default@test.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getCanEditEmail()).isTrue();
            assertThat(result.getMdp()).isTrue();
        }

        @Test
        @DisplayName("EDUCATION + emailPerso existant → canEditEmail=true")
        void educationWithPersonalEmail() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("pro@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("pro@ac-orleans-tours.fr");
            when(aPersonneBase.getEmailPersonnel()).thenReturn("perso@gmail.com");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getCanEditEmail()).isTrue();
        }

        @Test
        @DisplayName("EDUCATION + sans emailPerso + avec mailFixe → canEditEmail=false")
        void educationNoPersonalEmailWithMailFixe() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.EDUCATION);
            when(model.getMailFixe()).thenReturn("pro@ac-orleans-tours.fr");
            when(aPersonneBase.getEmail()).thenReturn("pro@ac-orleans-tours.fr");
            when(aPersonneBase.getEmailPersonnel()).thenReturn(null);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getCanEditEmail()).isFalse();
        }

        @Test
        @DisplayName("buildUserPublicLinks: eduConnect + passEtab")
        void eduConnectAndPassEtab() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE_EDUC);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(structureService.isReseauRecia(model)).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getUserPublic()).containsExactly("https://educonnect", "https://passetab");
        }

        @Test
        @DisplayName("buildUserPublicLinks: eduConnect seul")
        void eduConnectOnly() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE_EDUC);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(structureService.isReseauRecia(model)).thenReturn(false);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getUserPublic()).containsExactly("https://educonnect");
        }

        @Test
        @DisplayName("buildUserPublicLinks: passEtab seul")
        void passEtabOnly() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(structureService.isReseauRecia(model)).thenReturn(true);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getUserPublic()).containsExactly("https://passetab");
        }

        @Test
        @DisplayName("buildUserPublicLinks: aucun lien")
        void noLinks() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(structureService.isReseauRecia(model)).thenReturn(false);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getUserPublic()).isEmpty();
        }

        @Test
        @DisplayName("resolveEmail: LDAP blank → fallback extModel.getEmail()")
        void emailFallbackExtModel() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(model.getMailFromLdap()).thenReturn("");
            when(extModel.getEmail()).thenReturn("ext@test.fr");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo("ext@test.fr");
        }

        @Test
        @DisplayName("resolveEtablissementName: exception → null")
        void etablissementException() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(structureDto.getDisplayName()).thenThrow(new RuntimeException("DB error"));

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEtab()).isNull();
        }

        @Test
        @DisplayName("resolveAvatarUrl: avec photo")
        void avatarWithPhoto() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(aPersonneBase.getPhoto()).thenReturn("http://photo.url");

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getAvatar()).isEqualTo("http://photo.url");
        }

        @Test
        @DisplayName("resolveAvatarUrl: sans photo → null")
        void avatarWithoutPhoto() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(null);
            when(aPersonneBase.getEmail()).thenReturn("test@test.fr");
            when(aPersonneBase.getPhoto()).thenReturn(null);

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getAvatar()).isNull();
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @ParameterizedTest(name = "{0} refusé")
        @MethodSource("fr.recia.mce.api.escomceapi.services.factories.impl.UserDTOFactoryImplTest#blockedProfilesForPasswordChange")
        @DisplayName("Profils bloqués → AccessDeniedException")
        void passwordChangeBlockedForProfile(EnumPublic pub, String mailFixe) {
            when(soffitHolder.getSub()).thenReturn("testSub");
            when(model.getEnumPublic()).thenReturn(pub);
            lenient().when(model.getMailFixe()).thenReturn(mailFixe);
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(model);

            assertThatThrownBy(() -> factory.changePassword("testUid", new PasswordChangeRequestDTO()))
                    .isInstanceOf(AccessDeniedException.class);

            verify(passwordService, never()).changePassword(any(), any());
        }

        @ParameterizedTest(name = "{0} autorisé")
        @MethodSource("fr.recia.mce.api.escomceapi.services.factories.impl.UserDTOFactoryImplTest#allowedProfilesForPasswordChange")
        @DisplayName("Profils autorisés → appel PasswordService")
        void passwordChangeAllowedForProfile(EnumPublic pub, String mailFixe) {
            when(soffitHolder.getSub()).thenReturn("testSub");
            when(model.getEnumPublic()).thenReturn(pub);
            lenient().when(model.getMailFixe()).thenReturn(mailFixe);
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(model);

            PasswordChangeRequestDTO req = new PasswordChangeRequestDTO();
            factory.changePassword("testUid", req);

            verify(passwordService).changePassword(model, req);
            verify(personneService).clearUserCaches("testUid");
        }

        @Test
        @DisplayName("EnumPublic null → AccessDeniedException")
        void passwordChangeDeniedWhenEnumPublicNull() {
            when(soffitHolder.getSub()).thenReturn("testSub");
            when(model.getEnumPublic()).thenReturn(null);
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(model);

            assertThatThrownBy(() -> factory.changePassword("testUid", new PasswordChangeRequestDTO()))
                    .isInstanceOf(AccessDeniedException.class);

            verify(passwordService, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("EnumPublic null mais evalPublic calcule un profil autorisé → succès")
        void passwordChangeEvalPublicFallback() {
            APersonne ap = new APersonne();
            ap.setUid("testUid");
            ap.setId(1L);
            ap.setCategorie("Eleve");
            PersonneDTO realModel = new PersonneDTO(ap, (AStructure) null);

            when(soffitHolder.getSub()).thenReturn("testSub");
            when(personneService.retrievePersonnebyUid("testUid")).thenReturn(realModel);

            PasswordChangeRequestDTO req = new PasswordChangeRequestDTO();
            factory.changePassword("testUid", req);

            verify(passwordService).changePassword(realModel, req);
            verify(personneService).clearUserCaches("testUid");
            assertThat(realModel.getEnumPublic()).isEqualTo(EnumPublic.ELEVE);
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
        @DisplayName("NON_PROF_COL_LOCAL + isRegion + isLocalUser → PERSONNEL")
        void nonProfColLocalRegionLocalUser() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "SarapisUi-COLL-CVDL", null)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + isRegion + !isLocalUser → CVDL")
        void nonProfColLocalRegionNonLocalUser() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "ENT-COLL-CVDL", null)))
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

        @Test
        @DisplayName("ntPass : CVDL + regex + isMemberOf match → ntPass=true")
        void ntPassCvdlMatch() {
            PersonneDTO p = modelWithDomSource("Non_enseignant_collectivite_locale", "ENT-COLL-CVDL", null);
            IExternalUser ext = mock(IExternalUser.class);
            when(ext.getAttribute("isMemberOf")).thenReturn(List.of("nt-users"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithNtPassword", Pattern.compile("nt-users"));

            eval(p);
            assertThat(p.isNtPass()).isTrue();
        }

        @Test
        @DisplayName("ntPass : CVDL + regex + no match → ntPass=false")
        void ntPassCvdlNoMatch() {
            PersonneDTO p = modelWithDomSource("Non_enseignant_collectivite_locale", "ENT-COLL-CVDL", null);
            IExternalUser ext = mock(IExternalUser.class);
            when(ext.getAttribute("isMemberOf")).thenReturn(List.of("other-group"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithNtPassword", Pattern.compile("nt-users"));

            eval(p);
            assertThat(p.isNtPass()).isFalse();
        }

        @Test
        @DisplayName("ntPass : GIP + regex + isMemberOf match → ntPass=true")
        void ntPassGipMatch() {
            PersonneDTO p = modelWithDomSource("Enseignant", null, DomSource.GIP);
            IExternalUser ext = mock(IExternalUser.class);
            when(ext.getAttribute("isMemberOf")).thenReturn(List.of("gip-nt"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithNtPassword", Pattern.compile("gip-nt"));

            eval(p);
            assertThat(p.isNtPass()).isTrue();
        }

        @Test
        @DisplayName("ntPass : non-CVDL non-GIP → ntPass=false")
        void ntPassNotCvdlNorGip() {
            PersonneDTO p = modelWithDomSource("Enseignant", null, DomSource.AC);
            IExternalUser ext = mock(IExternalUser.class);
            lenient().when(ext.getAttribute("isMemberOf")).thenReturn(List.of("nt-users"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithNtPassword", Pattern.compile("nt-users"));

            eval(p);
            assertThat(p.isNtPass()).isFalse();
        }

        @Test
        @DisplayName("ntPass : regex null → ntPass=false")
        void ntPassRegexNull() {
            PersonneDTO p = modelWithDomSource("Non_enseignant_collectivite_locale", "SarapisUi-COLL-CVDL", null);
            IExternalUser ext = mock(IExternalUser.class);
            lenient().when(ext.getAttribute("isMemberOf")).thenReturn(List.of("nt-users"));
            p.setExtUser(ext);
            ReflectionTestUtils.setField(factory, "groupsWithNtPassword", null);

            eval(p);
            assertThat(p.isNtPass()).isFalse();
        }

        @Test
        @DisplayName("ntPass : extUser null → ntPass=false")
        void ntPassNoExtUser() {
            PersonneDTO p = modelWithDomSource("Non_enseignant_collectivite_locale", "SarapisUi-COLL-CVDL", null);
            p.setExtUser(null);
            ReflectionTestUtils.setField(factory, "groupsWithNtPassword", Pattern.compile("nt-users"));

            eval(p);
            assertThat(p.isNtPass()).isFalse();
        }

        @Test
        @DisplayName("PARENT + null ds → PARENT")
        void parentNullDs() {
            assertThat(eval(modelWithDomSource("Personne_relation_eleve", null, null))).isEqualTo(EnumPublic.PARENT);
        }

        @Test
        @DisplayName("PROF + null ds → PERSONNEL")
        void profNullDs() {
            PersonneDTO p = createModel("Enseignant", null);
            p.setStructureDto(null);
            assertThat(eval(p)).isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ETAB + AC + !isLocalUser → EDUCATION")
        void nonProfEtabAcNonLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_etablissement", "ENT-Externe", DomSource.AC)))
                    .isEqualTo(EnumPublic.EDUCATION);
        }

        @Test
        @DisplayName("NON_PROF_ETAB + LA + isLocalUser → PERSONNEL")
        void nonProfEtabLaLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_etablissement", "SarapisUi-Orleans", DomSource.LA)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ETAB + default ds → PERSONNEL")
        void nonProfEtabDefaultDs() {
            assertThat(eval(modelWithDomSource("Non_enseignant_etablissement", null, DomSource.CFA)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ETAB + null ds → PERSONNEL")
        void nonProfEtabNullDs() {
            assertThat(eval(modelWithDomSource("Non_enseignant_etablissement", null, null)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_ACAD + AC + !isLocalUser → EDUCATION")
        void nonProfAcadAcNonLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_service_academique", "ENT-Externe", DomSource.AC)))
                    .isEqualTo(EnumPublic.EDUCATION);
        }

        @Test
        @DisplayName("NON_PROF_ACAD + LA + isLocalUser → PERSONNEL")
        void nonProfAcadLaLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_service_academique", "SarapisUi-Orleans", DomSource.LA)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + !isRegion + AC + isLocalUser → PERSONNEL (fallthrough NON_PROF_ETAB)")
        void nonProfColLocalNonRegionAcLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "SarapisUi-Paris", DomSource.AC)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + !isRegion + AC + !isLocalUser → EDUCATION (fallthrough NON_PROF_ETAB)")
        void nonProfColLocalNonRegionAcNonLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "ENT-Externe", DomSource.AC)))
                    .isEqualTo(EnumPublic.EDUCATION);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + !isRegion + LA + isLocalUser → PERSONNEL (fallthrough NON_PROF_ETAB)")
        void nonProfColLocalNonRegionLaLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "SarapisUi-Orleans", DomSource.LA)))
                    .isEqualTo(EnumPublic.PERSONNEL);
        }

        @Test
        @DisplayName("NON_PROF_COL_LOCAL + !isRegion + LA + !isLocalUser → AGRI (fallthrough NON_PROF_ETAB)")
        void nonProfColLocalNonRegionLaNonLocal() {
            assertThat(eval(modelWithDomSource("Non_enseignant_collectivite_locale", "ENT-Agri", DomSource.LA)))
                    .isEqualTo(EnumPublic.AGRI);
        }
    }
}
