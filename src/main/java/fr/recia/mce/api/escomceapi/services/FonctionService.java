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
        log.debug("person_id: {}", id);
        Collection<FonctionDTO> foncts = fonctionRepository.findAllFonction(id);

        for (FonctionDTO f : foncts) {
            if (f.getStruct() == null) {
                f.setStruct(structureService.findStructureBySiren(f.getSiren()));
            }
        }

        return foncts;
    }

    public void updateDateFin(Long id, boolean active) {
        AFonction aFonction = aFonctionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fonction not found with id: " + id));
        if (active) {
            aFonction.setDateFin(null);
        } else {
            aFonction.setDateFin(new java.util.Date());
        }
        aFonction.setDateModification(new java.util.Date());
        aFonctionRepository.save(aFonction);
    }

}
