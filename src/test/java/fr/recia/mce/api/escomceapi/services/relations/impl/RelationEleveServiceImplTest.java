package fr.recia.mce.api.escomceapi.services.relations.impl;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - RelationEleveServiceImpl")
class RelationEleveServiceImplTest {

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private PersonneService personneService;

    @Mock
    private ExternalUserHelper extUserHelper;

    @Mock
    private MCEProperties mceProperties;

    private RelationEleveServiceImpl relationEleveService;

    @BeforeEach
    void setUp() {
        ServiceProperties serviceProperties = new ServiceProperties();
        ServiceProperties.RelationProperties relProps = serviceProperties.getRelationProperties();
        relProps.setRegexUid("uid=(\\w+),.*");
        relProps.setRegexRelation("uid=(\\w+),[^$]+\\$([^$]+)\\$([^$]+)\\$(1|2)\\$([^$]+)\\$([^$]+)");
        relProps.setGroupUid(1);
        relProps.setGroupTypRel(2);
        relProps.setGroupRespFinance(3);
        relProps.setGroupRespLegal(4);
        relProps.setGroupCodeContact(5);
        relProps.setGroupCodePaiement(6);

        when(mceProperties.getService()).thenReturn(serviceProperties);

        relationEleveService = new RelationEleveServiceImpl(mceProperties);
        ReflectionTestUtils.setField(relationEleveService, "aPersonneRepository", aPersonneRepository);
        ReflectionTestUtils.setField(relationEleveService, "personneService", personneService);
        ReflectionTestUtils.setField(relationEleveService, "extUserHelper", extUserHelper);
    }

    private static final String STUDENT_UID = "F20102xc";
    private static final String PARENT_UID = "pierrevar";

    @Test
    @DisplayName("allRelationEleves(IExternalUser) : aucun attribut LDAP → retourne vide (early exit)")
    void shouldReturnEmptyWhenNoLdapAttributes() {
        IExternalUser personne = mock(IExternalUser.class);
        when(extUserHelper.getUserEleveRelationAttribute()).thenReturn("ENTElevePersRelEleve");
        when(extUserHelper.getUserEleveTuteurAttribute()).thenReturn("ENTEleveEntrTutStage");
        when(personne.getId()).thenReturn(STUDENT_UID);
        when(personne.getAttribute("ENTElevePersRelEleve")).thenReturn(null);
        when(personne.getAttribute("ENTEleveEntrTutStage")).thenReturn(null);

        Collection<RelationEleveContact> result = relationEleveService.allRelationEleves(personne);

        assertThat(result).isEmpty();
        verify(aPersonneRepository, never()).findAllParentOfEleve(anyString());
    }

    @Test
    @DisplayName("allRelationEleves(IExternalUser) : attributs LDAP vides → fallback DB → retourne relations")
    void shouldFallbackToDbWhenLdapDataEmpty() {
        IExternalUser personne = mock(IExternalUser.class);
        when(extUserHelper.getUserEleveRelationAttribute()).thenReturn("ENTElevePersRelEleve");
        when(extUserHelper.getUserEleveTuteurAttribute()).thenReturn("ENTEleveEntrTutStage");
        when(personne.getId()).thenReturn(STUDENT_UID);
        when(personne.getAttribute("ENTElevePersRelEleve")).thenReturn(Collections.emptyList());
        when(personne.getAttribute("ENTEleveEntrTutStage")).thenReturn(null);

        AStructure structure = new AStructure();
        structure.setNom("College");

        APersonne parent = new APersonne();
        parent.setId(42L);
        parent.setUid(PARENT_UID);
        parent.setDisplayName("Pierre VAR");
        parent.setSn("VAR");
        parent.setGivenName("Pierre");
        parent.setAStructure(structure);

        APersonne enfant = new APersonne();
        enfant.setId(1L);
        enfant.setUid(STUDENT_UID);
        enfant.setDisplayName("Sara VAR");
        enfant.setSn("VAR");
        enfant.setGivenName("Sara");
        enfant.setAStructure(structure);

        RelationEleveContact dbRelation = new RelationEleveContact(
                "CONTACT2ELEVE", parent, enfant, "Autorite_parentale", "Père", true);

        when(aPersonneRepository.findAllParentOfEleve(STUDENT_UID))
                .thenReturn(List.of(dbRelation));

        Collection<RelationEleveContact> result = relationEleveService.allRelationEleves(personne);

        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);

        RelationEleveContact rel = result.iterator().next();
        assertThat(rel.getUidRelation()).isEqualTo(PARENT_UID);
        assertThat(rel.getDisplayNameRelation()).isEqualTo("Pierre VAR");
        assertThat(rel.getTypeRelation()).isEqualTo("Autorite_parentale");
        assertThat(rel.getLienParente()).isEqualTo("Père");
        assertThat(rel.isAutoriteParental()).isTrue();

        assertThat(rel.getEleve()).isNotNull();
        assertThat(rel.getEleve().getUid()).isEqualTo(STUDENT_UID);
        assertThat(rel.getContact()).isEqualTo(42L);
    }

    @Test
    @DisplayName("allRelationEleves(IExternalUser) : attributs LDAP vides, DB vide → retourne vide")
    void shouldFallbackToDbAndReturnEmptyWhenNoDbData() {
        IExternalUser personne = mock(IExternalUser.class);
        when(extUserHelper.getUserEleveRelationAttribute()).thenReturn("ENTElevePersRelEleve");
        when(extUserHelper.getUserEleveTuteurAttribute()).thenReturn("ENTEleveEntrTutStage");
        when(personne.getId()).thenReturn(STUDENT_UID);
        when(personne.getAttribute("ENTElevePersRelEleve")).thenReturn(Collections.emptyList());
        when(personne.getAttribute("ENTEleveEntrTutStage")).thenReturn(null);

        when(aPersonneRepository.findAllParentOfEleve(STUDENT_UID))
                .thenReturn(Collections.emptyList());

        Collection<RelationEleveContact> result = relationEleveService.allRelationEleves(personne);

        assertThat(result).isEmpty();
        verify(aPersonneRepository).findAllParentOfEleve(STUDENT_UID);
    }

    @Test
    @DisplayName("allRelationEleves(String) : UID null → retourne vide")
    void shouldReturnEmptyForNullUid() {
        Collection<RelationEleveContact> result = relationEleveService.allRelationEleves((String) null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("allRelationEleves(String) : utilisateur LDAP null → retourne vide")
    void shouldReturnEmptyWhenLdapUserNotFound() {
        when(personneService.retrievePersonLdap("inconnu")).thenReturn(null);

        Collection<RelationEleveContact> result = relationEleveService.allRelationEleves("inconnu");
        assertThat(result).isEmpty();
    }
}
