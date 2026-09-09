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

import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.OrFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Repository;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import java.util.Collection;
import java.util.Collections;
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
                        .toArray(new String[0]))
                .base(externalUserHelper.getUserDNSubPath()).filter(filter);

        IExternalUser user;

        try {
            user = ldapTemplate.searchForObject(query, mapper);
        } catch (EmptyResultDataAccessException e) {
            log.warn("Audit [GET_USER_BY_ID] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP | Détail : {}", uid,
                    e.getMessage());
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        } catch (Exception e) {
            log.error("Audit [GET_USER_BY_ID] : REFUSÉ pour l'utilisateur [{}] - Raison : Erreur technique lors de la recherche LDAP | Détail : {}", uid,
                    e.getMessage());
            throw new RuntimeException("Erreur technique LDAP", e);
        }

        return user;
    }

    @Override
    public List<IExternalUser> getByUids(Collection<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyList();
        }

        OrFilter orFilter = new OrFilter();
        for (String uid : uids) {
            orFilter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));
        }

        if (log.isDebugEnabled()) {
            log.debug("Filtre LDAP batch appliqué : {}", orFilter);
        }

        ContextMapper<IExternalUser> mapper = new LdapUserContextMapper(this.externalUserHelper);

        LdapQuery query = LdapQueryBuilder.query()
                .attributes(externalUserHelper.getAttributes()
                        .toArray(new String[0]))
                .base(externalUserHelper.getUserDNSubPath()).filter(orFilter);

        try {
            return ldapTemplate.search(query, mapper);
        } catch (Exception e) {
            log.error("Erreur lors de la recherche LDAP batch pour les uids={} - Détail : {}", uids, e.getMessage());
            throw new RuntimeException("Erreur technique LDAP lors de la recherche batch", e);
        }
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

        List<String> dns;
        try {
            dns = ldapTemplate.search(query, dnMapper);
        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la recherche LDAP | Détail : {}",
                    uid, e.getMessage());
            throw new RuntimeException("LDAP password update failed: " + e.getMessage());
        }

        if (dns == null || dns.isEmpty()) {
            specialLog.error(
                    "Audit [UPDATE_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP lors de la tentative de mise à jour",
                    uid);
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        }

        String dn = dns.get(0);
        log.debug("DN résolu pour uid={} : {}", uid, dn);

        try {
            ldapTemplate.modifyAttributes(dn, mods);
            specialLog.info("Audit [UPDATE_PASSWORD] : SUCCÈS pour l'utilisateur [{}]", uid);
        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification de l'attribut LDAP | Détail : {}",
                    uid, e.getMessage());
            throw new RuntimeException("LDAP password update failed: " + e.getMessage());
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

        List<String> dns;
        try {
            dns = ldapTemplate.search(query, dnMapper);
        } catch (Exception e) {
            log.error("Audit [UPDATE_EMAIL] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la recherche LDAP | Détail : {}", uid,
                    e.getMessage());
            throw new RuntimeException("LDAP email update failed: " + e.getMessage());
        }

        if (dns == null || dns.isEmpty()) {
            log.error(
                    "Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP lors de la tentative de mise à jour",
                    uid);
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        }

        String dn = dns.get(0);
        log.debug("DN résolu pour uid={} : {}", uid, dn);

        try {
            ldapTemplate.modifyAttributes(dn, mods);
            log.debug("Email LDAP mis à jour pour uid={} au DN={} avec le nouvel email={}", uid, dn, newEmail);
        } catch (Exception e) {
            log.error("Audit [UPDATE_EMAIL] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification de l'attribut LDAP | Détail : {}", uid,
                    e.getMessage());
            throw new RuntimeException("LDAP email update failed: " + e.getMessage());
        }
    }

    @Override
    public void updateAvatarLDAP(String uid, String newAvatarUrl) {        AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));

        LdapQuery query = LdapQueryBuilder.query()
                .base(externalUserHelper.getUserDNSubPath())
                .filter(filter);

        ModificationItem[] mods = new ModificationItem[]{
                new ModificationItem(
                        DirContext.REPLACE_ATTRIBUTE,
                        new BasicAttribute(externalUserHelper.getUserAvatarAttribute(), newAvatarUrl))
        };

        ContextMapper<String> dnMapper = ctx -> {
            DirContextAdapter adapter = (DirContextAdapter) ctx;
            return adapter.getDn().toString();
        };

        List<String> dns;
        try {
            dns = ldapTemplate.search(query, dnMapper);
        } catch (Exception e) {
            log.error("Audit [UPDATE_AVATAR] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la recherche LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP avatar update failed: " + e.getMessage());
        }

        if (dns == null || dns.isEmpty()) {
            log.error("Audit [UPDATE_AVATAR] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP", uid);
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        }

        String dn = dns.get(0);
        log.debug("DN résolu pour uid={} : {}", uid, dn);

        try {
            ldapTemplate.modifyAttributes(dn, mods);
            log.debug("Attribut LDAP {} mis à jour pour uid={} au DN={} avec la valeur={}",
                    externalUserHelper.getUserAvatarAttribute(), uid, dn, newAvatarUrl);
        } catch (Exception e) {
            log.error("Audit [UPDATE_AVATAR] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP avatar update failed: " + e.getMessage());
        }
    }

    @Override
    public void updateEtatCompte(String uid, String etat) {
        AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(externalUserHelper.getUserIdAttribute(), uid));

        LdapQuery query = LdapQueryBuilder.query()
                .base(externalUserHelper.getUserDNSubPath())
                .filter(filter);

        ModificationItem[] mods = new ModificationItem[]{
                new ModificationItem(
                        DirContext.REPLACE_ATTRIBUTE,
                        new BasicAttribute(externalUserHelper.getUserEtatCompteAttribute(), etat))
        };

        ContextMapper<String> dnMapper = ctx -> {
            DirContextAdapter adapter = (DirContextAdapter) ctx;
            return adapter.getDn().toString();
        };

        List<String> dns;
        try {
            dns = ldapTemplate.search(query, dnMapper);
        } catch (Exception e) {
            log.error("Audit [UPDATE_ETAT_COMPTE] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la recherche LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP etat compte update failed: " + e.getMessage());
        }

        if (dns == null || dns.isEmpty()) {
            log.error("Audit [UPDATE_ETAT_COMPTE] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP", uid);
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        }

        String dn = dns.get(0);
        log.debug("DN résolu pour uid={} : {}", uid, dn);

        try {
            ldapTemplate.modifyAttributes(dn, mods);
            log.info("Audit [UPDATE_ETAT_COMPTE] : SUCCÈS pour l'utilisateur [{}] - état={}", uid, etat);
        } catch (Exception e) {
            log.error("Audit [UPDATE_ETAT_COMPTE] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification de l'attribut LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP etat compte update failed: " + e.getMessage());
        }
    }
}
