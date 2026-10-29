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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.recia.mce.api.escomceapi.ldap.ConstLDAP;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;

import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

@Service
public class LdapUserContextMapper implements ContextMapper<LdapUserDto> {

    @Override
    public LdapUserDto mapFromContext(Object ctx) throws NamingException {
        DirContextAdapter context = (DirContextAdapter) ctx;
        LdapUserDto person = new LdapUserDto();

        // UID, displayName et email
        person.setId(context.getStringAttribute(ConstLDAP.UID));
        person.setDisplayName(context.getStringAttribute(ConstLDAP.DISPLAY_NAME));
        if (context.attributeExists(ConstLDAP.MAIL)) {
            person.setEmail(context.getStringAttribute(ConstLDAP.MAIL));
        }

        // Autres attributs
        Map<String, List<String>> attributes = new HashMap<>();
        Attributes ldapAttributes = context.getAttributes();
        NamingEnumeration<? extends Attribute> allAttributes = ldapAttributes.getAll();
        while (allAttributes.hasMore()) {
            Attribute attribute = allAttributes.next();
            String attributeName = attribute.getID();
            List<String> values = new ArrayList<>();
            NamingEnumeration<?> allValues = attribute.getAll();
            while (allValues.hasMore()) {
                Object value = allValues.next();
                values.add(value.toString());
            }
            attributes.put(attributeName, values);
        }
        person.setAttributes(attributes);

        return person;
    }

}
