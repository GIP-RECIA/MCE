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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.configuration.bean.DomaineProperties;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.ldap.ExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapStructDaoImpl;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Service
@Getter
@Slf4j
public class StructureServiceImpl {

    private final LdapStructDaoImpl externalStructDao;
    private final CacheManager cacheManager;
    private final DomaineProperties domaineProperties;

    private final Set<String> setDomaineEtabRecia = new HashSet<>();
    private final Set<String> setIncludeEtabRecia = new HashSet<>();
    private final Set<String> setExcludeEtabRecia = new HashSet<>();

    private List<ExternalStructure> allStructures;
    private Map<String, ExternalStructure> siren2structure;
    private Map<String, ExternalStructure> uai2structure;

    public StructureServiceImpl(LdapStructDaoImpl externalStructDao, CacheManager cacheManager, DomaineProperties domaineProperties) {
        this.externalStructDao = externalStructDao;
        this.cacheManager = cacheManager;
        this.domaineProperties = domaineProperties;
        setDomaineEtabRecia.addAll(domaineProperties.getGestionRecia());
        setIncludeEtabRecia.addAll(domaineProperties.getGestionInclude());
        setExcludeEtabRecia.addAll(domaineProperties.getGestionExclude());
    }

    private void loadAllStructures() {
        if (allStructures == null) {
            allStructures = externalStructDao.loadAllStructure();
            siren2structure = new HashMap<>();
            uai2structure = new HashMap<>();
            for (ExternalStructure struct : allStructures) {
                siren2structure.put(struct.getId(), struct);
                if (struct.getUai() != null) {
                    uai2structure.put(struct.getUai(), struct);
                }
            }
        }
    }

    public boolean isStructureLoaded() {
        return allStructures != null;
    }

    public ExternalStructure findStructureBySiren(String siren) {
        // Premier chargement des structures si elle ne sont pas encore chargées
        if (!isStructureLoaded()) {
            log.warn("Premier chargement du cache des structures, accès via SIREN");
            loadAllStructures();
        }
        log.debug("Recherche de structure avec le SIREN : {}", siren);
        return siren2structure.get(siren);
    }

    public ExternalStructure findStructureByUai(String uai) {
        // Premier chargement des structures si elle ne sont pas encore chargées
        if (!isStructureLoaded()) {
            log.warn("Premier chargement du cache des structures, accès via UAI");
            loadAllStructures();
        }
        log.debug("Recherche de structure avec l'UAI : {}", uai);
        return uai2structure.get(uai);
    }

    public boolean isReseauRecia(ExternalStructure str) {
        String uaiOrSiren = str.getUai();
        log.debug("  - isReseauRecia(IExternalStructure) - UAI='{}', domaines de la structure={}", uaiOrSiren, (Object) str.getDomaines());

        if (isDomaineRecia(str)) {
            log.debug("    ** Domaine RECIA détecté");
            if (uaiOrSiren != null && setExcludeEtabRecia.contains(uaiOrSiren)) {
                log.debug("    * UAI '{}' dans la liste d'exclusion {} → EXCLU", uaiOrSiren, setExcludeEtabRecia);
                return false;
            }
            log.debug("    - ACCEPTÉ (domaine Recia, UAI non exclue)");
            return true;
        }

        if (uaiOrSiren != null && setIncludeEtabRecia.contains(uaiOrSiren)) {
            log.debug("    UAI '{}' dans la liste d'inclusion {} → INCLUS", uaiOrSiren, setIncludeEtabRecia);
            return true;
        }

        log.debug("    Domaine non RECIA ET UAI '{}' pas dans inclusion {}", uaiOrSiren, setIncludeEtabRecia);
        return false;
    }

    public boolean isReseauRecia(DatabaseUserDto p) {

        // TODO : pass ldap user
//        if (p.getExtUser() == null) {
//            log.debug("- Résultat : uid={} N'APPARTIENT PAS au réseau Recia (extUser null)", p.getUid());
//            return false;
//        }

        //List<String> uais = p.getExtUser().getAttribute("ESCOUAI");
        List<String> uais = null;

        log.debug("=== isReseauRecia pour uid={} ===", p.getUid());
        log.debug("ESCOUAI pour uid={} : {}", p.getUid(), uais);

        if (uais == null || uais.isEmpty()) {
            log.debug("- Résultat : uid={} N'APPARTIENT PAS au réseau Recia (pas d'UAI)", p.getUid());
            return false;
        }

        int index = 0;
        for (String uai : uais) {
            index++;
            if (log.isDebugEnabled()) {
                log.debug(" [{}/{}] Recherche de la structure pour l'UAI '{}' (uid={})...", index, uais.size(), uai, p.getUid());
            }
            ExternalStructure str = findStructureByUai(uai);

            if (str == null) {
                if (log.isDebugEnabled()) {
                    log.debug("  - Aucune structure trouvée pour l'UAI '{}' (uid={})", uai, p.getUid());
                }
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Structure trouvée pour uid={} : id={}, nom='{}', domaines={}",
                        p.getUid(), str.getId(), str.getDisplayName(), (Object) str.getDomaines());
            }

            boolean estRecia = isReseauRecia(str);
            if (log.isDebugEnabled()) {
                log.debug("   isReseauRecia(str) pour UAI '{}' (uid={}) = {}", uai, p.getUid(), estRecia);
            }

            if (estRecia) {
                if (log.isDebugEnabled()) {
                    log.debug("- Résultat final : uid={} APPARTIENT au réseau Recia (via UAI '{}')", p.getUid(), uai);
                }
                return true;
            }
        }

        log.debug("- Résultat final : uid={} N'APPARTIENT PAS au réseau Recia (aucune UAI n'a matché)", p.getUid());
        return false;
    }

    private boolean isDomaineRecia(ExternalStructure struct) {

        if (struct == null)
            return false;

        if (setDomaineEtabRecia.isEmpty()) {
            log.warn(
                    "Erreur de configuration : La propriété 'domaine.gestion-recia' est vide. Impossible de déterminer le statut du réseau Recia pour la structure [id={}].",
                    struct.getId());
            return false;
        }

        String[] domaines = struct.getDomaines();
        if (domaines != null) {
            for (String dom : domaines) {
                if (setDomaineEtabRecia.contains(dom))
                    return true;
            }
        } else {
            log.error("Erreur de cohérence des données : La structure [id={}, nom={}] n'a aucun attribut de domaine défini. (Contexte : isDomaineRecia)",
                    struct.getId(), struct.getDisplayName());
        }

        return false;
    }
}
