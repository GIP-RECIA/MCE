/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.recia.mce.api.escomceapi.web.rest;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/personne/mce")
public class PersonneRestController {

    @Autowired
    private PersonneService personneService;

    @Autowired
    private IUserDTOFactory userDTOFactory;

    @Autowired
    private SoffitHolder soffitHolder;

    /**
     * Récupère l'UID de l'utilisateur actuellement connecté
     * GET /api/personne/mce/id
     */
    @GetMapping("/id")
    public ResponseEntity<String> getCurrentUserId() {
        String uid = getCurrentUid();

        if (uid == null || uid.isBlank()) {
            log.warn("Aucun uid trouvé dans le SoffitHolder");
            return new ResponseEntity<>("Aucun utilisateur authentifié", HttpStatus.UNAUTHORIZED);
        }

        log.debug("UID demandé : {}", uid);
        return ResponseEntity.ok(uid);
    }

    /**
     * Récupère le PersonneDTO de l'utilisateur connecté
     * GET /api/personne/mce/getuser
     */
    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid() {
        String uid = getCurrentUid();

        if (uid == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.debug("Récupération de la personne pour uid={}", uid);
        PersonneDTO personne = personneService.retrievePersonnebyUid(uid);

        if (personne == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        log.info("Personne trouvée : {}", personne);
        return new ResponseEntity<>(personne, HttpStatus.OK);
    }

    /**
     * Récupère la fiche LDAP brute de l'utilisateur connecté
     * GET /api/personne/mce/ldap
     */
    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap() {
        String uid = getCurrentUid();
        if (uid == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        IExternalUser personne = personneService.retrievePersonLdap(uid);
        if (personne == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(personne, HttpStatus.OK);
    }

    /**
     * Retourne le UserDTO complet de l'utilisateur connecté
     * GET /api/personne/mce/
     */
    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE() {

        UserDTO user = userDTOFactory.getCurrentUser();

        log.info("userDTO: {}", user);
        if (user == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(user, HttpStatus.OK);

    }

    /**
     * Retourne le UserDTO d'un enfant/élève par son identifiant
     * GET /api/personne/mce/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String id) {
        UserDTO enfant = userDTOFactory.from(id);
        if (enfant == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(enfant, HttpStatus.OK);
    }

    /**
     * Change le mot de passe de l'utilisateur connecté
     * POST /api/personne/mce/{uid}/change-password
     */
    @PostMapping("/{uid}/change-password")
    public ResponseEntity<String> changePass(
            @PathVariable String uid,
            @Valid @RequestBody PasswordChangeRequest request) {

        String currentUid = getCurrentUid();
        if (currentUid == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        if (!currentUid.equals(uid)) {
            log.warn("L'utilisateur {} a tenté de modifier le mot de passe de {}", currentUid, uid);
            return new ResponseEntity<>("Action non autorisée.", HttpStatus.FORBIDDEN);
        }

        log.info("Changement de mot de passe demandé pour uid={}", uid);

        try {
            String result = userDTOFactory.changePassword(uid, request);
            log.info("Résultat du changement de mot de passe pour uid={} : {}", uid, result);


            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("Argument invalide pour uid={} : {}", uid, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors du changement de mot de passe pour uid={}", uid, e);
            return new ResponseEntity<>("Erreur interne du serveur.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Méthode privée pour récupérer l'UID depuis le SoffitHolder
     */
    private String getCurrentUid() {
        String uid = soffitHolder.getSub();
        if (uid == null || uid.isBlank()) {
            log.warn("Aucun uid trouvé dans le SoffitHolder — token manquant ou expiré");
            return null;
        }
        return uid;
    }

}