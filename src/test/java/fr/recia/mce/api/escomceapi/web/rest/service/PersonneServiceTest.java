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
package fr.recia.mce.api.escomceapi.web.rest.service;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.AvatarProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.exception.InvalidAvatarException;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Exhaustifs - PersonneService")
public class PersonneServiceTest {

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private IExternalUserDao extDao;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private MCEProperties mceProperties;

    @InjectMocks
    private PersonneService personneService;

    private final String uid = "user123";

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getCache(anyString())).thenReturn(cache);
        lenient().when(cache.get(uid, PersonneDTO.class)).thenReturn(null);
        lenient().when(mailProperties.getRegexValideAddr())
                .thenReturn("[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z-]{2,4})");
        lenient().when(mailProperties.getRegexsDomainesExclus())
                .thenReturn("netocentre.fr touraine-eschool.fr chercan.fr colleges41.fr mon-e-college.loiret.fr e-college.indre.fr colleges-eureliens.fr");

        AvatarProperties avatarProps = new AvatarProperties();
        avatarProps.setStoragePath("./data/avatars");
        avatarProps.setFilename("avatar0.jpg");
        avatarProps.setFilenameBackup("avatar1.jpg");
        avatarProps.setBaseUrl("/api/personne/mce/");
        avatarProps.setMaxSize(524288);
        avatarProps.setAllowedTypes(java.util.List.of("image/jpeg", "image/png"));
        lenient().when(mceProperties.getAvatar()).thenReturn(avatarProps);
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
        @DisplayName("Succès : Validation OK + Cache Eviction (pas de persistance DB/LDAP)")
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

            // Assert - pas de mise à jour DB ou LDAP, seulement cache eviction
            verify(aPersonneRepository, never()).saveAndFlush(any());
            verifyNoInteractions(extDao);

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

        @Test
        @DisplayName("Échec : format email invalide")
        void updateEmail_InvalidFormat() {
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            personneDTO.setEnumPublic(EnumPublic.ELEVE);
            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);

            assertThatThrownBy(() -> personneService.updateEmail(uid, "invalid-email"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Échec : domaine exclu")
        void updateEmail_ExcludedDomain() {
            APersonne aPersonne = new APersonne();
            aPersonne.setUid(uid);
            PersonneDTO personneDTO = new PersonneDTO(aPersonne);
            personneDTO.setEnumPublic(EnumPublic.ELEVE);
            when(aPersonneRepository.getPersonneByUid(uid)).thenReturn(personneDTO);

            assertThatThrownBy(() -> personneService.updateEmail(uid, "user@netocentre.fr"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Tests d'Avatar (getAvatar)")
    class AvatarReadTests {

        @Test
        @DisplayName("Avatar non trouvé → null")
        void avatarNotFound() {
            byte[] result = personneService.getAvatar("abcdef");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Tests de Cache (clearUserCaches)")
    class ClearCacheTests {

        @Test
        @DisplayName("Cache présent → éviction")
        void clearWithCache() {
            personneService.clearUserCaches(uid);
            verify(cache, times(2)).evict(uid);
        }

        @Test
        @DisplayName("Cache null → pas d'erreur")
        void clearWithoutCache() {
            when(cacheManager.getCache(anyString())).thenReturn(null);

            personneService.clearUserCaches(uid);
            verifyNoInteractions(cache);
        }
    }

    @Nested
    @DisplayName("Tests de Cache Hit (getUserByUid)")
    class CacheHitTests {

        @Test
        @DisplayName("Cache hit retourne les données sans interroger la DB")
        void cacheHitReturnsFromCache() {
            PersonneDTO cachedPersonne = new PersonneDTO(new APersonne());
            when(cache.get(uid, PersonneDTO.class)).thenReturn(cachedPersonne);

            PersonneDTO result = personneService.getUserByUid(uid);

            assertThat(result).isEqualTo(cachedPersonne);
            verifyNoInteractions(aPersonneRepository);
            verify(cacheManager).getCache("personneDBCache");
        }
    }

    @Nested
    @DisplayName("Tests de Mise à Jour d'Avatar (updateAvatar)")
    class UpdateAvatarTests {

        private byte[] createValidJpeg() throws IOException {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        }

        @Test
        @DisplayName("Succès : rotation + écriture + DB + LDAP + cache eviction")
        void updateAvatar_Success() throws IOException {
            Path tempDir = Files.createTempDirectory("avatar-test-");
            try {
                mceProperties.getAvatar().setStoragePath(tempDir.toString());
                byte[] imageBytes = createValidJpeg();

                String uid = "ab"; // hash → "2646" → groupDir="26" userDir="46"
                APersonne entity = new APersonne();
                entity.setPhoto("/old/photo?v=3");
                when(aPersonneRepository.findByUid(uid)).thenReturn(entity);

                personneService.updateAvatar(uid, imageBytes);

                // Vérifier fichier créé
                Path expectedFile = tempDir.resolve("26").resolve("46").resolve("avatar0.jpg");
                assertThat(expectedFile).exists();
                assertThat(Files.readAllBytes(expectedFile)).isEqualTo(imageBytes);

                // Vérifier URL en base
                assertThat(entity.getPhoto()).isEqualTo("/api/personne/mce/2646/avatar0.jpg?v=4");
                assertThat(entity.getDateModification()).isNotNull();
                verify(aPersonneRepository).saveAndFlush(entity);
                verify(extDao).updateAvatarLDAP(uid, entity.getPhoto());
                verify(cache, times(2)).evict(uid);
            } finally {
                cleanupTempDir(tempDir);
            }
        }

        @Test
        @DisplayName("Rotation : backup existant supprimé")
        void updateAvatar_Rotate() throws IOException {
            Path tempDir = Files.createTempDirectory("avatar-test-");
            try {
                mceProperties.getAvatar().setStoragePath(tempDir.toString());
                byte[] imageBytes = createValidJpeg();

                // Pré-créer avatar0 et avatar1
                Path dir = tempDir.resolve("26").resolve("46");
                Files.createDirectories(dir);
                Path p0 = dir.resolve("avatar0.jpg");
                Path p1 = dir.resolve("avatar1.jpg");
                Files.write(p0, "old0".getBytes());
                Files.write(p1, "old1".getBytes());

                String uid = "ab";
                APersonne entity = new APersonne();
                entity.setPhoto("/old/photo?v=1");
                when(aPersonneRepository.findByUid(uid)).thenReturn(entity);

                personneService.updateAvatar(uid, imageBytes);

                // Vérifier rotation : old0 → avatar1, old1 supprimé
                assertThat(p0).exists();
                assertThat(Files.readAllBytes(p0)).isEqualTo(imageBytes);
                assertThat(Files.readAllBytes(p1)).isEqualTo("old0".getBytes());
            } finally {
                cleanupTempDir(tempDir);
            }
        }

        @Test
        @DisplayName("Version null/absente → démarre à v=1")
        void updateAvatar_NoVersion() throws IOException {
            Path tempDir = Files.createTempDirectory("avatar-test-");
            try {
                mceProperties.getAvatar().setStoragePath(tempDir.toString());
                byte[] imageBytes = createValidJpeg();

                String uid = "ab";
                APersonne entity = new APersonne();
                when(aPersonneRepository.findByUid(uid)).thenReturn(entity);

                personneService.updateAvatar(uid, imageBytes);

                assertThat(entity.getPhoto()).isEqualTo("/api/personne/mce/2646/avatar0.jpg?v=1");
                verify(aPersonneRepository).saveAndFlush(entity);
            } finally {
                cleanupTempDir(tempDir);
            }
        }

        @Test
        @DisplayName("Erreur : fichier trop volumineux")
        void updateAvatar_TooLarge() {
            byte[] largeFile = new byte[600000];
            assertThatThrownBy(() -> personneService.updateAvatar(uid, largeFile))
                    .isInstanceOf(InvalidAvatarException.class)
                    .hasMessageContaining("Taille");
        }

        @Test
        @DisplayName("Erreur : format invalide")
        void updateAvatar_InvalidFormat() {
            assertThatThrownBy(() -> personneService.updateAvatar(uid, "not-an-image".getBytes()))
                    .isInstanceOf(InvalidAvatarException.class)
                    .hasMessageContaining("Format");
        }

        @Test
        @DisplayName("Erreur : utilisateur introuvable (après validation image)")
        void updateAvatar_UserNotFound() throws IOException {
            Path tempDir = Files.createTempDirectory("avatar-test-");
            try {
                mceProperties.getAvatar().setStoragePath(tempDir.toString());
                byte[] imageBytes = createValidJpeg();

                when(aPersonneRepository.findByUid(uid)).thenReturn(null);

                assertThatThrownBy(() -> personneService.updateAvatar(uid, imageBytes))
                        .isInstanceOf(PersonneNotFoundException.class);
            } finally {
                cleanupTempDir(tempDir);
            }
        }

        @Test
        @DisplayName("Erreur LDAP non bloquante (log warning seulement)")
        void updateAvatar_LdapErrorDoesNotThrow() throws IOException {
            Path tempDir = Files.createTempDirectory("avatar-test-");
            try {
                mceProperties.getAvatar().setStoragePath(tempDir.toString());
                byte[] imageBytes = createValidJpeg();

                String uid = "ab";
                APersonne entity = new APersonne();
                when(aPersonneRepository.findByUid(uid)).thenReturn(entity);
                doThrow(new RuntimeException("LDAP fail")).when(extDao).updateAvatarLDAP(anyString(), anyString());

                personneService.updateAvatar(uid, imageBytes);

                verify(aPersonneRepository).saveAndFlush(entity);
                verify(extDao).updateAvatarLDAP(uid, entity.getPhoto());
            } finally {
                cleanupTempDir(tempDir);
            }
        }
    }

    private static void cleanupTempDir(Path tempDir) {
        if (tempDir != null) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            } catch (Exception ignored) {
            }
        }
    }
}
