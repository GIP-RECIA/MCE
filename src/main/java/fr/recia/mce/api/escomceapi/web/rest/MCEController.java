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

import fr.recia.mce.api.escomceapi.security.AppUser;
import fr.recia.mce.api.escomceapi.services.ChangePasswordService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.UserDto;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.UserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.RelationEleveService;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetStatusDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserForDisplayDTO;
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

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/mce")
public class MCEController {

    private final FonctionService fonctionService;
    private final PersonneService personneService;private final UserDTOFactory userDTOFactory;
    private final EmailVerificationService emailVerificationService;
    private final ChangePasswordService changePasswordService;

    public MCEController(FonctionService fonctionService, PersonneService personneService, UserDTOFactory userDTOFactory, EmailVerificationService emailVerificationService, ChangePasswordService changePasswordService) {
        this.fonctionService = fonctionService;
        this.personneService = personneService;
        this.userDTOFactory = userDTOFactory;
        this.emailVerificationService = emailVerificationService;
        this.changePasswordService = changePasswordService;
    }

    /**
     * Donne les informations de l'utilisateur actuellement connecté pour pouvoir les afficher
     */
    @GetMapping()
    public ResponseEntity<UserForDisplayDTO> getMCE(@AuthenticationPrincipal AppUser principal) {
        final String uid = principal.getUid();
        final UserDto userDto = personneService.getUser(uid);
        final UserForDisplayDTO userToDisplay = userDTOFactory.from(userDto);
        if (userToDisplay == null) {
            throw new PersonneNotFoundException("Utilisateur non trouvé pour l'uid : " + uid);
        }
        return ResponseEntity.ok(userToDisplay);
    }

    /**
     * Donne les informations d'un utilisateur donné en fonction de son UID (utile pour l'affichage des personnes en relation)
     * Vérifie bien que la personne à le droit d'afficher la personne en relation
     */
    @GetMapping("/{uid}")
    public ResponseEntity<UserForDisplayDTO> getDetailRelation(@PathVariable String uid, @AuthenticationPrincipal AppUser principal) {
        // TODO : à améliorer ? on ne devrait pas avoir une requête avec l'uid qui circule en clair
        final String principalUid = principal.getUid();
        final UserDto principalUser = personneService.getUser(uid);
        if (!canAccessRelationProfile(principalUser, uid)) {
            log.warn("Audit [GET_DETAIL_ENFANT] : Tentative d'accès non autorisé au profil uid={} par [{}]", uid, principalUid);
            throw new AccessDeniedException("Vous ne pouvez consulter que votre profil ou celui des personnes en relation avec vous");
        }
        final UserForDisplayDTO relation = userDTOFactory.from(principalUser);
        if (relation == null) {
            throw new PersonneNotFoundException("Enfant non trouvé pour l'uid : " + uid);
        }
        return ResponseEntity.ok(relation);
    }

    /**
     * Met à jour la date de fin d'une fonction donnée de l'utilisateur (utile pour désactiver une fonction)
     */
    @PutMapping("/fonction/{id}/dateFin")
    public ResponseEntity<Void> updateDateFin(@PathVariable Long id, @RequestBody boolean active, @AuthenticationPrincipal AppUser principal) {
        final String currentUid = principal.getUid();
        final UserDto userDto = personneService.getUser(currentUid);
        if (!canEditFunction(userDto, id)) {
            log.warn("Audit [UPDATE_DATEFIN] : Tentative de modification non autorisée de la fonction id={} par [{}]", id, currentUid);
            throw new AccessDeniedException("Vous ne pouvez modifier que vos propres fonctions");
        }
        log.debug("Mise à jour de l'état (active={}) de la fonction [id={}]", active, id);
        fonctionService.updateDateFin(id, active);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Change le mot de passe d'un utilisateur
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePass(@Valid @RequestBody PasswordChangeRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        final String uid = principal.getUid();
        final UserDto userDto = personneService.getUser(uid);
        changePasswordService.changePassword(userDto, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Envoi le code de validation pour confirmer le changement de mail d'un utilisateur
     */
    @PutMapping("/update-email")
    public ResponseEntity<?> updateEmail(@Valid @RequestBody EmailUpdateRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        final String uid = principal.getUid();
        if (!request.getEmail().equals(request.getConfirmEmail())) {
            log.warn("Les adresses email ne correspondent pas pour uid={}", uid);
            return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", "Les adresses email ne correspondent pas"));
        }
        final UserDto userDto = personneService.getUser(uid);
        personneService.validateEmailForUpdate(userDto, request.getEmail());
        emailVerificationService.sendVerificationEmail(userDto, request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new ErrorResponse("VERIFICATION_SENT", "Un email de vérification a été envoyé à " + request.getEmail()));
    }

    /**
     * Valide le changement de mail d'un utilisateur avec un code de validation
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        final String uid = principal.getUid();
        final UserDto userDto = personneService.getUser(uid);
        log.debug("[VERIFY_EMAIL] Requête pour uid={}", uid);
        emailVerificationService.verifyEmail(userDto, request.getCode(), request.isDoForward());
        log.info("[VERIFY_EMAIL] Succès uid={}", uid);
        return ResponseEntity.ok(new ErrorResponse("SUCCESS", "Email verifié avec succès"));
    }

    @PostMapping("/avatar")
    public ResponseEntity<Void> updateAvatar(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AppUser principal) throws Exception {
        String uid = principal.getUid();
        personneService.updateAvatar(uid, file.getBytes());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/network-password/status")
    // TODO : ça ne devrait pas être une route mais une info donnée dans l'utilisateur
    public ResponseEntity<NetworkPasswordResetStatusDTO> networkPasswordStatus(@AuthenticationPrincipal AppUser principal) {
       final String uid = principal.getUid();
        NetworkPasswordResetStatusDTO status = changePasswordService.getNetworkPasswordResetStatus(uid);
        log.info("[NETWORK_PASSWORD_RESET] Statut uid={} eligible={}", uid, status.isEligible());
        return ResponseEntity.ok(status);
    }

    /**
     * Changement du mot de passe réseau
     */
    @PostMapping("/network-password/reset")
    // TODO : ca ne devrait pas être une route entière mais une opération supplémentaire qui s'applique en même temps que le changement de mot de passe
    public ResponseEntity<?> networkPasswordReset(@Valid @RequestBody NetworkPasswordResetRequestDTO request, @AuthenticationPrincipal AppUser principal) {
        final String uid = principal.getUid();
        log.info("[NETWORK_PASSWORD_RESET] Application pour uid={}", uid);
        changePasswordService.changeNetworkPassword(uid, request.getNewPassword(), request.getConfirmPassword());
        log.info("[NETWORK_PASSWORD_RESET] Succès uid={}", uid);
        return ResponseEntity.ok(new ErrorResponse("NETWORK_PASSWORD_RESET_SUCCESS", "Votre mot de passe réseau a été modifié avec succès"));
    }

    /**
     * Indique si un utilisateur à le droit d'accéder aux informations d'un autre utilisateur
     */
    private boolean canAccessRelationProfile(UserDto principalUser, String uid) {
        if (principalUser.getUid().equals(uid)) {
            return true;
        }
        try {
            // Test sur les parents de l'enfant
            if (principalUser.getRespEleves().stream().map(RelationEleveContact::getUidRelation).anyMatch(rel -> rel != null && rel.equals(uid))) {
                return true;
            }
            // Test sur les enfants du parent
            return principalUser.getEleves().stream().map(RelationEleveContact::getUidRelation).anyMatch(rel -> rel != null && rel.equals(uid));
        } catch (Exception e) {
            log.warn("Erreur lors du contrôle d'accès au profil relation {} par {} : {}", uid, principalUser.getUid(), e.getMessage());
            return false;
        }
    }

    /**
     * Indique si un utilisateur a le droit de modifier une fonction donnée
     */
    private boolean canEditFunction(UserDto userDto, Long fonctionId) {
        try {
            final Long selfPersonId = userDto.getDatabaseUserDto().getId();
            final Long ownerPersonId = fonctionService.getPersonIdOfFonction(fonctionId);
            return selfPersonId.equals(ownerPersonId);
        } catch (Exception e) {
            log.warn("Erreur lors du contrôle de modification de la fonction id={} par {} : {}", fonctionId, userDto.getLdapUserDto().getId(), e.getMessage());
            return false;
        }
    }

}
