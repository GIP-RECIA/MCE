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
package fr.recia.mce.api.escomceapi.services.structure;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalStructDao;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Service
@Getter
@Slf4j
public class StructureServiceImpl implements IStructureService {

    @Autowired
    private IExternalStructDao externalStructDao;

    @Autowired
    private CacheManager cacheManager;

    private List<IExternalStructure> allStructures;

    private final Map<String, IExternalStructure> siren2structure = Collections
            .synchronizedMap(new HashMap<String, IExternalStructure>());

    @Override
    public List<IExternalStructure> getAllStructures() {

        String siren;

        if (allStructures == null) {
            allStructures = externalStructDao.loadAllStructure();
        }

        for (IExternalStructure struct : allStructures) {
            siren = struct.getId();
            siren2structure.put(siren, struct);

        }

        return allStructures;
    }

    public boolean isStructureLoaded() {
        return allStructures != null;
    }

    @Override
    public IExternalStructure findStructureBySiren(String siren) {

        if (isStructureLoaded()) {
            log.info("structures exists");

            return siren2structure.get(siren);
        }
        log.info("struct null");
        return null;
    }

}
