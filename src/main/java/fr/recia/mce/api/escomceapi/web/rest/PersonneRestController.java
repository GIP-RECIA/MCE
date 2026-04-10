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
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/personne/mce")
public class PersonneRestController {

    private final PersonneService personneService;
    private final IUserDTOFactory userDTOFactory;

    public PersonneRestController(PersonneService personneService, IUserDTOFactory userDTOFactory) {
        this.personneService = personneService;
        this.userDTOFactory = userDTOFactory;
    }

    /**
     * Retourne l'UID de l'utilisateur actuellement authentifié.
     *
     * @param authentication l'objet Authentication injecté par Spring Security
     * @return l'UID de l'utilisateur ou 401 si non authentifié
     */
    @GetMapping("/id")
    public ResponseEntity<String> getCurrentUserId(Authentication authentication) {
        String uid = getCurrentUid(authentication);
        return uid != null
                ? ResponseEntity.ok(uid)
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Aucun utilisateur authentifié");
    }

    /**
     * Retourne les informations complètes d'une personne (PersonneDTO) pour l'utilisateur connecté.
     *
     * @param authentication l'objet Authentication
     * @return PersonneDTO de l'utilisateur ou 401 si non authentifié
     */
    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid(Authentication authentication) {
        String uid = getCurrentUid(authentication);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PersonneDTO personne = personneService.retrievePersonnebyUid(uid);
        if (personne == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Personne trouvée : {}", personne);
        return ResponseEntity.ok(personne);
    }

    /**
     * Retourne la fiche LDAP brute de l'utilisateur connecté.
     *
     * @param authentication l'objet Authentication
     * @return IExternalUser ou 401/404 selon le cas
     */
    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap(Authentication authentication) {
        String uid = getCurrentUid(authentication);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IExternalUser personne = personneService.retrievePersonLdap(uid);
        return personne != null
                ? ResponseEntity.ok(personne)
                : ResponseEntity.notFound().build();
    }

    /**
     * Retourne le UserDTO complet de l'utilisateur connecté.
     *
     * @param authentication l'objet Authentication
     * @return UserDTO de l'utilisateur
     */
    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE(Authentication authentication) {
        String uid = getCurrentUid(authentication);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDTO user = userDTOFactory.from(uid);
        return user != null
                ? ResponseEntity.ok(user)
                : ResponseEntity.notFound().build();
    }

    /**
     * Retourne le UserDTO d'un enfant/élève par son identifiant.
     *
     * @param id identifiant de l'enfant
     * @return UserDTO de l'enfant
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String id) {
        UserDTO enfant = userDTOFactory.from(id);
        return enfant != null
                ? ResponseEntity.ok(enfant)
                : ResponseEntity.notFound().build();
    }

    /**
     * Change le mot de passe de l'utilisateur connecté.
     *
     * @param uid UID de l'utilisateur dont on veut changer le mot de passe
     * @param request données du changement de mot de passe
     * @param authentication authentification de l'utilisateur courant
     */
    @PostMapping("/{uid}/change-password")
    public ResponseEntity<Void> changePass(
            @PathVariable String uid,
            @Valid @RequestBody PasswordChangeRequest request,
            Authentication authentication) {

        String currentUid = getCurrentUid(authentication);

        if (currentUid == null) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }

        if (!currentUid.equals(uid)) {
            log.warn("Tentative non autorisée de modification de mot de passe pour {}", uid);
            throw new org.springframework.security.access.AccessDeniedException("Action non autorisée");
        }

        userDTOFactory.changePassword(uid, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Récupère l'UID de l'utilisateur à partir de l'objet Authentication.
     *
     * @param authentication l'authentication Spring Security
     * @return l'UID ou null si l'utilisateur n'est pas authentifié
     */
    private String getCurrentUid(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        return authentication.getName();
    }

}