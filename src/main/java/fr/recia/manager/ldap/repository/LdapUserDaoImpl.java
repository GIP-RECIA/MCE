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
package fr.recia.manager.ldap.repository;

import fr.recia.manager.configuration.cache.CacheNames;
import fr.recia.manager.ldap.ConstLDAP;
import fr.recia.manager.ldap.LdapUserDto;
import fr.recia.manager.services.exception.PersonneNotFoundException;
import fr.recia.manager.services.logging.Loggers;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.OrFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Repository;

import javax.naming.Name;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Repository
public class LdapUserDaoImpl {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private LdapUserContextMapper ldapUserContextMapper;

    @Autowired
    private CacheManager cacheManager;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    /**
     * Charge un utilisateur depuis le LDAP
     */
    public LdapUserDto getUserLdap(String uid) {
        // Récupération dans le cache si possible
        LdapUserDto userLdap = null;
        Cache cache = cacheManager.getCache(CacheNames.LDAP_PERSONNE_CACHE);
        if (cache != null) {
            userLdap = cache.get(uid, LdapUserDto.class);
            if (!Objects.isNull(userLdap)) {
                log.debug("Cache hit pour 'personneLDAPCache' : Données LDAP chargées pour l'uid [{}]", uid);
                return userLdap;
            }
        }

        // Sinon on requête le LDAP et on met dans le cache
        try {
            log.debug("Cache miss pour 'personneLDAPCache' : Récupération des données LDAP depuis l'annuaire pour l'uid [{}]", uid);
            userLdap = getUserByUid(uid);
            if (cache != null) {
                cache.putIfAbsent(uid, userLdap);
            }
        } catch (Exception e) {
            log.error("Échec du chargement des données LDAP pour l'uid [{}] - Détail : {}", uid, e.getMessage());
        }
        return userLdap;
    }

    /**
     * Charge un liste d'utilisateurs à partir de leurs uids
     */
    private LdapUserDto getUserByUid(String uid) {
        final AndFilter filter = new AndFilter();
        filter.append(new EqualsFilter(ConstLDAP.UID, uid));
        log.debug("Filtre LDAP appliqué : {}", filter);
        LdapQuery query = LdapQueryBuilder.query().base(ConstLDAP.PEOPLE_BRANCH).filter(filter);
        try {
            return ldapTemplate.searchForObject(query, ldapUserContextMapper);
        } catch (EmptyResultDataAccessException e) {
            log.warn("Audit [GET_USER_BY_ID] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable dans l'annuaire LDAP | Détail : {}", uid, e.getMessage());
            throw new PersonneNotFoundException("Utilisateur LDAP introuvable : " + uid);
        } catch (Exception e) {
            log.error("Audit [GET_USER_BY_ID] : REFUSÉ pour l'utilisateur [{}] - Raison : Erreur technique lors de la recherche LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("Erreur technique LDAP", e);
        }
    }

    /**
     * Charge une liste d'utilisateurs à partir de leurs uids
     */
    public List<LdapUserDto> getByUids(Collection<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyList();
        }
        OrFilter orFilter = new OrFilter();
        for (String uid : uids) {
            orFilter.append(new EqualsFilter(ConstLDAP.UID, uid));
        }
        log.debug("Filtre LDAP batch appliqué : {}", orFilter);
        LdapQuery query = LdapQueryBuilder.query().base(ConstLDAP.PEOPLE_BRANCH).filter(orFilter);
        try {
            return ldapTemplate.search(query, ldapUserContextMapper);
        } catch (Exception e) {
            log.error("Erreur lors de la recherche LDAP batch pour les uids={} - Détail : {}", uids, e.getMessage());
            throw new RuntimeException("Erreur technique LDAP lors de la recherche batch", e);
        }
    }

    /**
     * Met à jour les différents mots de passe (normal et réseau) dans le LDAP
     */
    public void modifEtatLdapPassword(String uid, String ldapHash, String sambaLm, String sambaNt) {
        List<ModificationItem> mods = new ArrayList<>();
        mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(ConstLDAP.USER_PASSWORD, ldapHash.getBytes(StandardCharsets.UTF_8))));
        // samba : écrits uniquement si les deux hashes sont fournis (cohérence pair LM/NT, équivalent legacy)
        if (sambaLm != null && sambaNt != null) {
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(ConstLDAP.SAMBA_LM_PASSWORD, sambaLm)));
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(ConstLDAP.SAMBA_NT_PASSWORD, sambaNt)));
        }
        try {
            final Name dn = LdapNameBuilder.newInstance(ConstLDAP.PEOPLE_BRANCH).add(ConstLDAP.UID, uid).build();
            ldapTemplate.modifyAttributes(dn, mods.toArray(ModificationItem[]::new));
            specialLog.info("Audit [MODIF_ETAT_LDAP_PASSWORD] : SUCCÈS pour l'utilisateur [{}]", uid);
        } catch (Exception e) {
            specialLog.error("Audit [MODIF_ETAT_LDAP_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification de l'attribut LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP password/etat update failed: " + e.getMessage());
        }
    }

    /**
     * Met à jour l'url de l'avatar dans le LDAP
     */
    public void updateAvatarLDAP(String uid, String newAvatarUrl) {
        ModificationItem[] mods = new ModificationItem[]{ new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(ConstLDAP.PHOTO, newAvatarUrl)) };
        try {
            final Name dn = LdapNameBuilder.newInstance(ConstLDAP.PEOPLE_BRANCH).add(ConstLDAP.UID, uid).build();
            ldapTemplate.modifyAttributes(dn, mods);
            log.debug("Attribut LDAP {} mis à jour pour uid={} au DN={} avec la valeur={}", ConstLDAP.PHOTO, uid, dn, newAvatarUrl);
        } catch (Exception e) {
            log.error("Audit [UPDATE_AVATAR] : REFUSÉ pour l'utilisateur [{}] - Raison : Échec de la modification LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("LDAP avatar update failed: " + e.getMessage());
        }
    }

}
