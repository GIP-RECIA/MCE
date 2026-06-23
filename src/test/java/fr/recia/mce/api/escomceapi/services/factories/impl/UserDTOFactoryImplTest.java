package fr.recia.mce.api.escomceapi.services.factories.impl;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.AvatarProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
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
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - UserDTOFactoryImpl (mailFixeConfiance)")
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
    private final String confirmedMailValue = "confirmed@email.fr";

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
    @DisplayName("Cas mailFixeConfiance = true")
    class MailFixeConfianceTrue {

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
    }

    @Nested
    @DisplayName("Cas mailFixeConfiance = false")
    class MailFixeConfianceFalse {

        @Test
        @DisplayName("Domaine non fiable + mail confirmé en DB → email confirmé affiché")
        void notTrustedWithConfirmedEmail() {
            when(model.getEnumPublic()).thenReturn(EnumPublic.ELEVE);
            when(model.getMailFixe()).thenReturn(mailFixeDomainNotTrusted);
            when(aPersonneBase.getEmail()).thenReturn(mailFixeDomainNotTrusted);

            CerbereConfirmation confirmation = new CerbereConfirmation();
            confirmation.setMail(confirmedMailValue);
            when(cerbereConfirmationRepository.findConfirmedByPersonId(1L))
                    .thenReturn(List.of(confirmation));

            UserDTO result = factory.from(model, extModel);

            assertThat(result.getEmail()).isEqualTo(confirmedMailValue);
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
}
