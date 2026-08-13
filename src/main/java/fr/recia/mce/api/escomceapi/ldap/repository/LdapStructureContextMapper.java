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

import javax.naming.NamingException;

import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;

import fr.recia.mce.api.escomceapi.ldap.ExternalStructHelper;
import fr.recia.mce.api.escomceapi.ldap.ExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;

public class LdapStructureContextMapper implements ContextMapper<IExternalStructure> {

    private ExternalStructHelper externalStructHelper;

    public LdapStructureContextMapper(ExternalStructHelper externalStructHelper) {
        super();
        this.externalStructHelper = externalStructHelper;
    }

    @Override
    public IExternalStructure mapFromContext(Object ctx) throws NamingException {

        DirContextAdapter context = (DirContextAdapter) ctx;
        ExternalStructure structs = new ExternalStructure();

        structs.setId(context.getStringAttribute(externalStructHelper.getStructIdAttribute()));

        structs.setName(context.getStringAttribute(externalStructHelper.getStructNameAttribute()).replace('$', ' '));

        structs.setDisplayName(context.getStringAttribute(externalStructHelper.getStructDisplayNameAttribute()));

        structs.setUai(context.getStringAttribute(externalStructHelper.getStructUaiAttribute()));

        structs.setType(context.getStringAttribute(externalStructHelper.getStructTypeAttribute()));

        structs.setDomaines(context.getStringAttributes(externalStructHelper.getStructDomainesAttribute()));

        return structs;
    }

}
