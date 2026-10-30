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

package fr.recia.mce.api.escomceapi.db.services;

import fr.recia.mce.api.escomceapi.configuration.cache.CacheNames;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseUserService {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private APersonneRepository aPersonneRepository;

    /**
     * Charge un utilisateur depuis la base de données
     */
    public DatabaseUserDto getUserDatabase(String uid) {
        // Récupération dans le cache si possible
        Cache cache = cacheManager.getCache(CacheNames.DATABASE_PERSONNE_CACHE);
        if (cache != null) {
            DatabaseUserDto cached = cache.get(uid, DatabaseUserDto.class);
            if (cached != null) {
                log.debug("Cache hit pour 'personneDBCache' : Données DB chargées pour l'uid [{}]", uid);
                return cached;
            }
        }

        // Sinon on requête la BDD et on met dans le cache
        log.debug("Cache miss pour 'personneDBCache' : Récupération des données DB pour l'uid [{}]", uid);
        DatabaseUserDto personne = aPersonneRepository.getPersonneByUid(uid);
        if (personne != null) {
            if (cache != null) {
                cache.putIfAbsent(uid, personne);
            }
        }

        return personne;
    }

}
