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

import javax.naming.NamingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.DirContextAdapter;

import fr.recia.mce.api.escomceapi.ldap.ExternalStructHelper;
import fr.recia.mce.api.escomceapi.ldap.ExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;

@ExtendWith(MockitoExtension.class)
class LdapStructureContextMapperTest {

    @Mock
    private DirContextAdapter context;
    private ExternalStructHelper helper;
    private LdapStructureContextMapper mapper;

    @BeforeEach
    void setUp() {
        helper = new ExternalStructHelper();
        helper.setStructIdAttribute("id");
        helper.setStructNameAttribute("name");
        helper.setStructDisplayNameAttribute("displayName");
        helper.setStructUaiAttribute("uai");
        helper.setStructTypeAttribute("type");
        helper.setStructDomainesAttribute("domaines");
        mapper = new LdapStructureContextMapper(helper);
    }

    @Test
    void shouldMapStructure() throws NamingException {
        when(context.getStringAttribute("id")).thenReturn("struct1");
        when(context.getStringAttribute("name")).thenReturn("Lycee Test");
        when(context.getStringAttribute("displayName")).thenReturn("Lycee Test - 0450001A");
        when(context.getStringAttribute("uai")).thenReturn("0450001A");
        when(context.getStringAttribute("type")).thenReturn("LYCEE");
        when(context.getStringAttributes("domaines")).thenReturn(new String[]{"ac-test.fr"});

        IExternalStructure result = mapper.mapFromContext(context);

        assertThat(result).isInstanceOf(ExternalStructure.class);
        ExternalStructure struct = (ExternalStructure) result;
        assertThat(struct.getId()).isEqualTo("struct1");
        assertThat(struct.getName()).isEqualTo("Lycee Test");
        assertThat(struct.getDisplayName()).isEqualTo("Lycee Test - 0450001A");
        assertThat(struct.getUai()).isEqualTo("0450001A");
        assertThat(struct.getType()).isEqualTo("LYCEE");
        assertThat(struct.getDomaines()).containsExactly("ac-test.fr");
    }

    @Test
    void shouldReplaceDollarWithSpaceInName() throws NamingException {
        when(context.getStringAttribute("id")).thenReturn("struct2");
        when(context.getStringAttribute("name")).thenReturn("Lycee$Test");
        when(context.getStringAttribute("displayName")).thenReturn("Lycee Test");
        when(context.getStringAttribute("uai")).thenReturn("0450001A");
        when(context.getStringAttribute("type")).thenReturn("LYCEE");
        when(context.getStringAttributes("domaines")).thenReturn(new String[]{"ac-test.fr"});

        IExternalStructure result = mapper.mapFromContext(context);

        ExternalStructure struct = (ExternalStructure) result;
        assertThat(struct.getName()).isEqualTo("Lycee Test");
    }

}
