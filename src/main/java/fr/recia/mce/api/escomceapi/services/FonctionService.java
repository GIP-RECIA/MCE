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

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.entities.AFonction;
import fr.recia.mce.api.escomceapi.db.repositories.AFonctionRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FonctionService {

    @Autowired
    private FonctionRepository fonctionRepository;

    @Autowired
    private AFonctionRepository aFonctionRepository;

    @Autowired
    private IStructureService structureService;

    public Collection<FonctionDTO> getAllFonctionOfPersonne(Long id) {
        log.debug("Récupération des fonctions pour l'ID personne : {}", id);
        Collection<FonctionDTO> foncts = fonctionRepository.findAllFonction(id);

        for (FonctionDTO f : foncts) {
            if (f.getStruct() == null) {
                f.setStruct(structureService.findStructureBySiren(f.getSiren()));
            }
        }
        log.debug("{} fonction(s) trouvée(s) pour l'ID personne : {}", foncts.size(), id);
        return foncts;
    }

    public Long getPersonIdOfFonction(Long fonctionId) {
        return aFonctionRepository.findById(fonctionId)
                .map(aFonction -> aFonction.getAPersonne() == null ? null : aFonction.getAPersonne().getId())
                .orElse(null);
    }

    public void updateDateFin(Long id, boolean active) {
        // TODO: une nouvelle colonne sera ajoutée en BDD pour la table afonction (à mettre à jour manuellement)
        log.debug("Mise à jour de l'état de la fonction (active={}) pour l'ID : {}", active, id);
        AFonction aFonction = aFonctionRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Impossible de mettre à jour la fonction : fonction introuvable avec l'ID : {}", id);
                    return new PersonneNotFoundException("Fonction non trouvée avec l'id : " + id);
                });
        if (active) {
            aFonction.setDateFin(null);
        } else {
            aFonction.setDateFin(new java.util.Date());
        }
        aFonction.setDateModification(new java.util.Date());
        aFonctionRepository.save(aFonction);
        log.info("État de la fonction mis à jour avec succès pour l'ID : {}", id);
    }

}
