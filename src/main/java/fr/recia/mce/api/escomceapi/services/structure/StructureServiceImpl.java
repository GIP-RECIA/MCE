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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
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

    private final Map<String, IExternalStructure> uai2structure = Collections
            .synchronizedMap(new HashMap<String, IExternalStructure>());

    @Value("${app.service.custom-params.domaine-etab-recia}")
    private String domaineEtabRecia;

    private Set<String> setDomaineEtabRecia = new HashSet<>();

    @Override
    public List<IExternalStructure> getAllStructures() {

        String siren;
        String uai;

        if (allStructures == null) {
            allStructures = externalStructDao.loadAllStructure();
        }

        for (IExternalStructure struct : allStructures) {
            siren = struct.getId();
            uai = struct.getUai();
            siren2structure.put(siren, struct);

            if (uai != null) {
                uai2structure.put(uai, struct);
            }

        }

        return allStructures;
    }

    public boolean isStructureLoaded() {
        return allStructures != null;
    }

    @Override
    public IExternalStructure findStructureBySiren(String siren) {

        if (isStructureLoaded()) {
            log.info("structures exists : {}", siren);

            return siren2structure.get(siren);
        }
        log.info("struct null");
        return null;
    }

    @Override
    public IExternalStructure findStructureByUai(String uai) {
        if (isStructureLoaded()) {
            log.info("structures with uai exists : {}", uai);

            return uai2structure.get(uai);
        }
        log.info("struct null");
        return null;
    }

    @Override
    public boolean isReseauRecia(IExternalStructure str) {
        String uaiOrSiren = str.getUai();
        if (isDomaineRecia(str)) {
            if (uaiOrSiren != null) {
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean isReseauRecia(PersonneDTO p) {

        List<String> uais = p.getExtUser().getAttribute("ESCOUAI");
        List<String> domPerson = p.getExtUser().getAttribute("ESCODomaines");

        // if (uais != null) {
        // for (String uai : uais) {
        // log.info("uai : {}", uai);
        // IExternalStructure struct = this.findStructureByUai(uai);

        // log.info("struct valeur : {}", struct);

        // if (struct != null && isReseauRecia(struct)) {
        // return true;

        // }
        // }
        // }

        if (domaineEtabRecia.isEmpty()) {
            log.info("Aucun domaine de gestion du réseau etab par le gip définit (domaineEtabRecia)");
            return false;
        }

        if (!domPerson.isEmpty() && !uais.isEmpty()) {
            if (setDomaineEtabRecia.isEmpty()) {
                for (String domaine : domaineEtabRecia.split(" ")) {
                    log.info("domaineRecia : {}", domaine);

                    setDomaineEtabRecia.add(domaine);
                }
            }
            for (String domP : domPerson) {

                if (setDomaineEtabRecia.contains(domP))
                    return true;
            }
        }

        return false;
    }

    private boolean isDomaineRecia(IExternalStructure struct) {

        if (struct == null)
            return false;

        String[] domaines = struct.getDomaines();
        List<String> doms = new ArrayList<>();
        if (domaines != null) {
            for (String string : domaines) {
                doms.add(string);
            }
        }

        if (domaineEtabRecia == null) {
            log.info("Aucun domaine de gestion du réseau etab par le gip définit (domaineEtabRecia)");
            return false;
        }
        if (setDomaineEtabRecia.isEmpty()) {
            for (String domaine : domaineEtabRecia.split(" ")) {
                setDomaineEtabRecia.add(domaine);
            }
        }

        if (doms != null) {

            for (String dom : doms) {
                if (setDomaineEtabRecia.contains(dom))
                    return true;
            }
        } else {
            log.error("Structure sans domaine " + struct.getDisplayName() + " " + struct.getId() + " (437)");
        }

        return false;
    }

    public void setDomaineEtabRecia(String domaineEtabRecia) {

        this.domaineEtabRecia = domaineEtabRecia;
        setDomaineEtabRecia.clear();
        if (domaineEtabRecia != null) {
            for (String domaine : domaineEtabRecia.split(" ")) {
                setDomaineEtabRecia.add(domaine);
            }
        }
        this.domaineEtabRecia = setDomaineEtabRecia.isEmpty() ? null : domaineEtabRecia;

    }

}
