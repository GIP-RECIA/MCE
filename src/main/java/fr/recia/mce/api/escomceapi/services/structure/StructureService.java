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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.recia.mce.api.escomceapi.ldap.ConstLDAP;
import fr.recia.mce.api.escomceapi.ldap.LdapStructureDto;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.configuration.bean.DomaineProperties;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapStructDaoImpl;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Service
@Getter
@Slf4j
public class StructureService {

    private final LdapStructDaoImpl externalStructDao;
    private final CacheManager cacheManager;
    private final DomaineProperties domaineProperties;

    private List<LdapStructureDto> allStructures;
    private Map<String, LdapStructureDto> siren2structure;
    private Map<String, LdapStructureDto> uai2structure;

    public StructureService(LdapStructDaoImpl externalStructDao, CacheManager cacheManager, DomaineProperties domaineProperties) {
        this.externalStructDao = externalStructDao;
        this.cacheManager = cacheManager;
        this.domaineProperties = domaineProperties;
    }

    private void loadAllStructures() {
        allStructures = externalStructDao.loadAllStructure();
        siren2structure = new HashMap<>();
        uai2structure = new HashMap<>();
        for (LdapStructureDto struct : allStructures) {
            siren2structure.put(struct.getId(), struct);
            if (struct.getUai() != null) {
                uai2structure.put(struct.getUai(), struct);
            }
        }
    }

    public boolean isStructureLoaded() {
        return allStructures != null;
    }

    public LdapStructureDto findStructureBySiren(String siren) {
        // Premier chargement des structures si elle ne sont pas encore chargées
        if (!isStructureLoaded()) {
            log.warn("Premier chargement du cache des structures, accès via SIREN");
            loadAllStructures();
        }
        log.debug("Recherche de structure avec le SIREN : {}", siren);
        return siren2structure.get(siren);
    }

    public LdapStructureDto findStructureByUai(String uai) {
        // Premier chargement des structures si elle ne sont pas encore chargées
        if (!isStructureLoaded()) {
            log.warn("Premier chargement du cache des structures, accès via UAI");
            loadAllStructures();
        }
        log.debug("Recherche de structure avec l'UAI : {}", uai);
        return uai2structure.get(uai);
    }

    /**
     * Indique si un utilisateur fait parti des établissements gérés par le Recia
     */
    public boolean isReseauRecia(LdapUserDto p) {
        final String uid = p.getId();
        final List<String> uais = p.getAttribute(ConstLDAP.ESCOUAI);
        if (uais == null || uais.isEmpty()) {
            log.debug("uid={} n'appartient au réseau recia (pas d'UAI)", uid);
            return false;
        }

        for (String uai : uais) {
            // 1ère possibilité : UAI étab dans la liste des UAI gérés
            if (domaineProperties.getGestionInclude().contains(uai)) {
                log.debug("UAI {} dans la liste d'inclusion, uid={} appartient au réseau recia", uai, uid);
                return true;
            }

            // 2ème possibilité : domaine étab dans la liste des domaines gérés
            final LdapStructureDto struct = findStructureByUai(uai);
            if(isDomaineRecia(struct)){
                log.debug("uid={} appartient au réseau recia", uid);
                return true;
            }
        }
        log.debug("uid={} n'appartient au réseau recia", uid);
        return false;
    }

    private boolean isDomaineRecia(LdapStructureDto struct) {
        final String[] domaines = struct.getDomaines();
        for (String dom : domaines) {
            if (domaineProperties.getGestionRecia().contains(dom)){
                log.debug("Domaine '{}' dans la liste d'inclusion", dom);
                return true;
            }
        }
        return false;
    }
}
