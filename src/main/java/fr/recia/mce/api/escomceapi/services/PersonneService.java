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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PersonneService {

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private LdapUserDaoImp userLdapDao;

    public PersonneDTO getPersonneByUid(String uid) {
        log.info("uid: {}", uid);
        PersonneDTO personne = aPersonneRepository.getPersonneByUid(uid);

        log.info("getPersonne : {}", personne);
        return personne;
    }

    public IExternalUser getPersonLdap(String uid) {
        return userLdapDao.getUserByUid(uid);

    }

}
