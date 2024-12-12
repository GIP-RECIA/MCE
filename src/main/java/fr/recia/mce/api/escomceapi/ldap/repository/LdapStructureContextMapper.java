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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;

import com.google.common.collect.Lists;

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
        Map<String, List<String>> attrs = new HashMap<>();
        ExternalStructure structs = new ExternalStructure();

        structs.setId(context.getStringAttribute(externalStructHelper.getStructIdAttribute()));
        attrs.put(externalStructHelper.getStructIdAttribute(), Lists.newArrayList(structs.getId()));

        structs.setName(context.getStringAttribute(externalStructHelper.getStructNameAttribute()).replace('$', ' '));
        attrs.put(externalStructHelper.getStructNameAttribute(), Lists.newArrayList(structs.getName()));

        structs.setDisplayName(context.getStringAttribute(externalStructHelper.getStructDisplayNameAttribute()));
        attrs.put(externalStructHelper.getStructDisplayNameAttribute(), Lists.newArrayList(structs.getDisplayName()));

        structs.setUai(context.getStringAttribute(externalStructHelper.getStructUaiAttribute()));
        attrs.put(externalStructHelper.getStructUaiAttribute(), Lists.newArrayList(structs.getUai()));

        structs.setType(context.getStringAttribute(externalStructHelper.getStructTypeAttribute()));
        attrs.put(externalStructHelper.getStructTypeAttribute(), Lists.newArrayList(structs.getType()));

        // structs.setAttributes(attrs);

        return structs;
    }

}
