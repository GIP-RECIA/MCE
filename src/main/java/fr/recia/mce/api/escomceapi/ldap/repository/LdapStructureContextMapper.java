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

import fr.recia.mce.api.escomceapi.ldap.ConstLDAP;
import fr.recia.mce.api.escomceapi.ldap.LdapStructureDto;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;

import org.springframework.stereotype.Service;

import javax.naming.NamingException;

@Service
public class LdapStructureContextMapper implements ContextMapper<LdapStructureDto> {

    @Override
    public LdapStructureDto mapFromContext(Object ctx) throws NamingException {
        DirContextAdapter context = (DirContextAdapter) ctx;
        LdapStructureDto structs = new LdapStructureDto();
        structs.setId(context.getStringAttribute(ConstLDAP.STRUCT_SIREN));
        structs.setName(context.getStringAttribute(ConstLDAP.STRUCT_NOM_COURANT).replace('$', ' '));
        structs.setDisplayName(context.getStringAttribute(ConstLDAP.STRUCT_NOM_COURANT));
        structs.setUai(context.getStringAttribute(ConstLDAP.STRUCT_UAI));
        structs.setType(context.getStringAttribute(ConstLDAP.STRUCT_TYPE));
        String ville = context.getStringAttribute(ConstLDAP.STRUCT_VILLE);
        structs.setVille(ville != null ? ville.toUpperCase() : null);
        structs.setDomaines(context.getStringAttributes(ConstLDAP.STRUCT_DOMAINES));
        String jointure = context.getStringAttribute(ConstLDAP.STRUCT_JOINTURE);
        if (jointure != null && jointure.contains("$")) {
            structs.setSource(jointure.substring(0, jointure.indexOf('$')));
        }
        return structs;
    }

}
