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
package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

@Service
@Getter
@Slf4j
public class PersonneService {

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private LdapUserDaoImp userLdapDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private transient IExternalUserDao extDao;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    @Cacheable(cacheNames = "personneDBCache", key = "#uid")
    public PersonneDTO getUserByUid(String uid) {
        log.debug("Recherche base de données : récupération de PersonneDTO pour l'uid [{}]", uid);

        PersonneDTO personne = aPersonneRepository.getPersonneByUid(uid);

        if (personne != null) {
            loadLdapUser(personne, uid);
        }

        log.debug("Recherche base de données terminée pour l'uid [{}] : Résultat={}", uid, personne);
        return personne;
    }

    @Cacheable(cacheNames = "personneLDAPCache", key = "#uid")
    private IExternalUser getUserLdap(String uid) {
        IExternalUser userLdap = null;

        Cache cache = cacheManager.getCache("personneLDAPCache");

        IExternalUser getUser = cache.get(uid, IExternalUser.class);
        if (!Objects.isNull(getUser)) {
            log.debug("Cache hit pour 'personneLDAPCache' : Données LDAP chargées pour l'uid [{}]", uid);
            return getUser;
        }

        try {
            log.debug("Cache miss pour 'personneLDAPCache' : Récupération des données LDAP depuis l'annuaire pour l'uid [{}]", uid);
            userLdap = getExtDao().getUserByUid(uid);
            cache.putIfAbsent(uid, userLdap);

        } catch (Exception e) {
            log.error("Échec du chargement des données LDAP pour l'uid [{}] - Détail : {}", uid, e.getMessage());

        }
        return userLdap;

    }

    private void loadLdapUser(PersonneDTO personne, String uid) {
        IExternalUser u = getUserLdap(uid);
        personne.setExtUser(u);
    }

    public PersonneDTO retrievePersonnebyUid(String uid) {
        return this.getUserByUid(uid);
    }

    public IExternalUser retrievePersonLdap(String uid) {
        log.debug("retrievePersonLdap : {}", uid);
        return getUserLdap(uid);

    }

    @Transactional
    public void updateEmail(String uid, String newEmail) {
        log.info("Mise à jour de l'email pour l'utilisateur [uid={}] vers : {}", uid, newEmail);

        // 1. Update in Database
        PersonneDTO personneDTO = aPersonneRepository.getPersonneByUid(uid);
        if (personneDTO == null) {
            specialLog.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable en base de données", uid);
            throw new PersonneNotFoundException("Utilisateur introuvable en base : " + uid);
        }

        APersonne entity = personneDTO.getApersonne();
        entity.setEmail(newEmail);
//        entity.setEmailPersonnel(newEmail);
        entity.setDateModification(new Date());

        aPersonneRepository.saveAndFlush(entity);
        log.debug("Email mis à jour en base de données pour l'uid : {}", uid);

        // 2. Update in LDAP
        try {
            getExtDao().updateEmail(uid, newEmail);
            log.debug("Email mis à jour dans l'annuaire LDAP pour l'uid : {}", uid);
        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Échec de la mise à jour LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("Erreur lors de la mise à jour de l'email dans l'annuaire", e);
        }

        // 3. Clear Caches
        clearUserCaches(uid);

        specialLog.info("Audit [UPDATE_EMAIL] : SUCCÈS pour l'utilisateur [{}]", uid);
    }

    private void clearUserCaches(String uid) {
        Cache dbCache = cacheManager.getCache("personneDBCache");
        if (dbCache != null) {
            dbCache.evict(uid);
        }
        Cache ldapCache = cacheManager.getCache("personneLDAPCache");
        if (ldapCache != null) {
            ldapCache.evict(uid);
        }
    }

}
