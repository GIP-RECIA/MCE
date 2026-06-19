package fr.recia.mce.api.escomceapi.web.rest.service;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Exhaustifs - PersonneService")
class PersonneServiceTest {

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private IExternalUserDao extDao;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private PersonneService personneService;

    private final String uid = "user123";

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getCache(anyString())).thenReturn(cache);
    }

    @Nested
    @DisplayName("Tests de Récupération (getUserByUid / retrievePersonnebyUid)")
    class RetrievalTests {

        @Test
        @DisplayName("Succès : Utilisateur trouvé en DB et chargé depuis LDAP")
        void shouldReturnPersonneWithLdapData() {
            // Arrange
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            IExternalUser externalUser = mock(IExternalUser.class);

            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);
            when(cache.get(uid, IExternalUser.class)).thenReturn(null); // Cache miss
            when(extDao.getUserByUid(uid)).thenReturn(externalUser);

            // Act
            PersonneDTO result = personneService.getUserByUid(uid);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getUid()).isEqualTo(uid);
            assertThat(result.getExtUser()).isEqualTo(externalUser);
            verify(cache).putIfAbsent(uid, externalUser);
        }

        @Test
        @DisplayName("Succès : Utilisateur trouvé en DB mais échec LDAP (ne doit pas planter)")
        void shouldReturnPersonneEvenIfLdapFails() {
            // Arrange
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);

            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);
            when(cache.get(uid, IExternalUser.class)).thenReturn(null);
            when(extDao.getUserByUid(uid)).thenThrow(new RuntimeException("LDAP Error"));

            // Act
            PersonneDTO result = personneService.getUserByUid(uid);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getExtUser()).isNull(); // Pas de données LDAP mais l'objet est là
            verify(aPersonneRepository).getPersonneByUid(uid);
        }

        @Test
        @DisplayName("Échec : Utilisateur inconnu en DB")
        void shouldReturnNullWhenNotFound() {
            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(null);
            assertThat(personneService.getUserByUid(uid)).isNull();
        }

        @Test
        @DisplayName("retrievePersonnebyUid doit déléguer à getUserByUid")
        void retrievePersonnebyUid_Delegates() {
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);

            PersonneDTO result = personneService.retrievePersonnebyUid(uid);
            assertThat(result).isNotNull();
            verify(aPersonneRepository).getPersonneByUid(uid);
        }
    }

    @Nested
    @DisplayName("Tests LDAP (retrievePersonLdap)")
    class LdapOnlyTests {

        @Test
        @DisplayName("Cache Hit : Retourne les données sans interroger le DAO")
        void shouldReturnFromCacheOnHit() {
            IExternalUser externalUser = mock(IExternalUser.class);
            when(cache.get(uid, IExternalUser.class)).thenReturn(externalUser);

            IExternalUser result = personneService.retrievePersonLdap(uid);

            assertThat(result).isEqualTo(externalUser);
            verifyNoInteractions(extDao);
        }

        @Test
        @DisplayName("Cache Miss : Interroge le DAO et met à jour le cache")
        void shouldFetchFromDaoOnMiss() {
            IExternalUser externalUser = mock(IExternalUser.class);
            when(cache.get(uid, IExternalUser.class)).thenReturn(null);
            when(extDao.getUserByUid(uid)).thenReturn(externalUser);

            IExternalUser result = personneService.retrievePersonLdap(uid);

            assertThat(result).isEqualTo(externalUser);
            verify(extDao).getUserByUid(uid);
            verify(cache).putIfAbsent(uid, externalUser);
        }

        @Test
        @DisplayName("Erreur LDAP : Retourne null et log l'erreur")
        void shouldReturnNullOnLdapException() {
            when(cache.get(uid, IExternalUser.class)).thenReturn(null);
            when(extDao.getUserByUid(uid)).thenThrow(new RuntimeException("Connection timeout"));

            IExternalUser result = personneService.retrievePersonLdap(uid);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Tests de Mise à Jour d'Email (updateEmail)")
    class UpdateEmailTests {

        @Test
        @DisplayName("Succès : Mise à jour DB + LDAP + Cache Eviction")
        void updateEmail_Success() {
            // Arrange
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            personneDTO.setEnumPublic(EnumPublic.ELEVE);
            String newEmail = "new@recia.fr";

            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);

            // Act
            personneService.updateEmail(uid, newEmail);

            // Assert
            assertThat(aPersonne.getEmail()).isNull();
            assertThat(aPersonne.getEmailPersonnel()).isEqualTo(newEmail);
            assertThat(aPersonne.getDateModification()).isNotNull();

            verify(aPersonneRepository).saveAndFlush(aPersonne);
            verify(extDao).updateEmail(uid, newEmail);

            // Vérification de l'éviction des caches
            verify(cacheManager).getCache("personneDBCache");
            verify(cacheManager).getCache("personneLDAPCache");
            verify(cache, times(2)).evict(uid);
        }

        @Test
        @DisplayName("Échec : Utilisateur introuvable (PersonneNotFoundException)")
        void updateEmail_UserNotFound() {
            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(null);

            assertThatThrownBy(() -> personneService.updateEmail(uid, "test@test.fr"))
                    .isInstanceOf(PersonneNotFoundException.class)
                    .hasMessageContaining(uid);

            verifyNoInteractions(extDao);
        }

        @Test
        @DisplayName("Échec : Erreur LDAP (RuntimeException)")
        void updateEmail_LdapError() {
            // Arrange
            APersonne aPersonne = new APersonne();
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            personneDTO.setEnumPublic(EnumPublic.ELEVE);
            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);
            doThrow(new RuntimeException("LDAP Read-only")).when(extDao).updateEmail(anyString(), anyString());

            // Act & Assert
            assertThatThrownBy(() -> personneService.updateEmail(uid, "new@test.fr"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Erreur lors de la mise à jour de l'email");

            // La DB est mise à jour avant (transactional)
            verify(aPersonneRepository).saveAndFlush(aPersonne);
        }

        @Test
        @DisplayName("Échec : email personnel non modifiable pour un personnel avec email fixe")
        void updateEmail_NotEditableForStaffWithFixedEmailAndNoPersonalEmail() {
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            aPersonne.setEmail("user@ac-orleans-tours.fr");
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            personneDTO.setEnumPublic(EnumPublic.EDUCATION);

            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);

            assertThatThrownBy(() -> personneService.updateEmail(uid, "new@test.fr"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("email personnel");

            verify(aPersonneRepository, never()).saveAndFlush(any());
            verifyNoInteractions(extDao);
        }
    }
}
