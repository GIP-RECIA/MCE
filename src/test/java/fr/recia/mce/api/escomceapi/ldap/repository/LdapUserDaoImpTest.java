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
package fr.recia.mce.api.escomceapi.ldap.repository;

import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import javax.naming.directory.ModificationItem;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - LdapUserDaoImp")
class LdapUserDaoImpTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private ExternalUserHelper externalUserHelper;

    @InjectMocks
    private LdapUserDaoImp dao;

    @Mock
    private IExternalUser mockUser;

    private final String uid = "testuser";

    @BeforeEach
    void setUp() {
        lenient().when(externalUserHelper.getUserIdAttribute()).thenReturn("uid");
        lenient().when(externalUserHelper.getUserDNSubPath()).thenReturn("ou=users,dc=example,dc=com");
        lenient().when(externalUserHelper.getAttributes()).thenReturn(Collections.emptySet());
        lenient().when(externalUserHelper.getUserEmailAttribute()).thenReturn("mail");
        lenient().when(externalUserHelper.getUserAvatarAttribute()).thenReturn("avatar");
    }

    @Nested
    @DisplayName("getUserByUid")
    class GetUserByUidTests {

        @Test
        @DisplayName("Succès : retourne l'utilisateur")
        void success() {
            when(ldapTemplate.searchForObject(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(mockUser);

            IExternalUser result = dao.getUserByUid(uid);

            assertThat(result).isSameAs(mockUser);
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable → PersonneNotFoundException")
        void notFound() {
            when(ldapTemplate.searchForObject(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenThrow(new EmptyResultDataAccessException(1));

            assertThatThrownBy(() -> dao.getUserByUid(uid))
                    .isInstanceOf(PersonneNotFoundException.class)
                    .hasMessageContaining(uid);
        }

        @Test
        @DisplayName("Échec : erreur technique LDAP → RuntimeException")
        void technicalError() {
            when(ldapTemplate.searchForObject(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenThrow(new RuntimeException("LDAP timeout"));

            assertThatThrownBy(() -> dao.getUserByUid(uid))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Erreur technique LDAP");
        }
    }

    @Nested
    @DisplayName("getByUids")
    class GetByUidsTests {

        @Test
        @DisplayName("Succès : retourne la liste des utilisateurs")
        void success() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(List.of(mockUser));

            List<IExternalUser> result = dao.getByUids(List.of("user1", "user2"));

            assertThat(result).containsExactly(mockUser);
        }

        @Test
        @DisplayName("Entrée nulle → liste vide")
        void nullInput() {
            List<IExternalUser> result = dao.getByUids(null);

            assertThat(result).isEmpty();
            verify(ldapTemplate, never()).search(any(LdapQuery.class), any(ContextMapper.class));
        }

        @Test
        @DisplayName("Entrée vide → liste vide")
        void emptyInput() {
            List<IExternalUser> result = dao.getByUids(Collections.emptyList());

            assertThat(result).isEmpty();
            verify(ldapTemplate, never()).search(any(LdapQuery.class), any(ContextMapper.class));
        }

        @Test
        @DisplayName("Échec : erreur technique LDAP → RuntimeException")
        void technicalError() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenThrow(new RuntimeException("LDAP timeout"));

            assertThatThrownBy(() -> dao.getByUids(List.of("user1")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Erreur technique LDAP");
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePasswordTests {

        @Test
        @DisplayName("Succès : met à jour le mot de passe LDAP")
        void success() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(List.of("cn=testuser,ou=users,dc=example,dc=com"));

            dao.updatePassword(uid, "{ARGON2}newHash");

            verify(ldapTemplate).modifyAttributes(eq("cn=testuser,ou=users,dc=example,dc=com"), any(ModificationItem[].class));
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable → PersonneNotFoundException")
        void notFound() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> dao.updatePassword(uid, "{ARGON2}newHash"))
                    .isInstanceOf(PersonneNotFoundException.class)
                    .hasMessageContaining(uid);

            verify(ldapTemplate, never()).modifyAttributes(anyString(), any(ModificationItem[].class));
        }

        @Test
        @DisplayName("Échec : erreur technique LDAP → RuntimeException")
        void technicalError() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenThrow(new RuntimeException("LDAP timeout"));

            assertThatThrownBy(() -> dao.updatePassword(uid, "{ARGON2}newHash"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("LDAP password update failed");

            verify(ldapTemplate, never()).modifyAttributes(anyString(), any(ModificationItem[].class));
        }
    }

    @Nested
    @DisplayName("updateEmail")
    class UpdateEmailTests {

        @Test
        @DisplayName("Succès : met à jour l'email LDAP")
        void success() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(List.of("cn=testuser,ou=users,dc=example,dc=com"));

            dao.updateEmail(uid, "new@test.com");

            verify(ldapTemplate).modifyAttributes(eq("cn=testuser,ou=users,dc=example,dc=com"), any(ModificationItem[].class));
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable → PersonneNotFoundException")
        void notFound() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> dao.updateEmail(uid, "new@test.com"))
                    .isInstanceOf(PersonneNotFoundException.class)
                    .hasMessageContaining(uid);

            verify(ldapTemplate, never()).modifyAttributes(anyString(), any(ModificationItem[].class));
        }

        @Test
        @DisplayName("Échec : erreur technique LDAP → RuntimeException")
        void technicalError() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenThrow(new RuntimeException("LDAP timeout"));

            assertThatThrownBy(() -> dao.updateEmail(uid, "new@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("LDAP email update failed");

            verify(ldapTemplate, never()).modifyAttributes(anyString(), any(ModificationItem[].class));
        }
    }

    @Nested
    @DisplayName("updateAvatarLDAP")
    class UpdateAvatarLdapTests {

        @Test
        @DisplayName("Succès : met à jour l'avatar LDAP")
        void success() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(List.of("cn=testuser,ou=users,dc=example,dc=com"));

            dao.updateAvatarLDAP(uid, "http://avatar.url/img.jpg");

            verify(ldapTemplate).modifyAttributes(eq("cn=testuser,ou=users,dc=example,dc=com"), any(ModificationItem[].class));
        }

        @Test
        @DisplayName("Échec : utilisateur introuvable → PersonneNotFoundException")
        void notFound() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> dao.updateAvatarLDAP(uid, "http://avatar.url/img.jpg"))
                    .isInstanceOf(PersonneNotFoundException.class)
                    .hasMessageContaining(uid);

            verify(ldapTemplate, never()).modifyAttributes(anyString(), any(ModificationItem[].class));
        }

        @Test
        @DisplayName("Échec : erreur technique LDAP → RuntimeException")
        void technicalError() {
            when(ldapTemplate.search(any(LdapQuery.class), any(ContextMapper.class)))
                    .thenThrow(new RuntimeException("LDAP timeout"));

            assertThatThrownBy(() -> dao.updateAvatarLDAP(uid, "http://avatar.url/img.jpg"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("LDAP avatar update failed");

            verify(ldapTemplate, never()).modifyAttributes(anyString(), any(ModificationItem[].class));
        }
    }

}
