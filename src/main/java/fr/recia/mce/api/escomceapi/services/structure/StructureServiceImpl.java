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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.configuration.bean.DomaineProperties;
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

    private final DomaineProperties domaineProperties;

    private List<IExternalStructure> allStructures;

    private final Map<String, IExternalStructure> siren2structure = Collections
            .synchronizedMap(new HashMap<>());

    private final Map<String, IExternalStructure> uai2structure = Collections
            .synchronizedMap(new HashMap<>());

    private final Set<String> setDomaineEtabRecia = new HashSet<>();
    private final Set<String> setIncludeEtabRecia = new HashSet<>();
    private final Set<String> setExcludeEtabRecia = new HashSet<>();

    public StructureServiceImpl(DomaineProperties domaineProperties) {
        this.domaineProperties = domaineProperties;
        setDomaineEtabRecia.addAll(domaineProperties.getGestionRecia());
        setIncludeEtabRecia.addAll(domaineProperties.getGestionInclude());
        setExcludeEtabRecia.addAll(domaineProperties.getGestionExclude());
    }

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
            log.info("Recherche de structure avec le SIREN : {}", siren);

            return siren2structure.get(siren);
        }
        log.warn("Tentative de recherche de structure par SIREN [{}], mais le cache des structures n'est pas encore chargé.", siren);
        return null;
    }

    @Override
    public IExternalStructure findStructureByUai(String uai) {
        if (isStructureLoaded()) {
            log.info("Recherche de structure avec l'UAI : {}", uai);

            return uai2structure.get(uai);
        }
        log.warn("Tentative de recherche de structure par UAI [{}], mais le cache des structures n'est pas encore chargé.", uai);
        return null;
    }

    @Override
    public boolean isReseauRecia(IExternalStructure str) {
        String uaiOrSiren = str.getUai();
        log.info("  └─ isReseauRecia(IExternalStructure) - UAI='{}', domaines de la structure={}",
                uaiOrSiren, (Object) str.getDomaines());

        if (isDomaineRecia(str)) {
            log.info("    ✓ Domaine RECIA détecté");
            if (uaiOrSiren != null && setExcludeEtabRecia.contains(uaiOrSiren)) {
                log.info("    ✗ UAI '{}' dans la liste d'exclusion {} → EXCLU", uaiOrSiren, setExcludeEtabRecia);
                return false;
            }
            log.info("    → ACCEPTÉ (domaine Recia, UAI non exclue)");
            return true;
        }

        if (uaiOrSiren != null && setIncludeEtabRecia.contains(uaiOrSiren)) {
            log.info("    ✓ UAI '{}' dans la liste d'inclusion {} → INCLUS", uaiOrSiren, setIncludeEtabRecia);
            return true;
        }

        log.info("    ✗ Domaine non RECIA ET UAI '{}' pas dans inclusion {}", uaiOrSiren, setIncludeEtabRecia);
        return false;
    }

    @Override
    public boolean isReseauRecia(PersonneDTO p) {

        List<String> uais = p.getExtUser().getAttribute("ESCOUAI");

        log.info("=== isReseauRecia pour uid={} ===", p.getUid());
        log.info("ESCOUAI={}", uais);

        if (uais == null || uais.isEmpty()) {
            log.info("→ Résultat : uid={} N'APPARTIENT PAS au réseau Recia (pas d'UAI)", p.getUid());
            return false;
        }

        int index = 0;
        for (String uai : uais) {
            index++;
            log.info(" [{}/{}] Recherche de la structure pour l'UAI '{}'...", index, uais.size(), uai);
            IExternalStructure str = findStructureByUai(uai);

            if (str == null) {
                log.info("  → Aucune structure trouvée pour l'UAI '{}'", uai);
                continue;
            }

            log.info("  Structure trouvée : id={}, nom='{}', domaines={}",
                    str.getId(), str.getDisplayName(), (Object) str.getDomaines());

            boolean estRecia = isReseauRecia(str);
            log.info("   isReseauRecia(str) pour UAI '{}' = {}", uai, estRecia);

            if (estRecia) {
                log.info("→ Résultat final : uid={} APPARTIENT au réseau Recia (via UAI '{}')", p.getUid(), uai);
                return true;
            }
        }

        log.info("→ Résultat final : uid={} N'APPARTIENT PAS au réseau Recia (aucune UAI n'a matché)", p.getUid());
        return false;
    }

    private boolean isDomaineRecia(IExternalStructure struct) {

        if (struct == null)
            return false;

        if (setDomaineEtabRecia.isEmpty()) {
            log.warn("Erreur de configuration : La propriété 'domaine.gestion-recia' est vide. Impossible de déterminer le statut du réseau Recia pour la structure [id={}].", struct.getId());
            return false;
        }

        String[] domaines = struct.getDomaines();
        if (domaines != null) {
            for (String dom : domaines) {
                if (setDomaineEtabRecia.contains(dom))
                    return true;
            }
        } else {
            log.error("Erreur de cohérence des données : La structure [id={}, nom={}] n'a aucun attribut de domaine défini. (Contexte : isDomaineRecia)", struct.getId(), struct.getDisplayName());
        }

        return false;
    }

}
