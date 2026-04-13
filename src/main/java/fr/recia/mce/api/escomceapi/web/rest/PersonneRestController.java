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
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.nio.file.AccessDeniedException;

@Slf4j
@RestController
@RequestMapping("/api/personne/mce")
public class PersonneRestController {

    private final PersonneService personneService;
    private final IUserDTOFactory userDTOFactory;

    /**
     * Constructeur du contrôleur.
     *
     * @param personneService service métier pour la gestion des personnes
     * @param userDTOFactory factory permettant de construire les UserDTO
     */
    public PersonneRestController(PersonneService personneService, IUserDTOFactory userDTOFactory) {
        this.personneService = personneService;
        this.userDTOFactory = userDTOFactory;
    }

    /**
     * Récupère l'identifiant (UID) de l'utilisateur actuellement authentifié.
     *
     * @param authentication objet d'authentification Spring Security
     * @return UID de l'utilisateur connecté
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    @GetMapping("/id")
    public ResponseEntity<String> getCurrentUserId(Authentication authentication) {
        return ResponseEntity.ok(requireAuthenticatedUid(authentication));
    }

    /**
     * Récupère les informations complètes de la personne associée
     * à l'utilisateur actuellement connecté.
     *
     * @param authentication objet d'authentification Spring Security
     * @return {@link PersonneDTO} contenant les informations de la personne
     * @throws PersonneNotFoundException si aucune personne n'est trouvée
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid(Authentication authentication) {
        String uid = requireAuthenticatedUid(authentication);

        PersonneDTO personne = personneService.retrievePersonnebyUid(uid);
        if (personne == null) {
            throw new PersonneNotFoundException("Aucune personne trouvée pour l'uid : " + uid);
        }

        log.info("Personne trouvée : {}", personne);
        return ResponseEntity.ok(personne);
    }

    /**
     * Récupère la fiche LDAP brute de l'utilisateur connecté.
     *
     * @param authentication objet d'authentification Spring Security
     * @return {@link IExternalUser} représentant les données LDAP
     * @throws PersonneNotFoundException si aucune entrée LDAP n'est trouvée
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap(Authentication authentication) {
        String uid = requireAuthenticatedUid(authentication);

        IExternalUser personne = personneService.retrievePersonLdap(uid);
        if (personne == null) {
            throw new PersonneNotFoundException("Aucune entrée LDAP trouvée pour l'uid : " + uid);
        }

        return ResponseEntity.ok(personne);
    }

    /**
     * Récupère le DTO complet de l'utilisateur connecté.
     *
     * @param authentication objet d'authentification Spring Security
     * @return {@link UserDTO} représentant l'utilisateur
     * @throws PersonneNotFoundException si aucun utilisateur n'est trouvé
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE(Authentication authentication) {
        String uid = requireAuthenticatedUid(authentication);

        UserDTO user = userDTOFactory.from(uid);
        if (user == null) {
            throw new PersonneNotFoundException("Aucun utilisateur trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    /**
     * Récupère les informations d'un enfant (élève) à partir de son identifiant.
     *
     * @param id identifiant de l'enfant
     * @return {@link UserDTO} représentant l'enfant
     * @throws PersonneNotFoundException si aucun enfant n'est trouvé
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String id) {
        UserDTO enfant = userDTOFactory.from(id);
        if (enfant == null) {
            throw new PersonneNotFoundException("Aucun enfant trouvé pour l'id : " + id);
        }

        return ResponseEntity.ok(enfant);
    }

    /**
     * Permet à un utilisateur de modifier son propre mot de passe.
     *
     * <p>
     * Sécurité :
     * <ul>
     *     <li>L'utilisateur authentifié doit correspondre à l'UID fourni</li>
     *     <li>Sinon, une exception {@link AccessDeniedException} est levée</li>
     * </ul>
     *
     * @param uid UID de l'utilisateur
     * @param request objet contenant l'ancien et le nouveau mot de passe
     * @param authentication objet d'authentification Spring Security
     * @return réponse HTTP 200 si succès
     * @throws AccessDeniedException si l'utilisateur tente de modifier un autre compte
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    @PostMapping("/{uid}/change-password")
    public ResponseEntity<Void> changePass(
            @PathVariable String uid,
            @Valid @RequestBody PasswordChangeRequest request,
            Authentication authentication) throws AccessDeniedException {

        String currentUid = requireAuthenticatedUid(authentication);

        if (!currentUid.equals(uid)) {
            log.warn("Tentative non autorisée de modification de mot de passe pour {}", uid);
            throw new AccessDeniedException("Action non autorisée");
        }

        userDTOFactory.changePassword(uid, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Vérifie et retourne l'UID de l'utilisateur authentifié.
     *
     * @param authentication objet d'authentification Spring Security
     * @return UID de l'utilisateur
     * @throws IllegalStateException si l'utilisateur n'est pas authentifié
     */
    private String requireAuthenticatedUid(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        return authentication.getName();
    }

}