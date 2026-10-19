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

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.security.AppUser;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordResetService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.ChampsObligatoiresException;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetStatusDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import fr.recia.mce.api.escomceapi.web.dto.VerifyEmailRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/mce")
public class MCEController {

    private final FonctionService fonctionService;
    private final PersonneService personneService;
    private final IRelationEleveService relationEleveService;
    private final IUserDTOFactory userDTOFactory;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public MCEController(FonctionService fonctionService, PersonneService personneService, IRelationEleveService relationEleveService,
                         IUserDTOFactory userDTOFactory, EmailVerificationService emailVerificationService, PasswordResetService passwordResetService) {
        this.fonctionService = fonctionService;
        this.personneService = personneService;
        this.relationEleveService = relationEleveService;
        this.userDTOFactory = userDTOFactory;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Donne les informations de l'utilisateur actuellement connecté pour pouvoir les afficher
     */
    @GetMapping()
    public ResponseEntity<UserDTO> getMCE(@AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        UserDTO user = userDTOFactory.from(uid);
        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    /**
     * Donne les informations d'un utilisateur donné en fonction de son UID (utile pour l'affichage des personnes en relation)
     * Vérifie bien que la personne à le droit d'afficher la personne en relation
     */
    @GetMapping("/{uid}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String uid, @AuthenticationPrincipal AppUser principal) {
        // TODO : à améliorer ? on ne devrait pas avoir une requête avec l'uid qui circule en clair
        String currentUid = principal.getUid();
        if (!canAccessRelationProfile(currentUid, uid)) {
            log.warn("Audit [GET_DETAIL_ENFANT] : Tentative d'accès non autorisé au profil uid={} par [{}]", uid, currentUid);
            throw new AccessDeniedException("Vous ne pouvez consulter que votre profil ou celui des personnes en relation avec vous");
        }
        UserDTO enfant = userDTOFactory.from(uid);

        if (enfant == null) {
            throw new PersonneNotFoundException("Enfant non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(enfant);
    }

    /**
     * Met à jour la date de fin d'une fonction donné de l'utilisateur (utile pour désactiver une fonction)
     */
    @PutMapping("/fonction/{id}/dateFin")
    public ResponseEntity<Void> updateDateFin(@PathVariable Long id, @RequestBody boolean active, @AuthenticationPrincipal AppUser principal) {
        String currentUid = principal.getUid();
        if (!canEditFunction(currentUid, id)) {
            log.warn("Audit [UPDATE_DATEFIN] : Tentative de modification non autorisée de la fonction id={} par [{}]", id, currentUid);
            throw new AccessDeniedException("Vous ne pouvez modifier que vos propres fonctions");
        }
        log.debug("Mise à jour de l'état (active={}) de la fonction [id={}]", active, id);
        fonctionService.updateDateFin(id, active);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePass(@Valid @RequestBody PasswordChangeRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        userDTOFactory.changePassword(uid, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/update-email")
    public ResponseEntity<?> updateEmail(@Valid @RequestBody EmailUpdateRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        if (!request.getEmail().equals(request.getConfirmEmail())) {
            log.warn("Les adresses email ne correspondent pas pour uid={}", uid);
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "Les adresses email ne correspondent pas"));
        }
        personneService.validateEmailForUpdate(uid, request.getEmail());
        emailVerificationService.sendVerificationEmail(uid, request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new ErrorResponse("VERIFICATION_SENT", "Un email de vérification a été envoyé à " + request.getEmail()));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        if (uid == null || uid.isBlank()) {
            log.warn("[VERIFY_EMAIL] ÉCHEC : aucun identifiant");
            throw new ChampsObligatoiresException("L'identifiant est obligatoire");
        }
        log.debug("[VERIFY_EMAIL] Requête pour uid={}", uid);
        emailVerificationService.verifyEmail(uid, request.getCode(), request.isDoForward());
        log.info("[VERIFY_EMAIL] Succès uid={}", uid);
        return ResponseEntity.ok(new ErrorResponse("SUCCESS", "Email verifié avec succès"));
    }


    @PostMapping("/avatar")
    public ResponseEntity<Void> updateAvatar(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AppUser principal) throws Exception {
        String uid = principal.getUid();
        personneService.updateAvatar(uid, file.getBytes());
        return ResponseEntity.noContent().build();
    }

    /**
     * Statut du parcours « mot de passe réseau » du compte authentifié : éligibilité (CVDL ntPass sans
     * mot de passe local stocké, état Valide, détectée côté serveur car {@code ntPass} n'est pas présent
     * dans le jeton OIDC). Permet au portail d'afficher le bon écran.
     */
    @GetMapping("/network-password/status")
    public ResponseEntity<NetworkPasswordResetStatusDTO> networkPasswordStatus(@AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        NetworkPasswordResetStatusDTO status = passwordResetService.getNetworkPasswordResetStatus(uid);
        log.info("[NETWORK_PASSWORD_RESET] Statut uid={} eligible={}", uid, status.isEligible());
        return ResponseEntity.ok(status);
    }

    private boolean canAccessRelationProfile(String currentUid, String uid) {
        if (currentUid.equals(uid)) {
            return true;
        }
        try {
            Collection<RelationEleveContact> relations = relationEleveService.allRelationEleves(currentUid);
            if (relations != null
                && relations.stream().map(RelationEleveContact::getUidRelation)
                .anyMatch(rel -> rel != null && rel.equals(uid))) {
                return true;
            }

            PersonneDTO current = personneService.retrievePersonnebyUid(currentUid);
            if (current != null && current.getAPersonneBase() != null) {
                Long parentId = current.getAPersonneBase().getId();
                return relationEleveService.allEleveEnRelation(parentId).stream()
                    .map(RelationEleveContact::getUidRelation)
                    .anyMatch(rel -> rel != null && rel.equals(uid));
            }
            return false;
        } catch (Exception e) {
            log.warn("Erreur lors du contrôle d'accès au profil relation {} par {} : {}", uid, currentUid, e.getMessage());
            return false;
        }
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
