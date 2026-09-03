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
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@RestController
@RequestMapping("/api/personne")
public class FonctionRestController {

    private static final String GUEST_USER = "guest";

    @Autowired
    private FonctionService fonctionService;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private IRelationEleveService relationEleveService;

    @Autowired
    private SoffitHolder soffitHolder;

    @GetMapping("/fonction/{id}")
    public ResponseEntity<Collection<FonctionDTO>> getFonctionsOfPerson(@PathVariable Long id) {
        String currentUid = getCurrentUid();
        if (!canAccessFunctionProfile(currentUid, id)) {
            log.warn("Audit [GET_FONCTIONS] : Tentative d'accès non autorisé aux fonctions de la personne id={} par [{}]", id, currentUid);
            throw new AccessDeniedException("Vous ne pouvez consulter que vos fonctions ou celles des personnes en relation avec vous");
        }
        Collection<FonctionDTO> fonctions = fonctionService.getAllFonctionOfPersonne(id);
        log.debug("Fonctions de la personne [id={}] : {}", id, fonctions);
        return new ResponseEntity<>(fonctions, HttpStatus.OK);

    }

    @PutMapping("/fonction/{id}/dateFin")
    public ResponseEntity<Void> updateDateFin(@PathVariable Long id, @RequestBody boolean active) {
        String currentUid = getCurrentUid();
        if (!canEditFunction(currentUid, id)) {
            log.warn("Audit [UPDATE_DATEFIN] : Tentative de modification non autorisée de la fonction id={} par [{}]", id, currentUid);
            throw new AccessDeniedException("Vous ne pouvez modifier que vos propres fonctions");
        }
        log.debug("Mise à jour de l'état (active={}) de la fonction [id={}]", active, id);
        fonctionService.updateDateFin(id, active);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private String getCurrentUid() {
        String sub = soffitHolder.getSub();
        if (sub == null || sub.isBlank() || GUEST_USER.equals(sub)) {
            throw new AccessDeniedException("Utilisateur non authentifié");
        }
        return sub;
    }

    /**
     * Lecture des fonctions : autorisée pour soi-même ou pour toute personne en relation.
     *
     * @param currentUid uid du connecté
     * @param personId   id personne (base) dont on demande les fonctions
     */
    private boolean canAccessFunctionProfile(String currentUid, Long personId) {
        try {
            PersonneDTO current = personneService.retrievePersonnebyUid(currentUid);
            if (current == null || current.getAPersonneBase() == null) {
                return false;
            }
            Long selfPersonId = current.getAPersonneBase().getId();
            if (selfPersonId.equals(personId)) {
                return true;
            }
            Set<Long> relationPersonIds = new HashSet<>();
            Collection<RelationEleveContact> relations = relationEleveService.allRelationEleves(currentUid);
            if (relations != null) {
                relations.stream()
                        .map(RelationEleveContact::getUidRelation)
                        .filter(java.util.Objects::nonNull)
                        .forEach(uid -> relationPersonIds.addAll(personIdsOfUid(uid)));
            }
            Collection<RelationEleveContact> enRelation = relationEleveService.allEleveEnRelation(selfPersonId);
            if (enRelation != null) {
                enRelation.stream()
                        .map(RelationEleveContact::getUidRelation)
                        .filter(java.util.Objects::nonNull)
                        .forEach(uid -> relationPersonIds.addAll(personIdsOfUid(uid)));
            }
            return relationPersonIds.contains(personId);
        } catch (Exception e) {
            log.warn("Erreur lors du contrôle d'accès aux fonctions id={} par {} : {}", personId, currentUid, e.getMessage());
            return false;
        }
    }

    /**
     * Mise à jour de la date de fin : autorisée uniquement pour ses propres fonctions.
     *
     * @param currentUid uid du connecté
     * @param fonctionId id de fonction (AFonction) à modifier
     */
    private boolean canEditFunction(String currentUid, Long fonctionId) {
        try {
            PersonneDTO current = personneService.retrievePersonnebyUid(currentUid);
            if (current == null || current.getAPersonneBase() == null) {
                return false;
            }
            Long selfPersonId = current.getAPersonneBase().getId();
            Long ownerPersonId = fonctionService.getPersonIdOfFonction(fonctionId);
            return selfPersonId.equals(ownerPersonId);
        } catch (Exception e) {
            log.warn("Erreur lors du contrôle de modification de la fonction id={} par {} : {}", fonctionId, currentUid, e.getMessage());
            return false;
        }
    }

    private Set<Long> personIdsOfUid(String uid) {
        Set<Long> ids = new HashSet<>();
        try {
            PersonneDTO p = personneService.retrievePersonnebyUid(uid);
            if (p != null && p.getAPersonneBase() != null) {
                ids.add(p.getAPersonneBase().getId());
            }
        } catch (Exception e) {
            log.debug("Impossible de résoudre l'uid {} pour le contrôle d'accès aux fonctions : {}", uid, e.getMessage());
        }
        return ids;
    }

}
