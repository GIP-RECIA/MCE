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
package fr.recia.mce.api.escomceapi.web.rest;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@RestController
@RequestMapping("/api/personne")
public class FonctionRestController {

    @Autowired
    private FonctionService fonctionService;

    @GetMapping("/fonction/{id}")
    public ResponseEntity<Collection<FonctionDTO>> getFonctionsOfPerson(@PathVariable Long id) {
        Collection<FonctionDTO> fonctions = fonctionService.getAllFonctionOfPersonne(id);
        log.debug("Fonctions de la personne [id={}] : {}", id, fonctions);
        return new ResponseEntity<>(fonctions, HttpStatus.OK);

    }

    @PutMapping("/fonction/{id}/dateFin")
    public ResponseEntity<Void> updateDateFin(@PathVariable Long id, @RequestBody boolean active) {
        log.debug("Mise à jour de l'état (active={}) de la fonction [id={}]", active, id);
        fonctionService.updateDateFin(id, active);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
