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

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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

    // public PersonneDTO getPersonneByUid(String uid) {
    // log.info("uid: {}", uid);
    // PersonneDTO personne = aPersonneRepository.getPersonneByUid(uid);

    // log.info("getPersonne : {}", personne);
    // return personne;
    // }

    // public IExternalUser getPersonLdap(String uid) {
    // return userLdapDao.getUserByUid(uid);

    // }

    @Cacheable(cacheNames = "personneDBCache", key = "#uid")
    private PersonneDTO getUserByUid(String uid) {
        log.info("uid: {}", uid);
        PersonneDTO personne = null;

        Cache cache = cacheManager.getCache("personneDBCache");
        PersonneDTO getPersonne = cache.get(uid, PersonneDTO.class);
        if (!Objects.isNull(getPersonne)) {
            log.info("Loading personneDB cache for user {}...", uid);
            return getPersonne;
        }

        try {
            log.info("Calcul personDB");
            personne = aPersonneRepository.getPersonneByUid(uid);
            cache.putIfAbsent(uid, personne);

            if (personne != null) {
                loadLdapUser(personne, uid);
            }

        } catch (Exception e) {
            log.error("error : {}", e);
        }

        log.info("getPersonne : {}", personne);
        return personne;
    }

    @Cacheable(cacheNames = "personneLDAPCache", key = "#uid")
    private IExternalUser getUserLdap(String uid) {
        IExternalUser userLdap = null;

        Cache cache = cacheManager.getCache("personneLDAPCache");

        IExternalUser getUser = cache.get(uid, IExternalUser.class);
        if (!Objects.isNull(getUser)) {
            log.info("Loading personneLDAP cache for user {}...", uid);
            return getUser;
        }

        try {
            log.info("Calcul personLDAP");
            userLdap = getExtDao().getUserByUid(uid);
            cache.putIfAbsent(uid, userLdap);

        } catch (Exception e) {
            log.error("error : {}", e);

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
        log.info("retrievePersonLdap: {}", uid);
        return getUserLdap(uid);

    }

}
