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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.OrFilter;
import org.springframework.ldap.filter.PresentFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Repository;

import fr.recia.mce.api.escomceapi.ldap.ExternalStructHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class LdapStructDaoImpl implements IExternalStructDao {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private ExternalStructHelper externalStructHelper;

    @Override
    public List<IExternalStructure> loadAllStructure() {

        OrFilter filter = new OrFilter();
        filter.or(new PresentFilter(externalStructHelper.getStructUaiAttribute()));
        filter.or(new EqualsFilter(externalStructHelper.getStructTypeAttribute(), "COLLECTIVITE LOCALE"));
        System.out.println(filter.encode());

        if (log.isDebugEnabled()) {
            log.debug("Ldap filter applied: {}", filter);
        }

        ContextMapper<IExternalStructure> mapper = new LdapStructureContextMapper(this.externalStructHelper);

        LdapQuery query = LdapQueryBuilder.query()
                .attributes(externalStructHelper.getAttributes()
                        .toArray(new String[externalStructHelper.getAttributes().size()]))
                .base(externalStructHelper.getStructDNSubPath()).filter(filter);

        List<IExternalStructure> structs;
        try {
            structs = ldapTemplate.search(query, mapper);
        } catch (Exception e) {
            structs = null;
            log.info("error structs null : {}", e);
        }
        log.info("{} structures found.", structs.size());

        return structs;
    }

}
