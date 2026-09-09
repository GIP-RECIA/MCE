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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.naming.NamingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.DirContextAdapter;

import fr.recia.mce.api.escomceapi.ldap.ExternalUser;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;

@ExtendWith(MockitoExtension.class)
class LdapUserContextMapperTest {

    @Mock
    private DirContextAdapter context;
    private LdapUserContextMapper mapper;

    @BeforeEach
    void setUp() {
        ExternalUserHelper helper = new ExternalUserHelper(
                "uid", "displayName", "mail", "groupAttr",
                "groupAttr", "eleveRelation", "eleveTuteur",
                "tuteurEleve", "enseignement", "codeMatiere",
                "avatar", "etatCompte", Collections.emptySet(), Collections.emptySet(), null);
        mapper = new LdapUserContextMapper(helper);
    }

    @Test
    void shouldMapBasicUser() throws NamingException {
        when(context.getStringAttribute("uid")).thenReturn("testuser");
        when(context.getStringAttribute("displayName")).thenReturn("Test User");
        when(context.getStringAttribute("mail")).thenReturn("test@test.com");
        when(context.attributeExists("mail")).thenReturn(true);

        IExternalUser result = mapper.mapFromContext(context);

        assertThat(result).isInstanceOf(ExternalUser.class);
        ExternalUser user = (ExternalUser) result;
        assertThat(user.getId()).isEqualTo("testuser");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(user.getEmail()).isEqualTo("test@test.com");
        assertThat(user.getAttributes()).containsKeys("uid", "displayName", "mail");
    }

    @Test
    void shouldMapUserWithoutEmail() throws NamingException {
        when(context.getStringAttribute("uid")).thenReturn("testuser");
        when(context.getStringAttribute("displayName")).thenReturn("Test User");
        when(context.attributeExists("mail")).thenReturn(false);

        IExternalUser result = mapper.mapFromContext(context);

        ExternalUser user = (ExternalUser) result;
        assertThat(user.getId()).isEqualTo("testuser");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(user.getEmail()).isNull();
    }

    @Test
    void shouldMapUserWithGroupAttribute() throws NamingException {
        when(context.getStringAttribute("uid")).thenReturn("testuser");
        when(context.getStringAttribute("displayName")).thenReturn("Test User");
        when(context.attributeExists("mail")).thenReturn(true);
        when(context.getStringAttribute("mail")).thenReturn("test@test.com");
        when(context.attributeExists("groupAttr")).thenReturn(true);
        when(context.getStringAttributes("groupAttr")).thenReturn(new String[]{"group1", "group2"});

        IExternalUser result = mapper.mapFromContext(context);

        ExternalUser user = (ExternalUser) result;
        assertThat(user.getAttributes()).containsKey("groupAttr");
        assertThat(user.getAttribute("groupAttr")).contains("group1", "group2");
    }

}
