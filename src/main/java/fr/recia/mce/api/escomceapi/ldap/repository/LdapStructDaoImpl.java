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

import java.util.List;

import fr.recia.mce.api.escomceapi.ldap.ConstLDAP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.OrFilter;
import org.springframework.ldap.filter.PresentFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Repository;

import fr.recia.mce.api.escomceapi.ldap.LdapStructureDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class LdapStructDaoImpl {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private LdapStructureContextMapper ldapStructureContextMapper;

    public List<LdapStructureDto> loadAllStructure() {
        OrFilter filter = new OrFilter();
        filter.or(new PresentFilter(ConstLDAP.STRUCT_UAI));
        // TODO : string en dur
        filter.or(new EqualsFilter(ConstLDAP.STRUCT_TYPE, "COLLECTIVITE LOCALE"));

        LdapQuery query = LdapQueryBuilder.query().base(ConstLDAP.STRUCTURE_BRANCH).filter(filter);
        try {
            List<LdapStructureDto> structs = ldapTemplate.search(query, ldapStructureContextMapper);
            log.debug("Recherche LDAP réussie : {} structures trouvées avec le filtre [{}]", structs.size(), filter);
            return structs;
        } catch (Exception e) {
            log.error("Échec du chargement des structures depuis LDAP - Raison : Erreur technique lors de la recherche | Détail : {}", e.getMessage());
            return null;
        }
    }

}
