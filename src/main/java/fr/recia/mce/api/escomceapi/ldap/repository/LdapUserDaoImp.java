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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Repository;

import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import lombok.extern.slf4j.Slf4j;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import java.util.List;

@Slf4j
@Repository
public class LdapUserDaoImp implements IExternalUserDao {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private ExternalUserHelper externalUserHelper;

    @Override
    public IExternalUser getUserByUid(String uid) {

        final AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));

        if (log.isDebugEnabled()) {
            log.debug("Ldap filter applied: {}", filter);
        }

        ContextMapper<IExternalUser> mapper = new LdapUserContextMapper(this.externalUserHelper);

        LdapQuery query = LdapQueryBuilder.query()
                .attributes(externalUserHelper.getAttributes()
                        .toArray(new String[externalUserHelper.getAttributes().size()]))
                .base(externalUserHelper.getUserDNSubPath()).filter(filter);

        IExternalUser user;

        try {
            user = ldapTemplate.searchForObject(query, mapper);
        } catch (Exception e) {
            user = null;
            log.info("error user null : {}", e);
        }

        return user;
    }



    @Override
    public void updatePassword(String uid, String newHashedPassword) {

        AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));

        LdapQuery query = LdapQueryBuilder.query()
                .base(externalUserHelper.getUserDNSubPath())
                .filter(filter);

        ModificationItem[] mods = new ModificationItem[]{
                new ModificationItem(
                        DirContext.REPLACE_ATTRIBUTE,
                        new BasicAttribute("userPassword", newHashedPassword))
        };

        // ContextMapper pour récupérer le DN réel de l'utilisateur
        ContextMapper<String> dnMapper = ctx -> {
            DirContextAdapter adapter = (DirContextAdapter) ctx;
            return adapter.getDn().toString();
        };

        try {
            List<String> dns = ldapTemplate.search(query, dnMapper);

            if (dns == null || dns.isEmpty()) {
                log.error("Aucun utilisateur LDAP trouvé pour uid: {}", uid);
                throw new RuntimeException("Utilisateur LDAP introuvable : " + uid);
            }

            String dn = dns.get(0);
            log.debug("DN résolu pour uid={} : {}", uid, dn);

            ldapTemplate.modifyAttributes(dn, mods);
            log.info("LDAP password updated for uid: {} at DN: {}", uid, dn);

        } catch (Exception e) {
            log.error("Failed to update LDAP password for uid {}: {}", uid, e.getMessage());
            throw new RuntimeException("LDAP password update failed", e);
        }
    }

}
