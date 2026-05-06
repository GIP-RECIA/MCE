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

import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    @Override
    public IExternalUser getUserByUid(String uid) {

        final AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));

        if (log.isDebugEnabled()) {
            log.debug("Filtre LDAP appliqué : {}", filter);
        }

        ContextMapper<IExternalUser> mapper = new LdapUserContextMapper(this.externalUserHelper);

        LdapQuery query = LdapQueryBuilder.query()
                .attributes(externalUserHelper.getAttributes()
                        .toArray(new String[externalUserHelper.getAttributes().size()]))
                .base(externalUserHelper.getUserDNSubPath()).filter(filter);

        IExternalUser user;

        try {
            user = ldapTemplate.searchForObject(query, mapper);
        } catch (EmptyResultDataAccessException e) {
            specialLog.warn("Audit [GET_USER_BY_ID] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP | Détail : {}", uid, e.getMessage());
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        } catch (Exception e) {
            specialLog.error("Audit [GET_USER_BY_ID] : REFUSÉ pour l'utilisateur [{}] - Raison : Erreur technique lors de la recherche LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("Erreur technique LDAP", e);
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
                specialLog.error("Audit [UPDATE_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP lors de la tentative de mise à jour", uid);
                throw new RuntimeException("Utilisateur LDAP introuvable : " + uid);
            }

            String dn = dns.get(0);
            log.debug("DN résolu pour uid={} : {}", uid, dn);

            ldapTemplate.modifyAttributes(dn, mods);
            log.info("Mot de passe LDAP mis à jour pour l'uid : {} au DN : {}", uid, dn);

        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification de l'attribut LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP password update failed", e);
        }
    }

    @Override
    public void updateEmail(String uid, String newEmail) {
        AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));

        LdapQuery query = LdapQueryBuilder.query()
                .base(externalUserHelper.getUserDNSubPath())
                .filter(filter);

        ModificationItem[] mods = new ModificationItem[]{
                new ModificationItem(
                        DirContext.REPLACE_ATTRIBUTE,
                        new BasicAttribute(externalUserHelper.getUserEmailAttribute(), newEmail))
        };

        ContextMapper<String> dnMapper = ctx -> {
            DirContextAdapter adapter = (DirContextAdapter) ctx;
            return adapter.getDn().toString();
        };

        try {
            List<String> dns = ldapTemplate.search(query, dnMapper);

            if (dns == null || dns.isEmpty()) {
                specialLog.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP lors de la tentative de mise à jour", uid);
                throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
            }

            String dn = dns.get(0);
            log.debug("DN résolu pour uid={} : {}", uid, dn);

            ldapTemplate.modifyAttributes(dn, mods);
            log.info("Email LDAP mis à jour pour l'uid : {} au DN : {} avec le nouvel email : {}", uid, dn, newEmail);

        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_EMAIL] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification de l'attribut LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP email update failed", e);
        }
    }

}
