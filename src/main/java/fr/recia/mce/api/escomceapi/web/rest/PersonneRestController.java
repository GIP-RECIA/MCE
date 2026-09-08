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

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.enums.SurType;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.ActivationService;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.ApiException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.exception.ResendCooldownActiveException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.ActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationResultDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationStatusResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.CharteAcceptRequest;
import fr.recia.mce.api.escomceapi.web.dto.CharteStatusResponse;
import fr.recia.mce.api.escomceapi.web.dto.ConnexionActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ConnexionActivationResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ForgotPasswordRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ResetPasswordRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.RecoverUidRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.StructureResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.VerifyEmailRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/personne/mce")
public class PersonneRestController {

    private static final String GUEST_USER = "guest";

    private final PersonneService personneService;
    private final IUserDTOFactory userDTOFactory;
    private final SoffitHolder soffitHolder;
    private final EmailVerificationService emailVerificationService;
    private final CharteService charteService;
    private final ActivationService activationService;
    private final IStructureService structureService;
    private final IRelationEleveService relationEleveService;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    public PersonneRestController(PersonneService personneService, IUserDTOFactory userDTOFactory,
            SoffitHolder soffitHolder, EmailVerificationService emailVerificationService,
            CharteService charteService,
            ActivationService activationService,
            IStructureService structureService,
            IRelationEleveService relationEleveService) {
        this.personneService = personneService;
        this.userDTOFactory = userDTOFactory;
        this.soffitHolder = soffitHolder;
        this.emailVerificationService = emailVerificationService;
        this.charteService = charteService;
        this.activationService = activationService;
        this.structureService = structureService;
        this.relationEleveService = relationEleveService;
    }

    @GetMapping("/debug-id")
    public ResponseEntity<String> getCurrentUserId() {
        return ResponseEntity.ok(getCurrentUid());
    }

    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid() {
        String uid = getCurrentUid();
        PersonneDTO personne = personneService.retrievePersonnebyUid(uid);

        if (personne == null) {
            throw new PersonneNotFoundException("Personne non trouvée pour l'uid : " + uid);
        }

        return ResponseEntity.ok(personne);
    }

    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap() {
        String uid = getCurrentUid();
        IExternalUser user = personneService.retrievePersonLdap(uid);

        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur LDAP non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE() {
        String uid = getCurrentUid();
        UserDTO user = userDTOFactory.from(uid);

        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String id) {
        String currentUid = getCurrentUid();
        if (!canAccessRelationProfile(currentUid, id)) {
            specialLog.warn("Audit [GET_DETAIL_ENFANT] : Tentative d'accès non autorisé au profil uid={} par [{}]", id, currentUid);
            throw new AccessDeniedException("Vous ne pouvez consulter que votre profil ou celui des personnes en relation avec vous");
        }
        UserDTO enfant = userDTOFactory.from(id);

        if (enfant == null) {
            throw new PersonneNotFoundException("Enfant non trouvé pour l'id : " + id);
        }

        return ResponseEntity.ok(enfant);
    }

    @PostMapping("/{uid}/change-password")
    public ResponseEntity<Void> changePass(
            @PathVariable String uid,
            @Valid @RequestBody PasswordChangeRequestDTO request) {

        String currentUid = getCurrentUid();

        if (!currentUid.equals(uid)) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : Tentative de changement de mot de passe non autorisée pour uid={}", uid);
            throw new AccessDeniedException("Vous ne pouvez modifier que votre propre mot de passe");
        }

        userDTOFactory.changePassword(uid, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{uid}/update-email")
    public ResponseEntity<?> updateEmail(
            @PathVariable String uid,
            @Valid @RequestBody EmailUpdateRequestDTO request) {

        String currentUid = getCurrentUid();

        if (!currentUid.equals(uid)) {
            log.warn("Tentative de mise à jour d'email non autorisée pour uid={}", uid);
            throw new AccessDeniedException("Vous ne pouvez modifier que votre propre email");
        }

        if (!request.getEmail().equals(request.getConfirmEmail())) {
            log.warn("Les adresses email ne correspondent pas pour uid={}", uid);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("BAD_REQUEST", "Les adresses email ne correspondent pas"));
        }

        personneService.validateEmailForUpdate(uid, request.getEmail());
        emailVerificationService.sendVerificationEmail(uid, request.getEmail());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ErrorResponse("VERIFICATION_SENT",
                        "Un email de vérification a été envoyé à " + request.getEmail()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        String uid = request.getUid();
        String email = request.getEmail();
        String profil = request.getProfil();

        log.info("[FORGOT_PASSWORD] Demande uid={}, email={}, profil={}", uid, email, profil);

        try {
            emailVerificationService.sendPasswordResetCode(uid, email, profil);
        } catch (ResendCooldownActiveException e) {
            // Anti-double-clic : laisser le GlobalExceptionHandler répondre 429 avec le temps restant.
            throw e;
        } catch (ContactAdminException e) {
            log.warn("[FORGOT_PASSWORD] CONTACT_ADMIN uid={} : {}", uid, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("CONTACT_ADMIN_REQUIRED", e.getMessage()));
        } catch (IllegalArgumentException | ApiException e) {
            log.warn("[FORGOT_PASSWORD] ÉCHEC uid={} : {}", uid, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("FORGOT_PASSWORD_FAILED", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[FORGOT_PASSWORD] ERREUR uid={} : {}", uid, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("INTERNAL_ERROR", "Une erreur interne est survenue"));
        }

        log.info("[FORGOT_PASSWORD] Code envoyé à {} pour uid={}", email, uid);
        return ResponseEntity.ok(new ErrorResponse("RESET_CODE_SENT",
                "Un code de réinitialisation a été envoyé à votre adresse email"));
    }

    @PostMapping("/recover-uid")
    public ResponseEntity<?> recoverUid(
            @Valid @RequestBody RecoverUidRequestDTO request) {

        log.info("[RECOVER_UID] Demande nom={}, prenom={}, email={}, profil={}, type={}, ville={}, etab={}",
                request.getNom(), request.getPrenom(), request.getEmail(), request.getProfil(),
                request.getTypeEtablissement(), request.getVille(), request.getEtablissement());

        // Résolution précise par identité + établissement + email, puis envoi du code si la cible
        // est unique. Réponse volontairement générique : aucun détail ne distingue un email inexistant,
        // un compte sans mot de passe local, une cible ambiguë, etc. → pas d'énumération d'utilisateurs,
        // pas de fuite d'uid. (see EmailVerificationService.recoverUid)
        EmailVerificationService.RecoverUidResult result = emailVerificationService.recoverUid(request);

        log.info("[RECOVER_UID] Réponse générique envoyée pour email={} (code envoyé ? {})",
                request.getEmail(), result != null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "RECOVER_CODE_SENT");
        body.put("message",
                "Si un compte correspond à ces informations et permet de réinitialiser son mot de passe, un code vous a été envoyé.");
        // codeSent distingue pour le front "un code a bien été envoyé" du cas où aucun
        // code n'a pu l'être (compte non réinitialisable : EduConnect, pas d'email, ...).
        // Le message reste volontairement générique (anti-énumération) : identique dans
        // les deux cas. Un compte réellement réinitialisable ne fournit pas plus d'indices.
        body.put("codeSent", result != null);
        body.put("charteRequired", result != null && result.isCharteRequired());
        if (result != null && result.getCharteUrl() != null) {
            body.put("charteUrl", result.getCharteUrl());
        }
        if (result != null && result.getResetToken() != null) {
            body.put("resetToken", result.getResetToken());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {

        String uid = request.getUid();
        log.info("[RESET_PASSWORD] Demande uid={}, charteAccepted={}", uid, request.isCharteAccepted());

        emailVerificationService.processResetPassword(
                uid, request.getResetToken(), request.getCode(),
                request.getNewPassword(), request.getConfirmPassword(),
                request.isCharteAccepted());

        log.info("[RESET_PASSWORD] Succès uid={}", uid);
        return ResponseEntity.ok(new ErrorResponse("PASSWORD_RESET_SUCCESS",
                "Votre mot de passe a été réinitialisé avec succès"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(
            @Valid @RequestBody VerifyEmailRequestDTO request) {

        String uid = request.getUid();

        log.debug("[VERIFY_EMAIL] Requête pour uid={}", uid);

        emailVerificationService.verifyEmail(uid, request.getCode());
        log.info("[VERIFY_EMAIL] Succès uid={}", uid);
        return ResponseEntity.ok(new ErrorResponse("SUCCESS", "Email verifié avec succès"));
    }

    @GetMapping("/charte-status")
    public ResponseEntity<CharteStatusResponse> getCharteStatus(@RequestParam String uid) {
        boolean charteRequired = charteService.isCharteRequired(uid);
        String charteUrl = charteService.getCharteUrl(uid);
        boolean charteSignee = !charteRequired;
        log.info("[CHARTE_STATUS] uid={}, charteRequired={}, charteSignee={}, charteUrl={}",
                uid, charteRequired, charteSignee, charteUrl);
        return ResponseEntity.ok(new CharteStatusResponse(charteRequired, charteUrl, charteSignee));
    }

    /**
     * Fait signer au compte authentifié (uid issu du jeton) la charte de son domaine courant.
     * Accessible à TOUT utilisateur valide, y compris les profils EduConnect/agri/CVDL
     * qui n'ont ni activation ni réinitialisation de mot de passe locale (donc aucun autre
     * point d'entrée pour signer leur charte).
     * L'uid n'est PAS lu dans le corps de la requête mais depuis le jeton Soffit, afin qu'un
     * utilisateur ne puisse signer que sa propre charte.
     */
    @PostMapping("/charte/accept")
    public ResponseEntity<CharteStatusResponse> accepterCharte(@Valid @RequestBody CharteAcceptRequest request) {
        String uid = getCurrentUid();

        if (!request.isCharteAccepted()) {
            throw new IllegalArgumentException("Vous devez accepter la charte d'utilisation avant de poursuivre");
        }

        log.info("[CHARTE][ACCEPT] uid={} acceptation de la charte", uid);

        boolean charteRequise = charteService.isCharteRequired(uid);
        if (charteRequise) {
            personneService.signCharte(uid);
            log.info("[CHARTE][ACCEPT] uid={} charte signée", uid);
        } else {
            log.info("[CHARTE][ACCEPT] uid={} charte déjà signée, aucune écriture", uid);
        }

        boolean charteSignee = !charteService.isCharteRequired(uid);
        String charteUrl = charteService.getCharteUrl(uid);
        return ResponseEntity.ok(new CharteStatusResponse(!charteSignee, charteUrl, charteSignee));
    }

    /**
     * Point d'entrée CONNEXION du parcours d'activation de compte : login + mot de passe temporaire. Public, le compte étant encore inactif.
     */
    @PostMapping("/activation/connexion")
    public ResponseEntity<ConnexionActivationResponseDTO> connexionActivation(
            @Valid @RequestBody ConnexionActivationRequestDTO request) {

        log.info("[ACTIVATION][CONNEXION] Demande pour login={}", request.getLogin());
        String uid = activationService.connexion(request.getLogin(), request.getPassword());
        log.info("[ACTIVATION][CONNEXION] Succès uid={}", uid);
        return ResponseEntity.ok(new ConnexionActivationResponseDTO(uid));
    }

    /**
     * Détermine le parcours d'activation du compte (CHARTE → COURRIEL → PASSWORD → FIN) selon le profil.
     */
    @GetMapping("/activation/status")
    public ResponseEntity<ActivationStatusResponseDTO> activationStatus(@RequestParam String uid) {
        log.info("[ACTIVATION][STATUS] Demande uid={}", uid);
        ActivationStatusResponseDTO status = activationService.getActivationStatus(uid);
        log.info("[ACTIVATION][STATUS] Succès uid={} etape={}", uid, status.getEtapeSuivante());
        return ResponseEntity.ok(status);
    }

    /**
     * Point d'entrée PASSWORD du parcours d'activation : charte + mot de passe (et éventuellement email) puis activation du compte.
     */
    @PostMapping("/activation/password")
    public ResponseEntity<ActivationResultDTO> activerCompte(
            @Valid @RequestBody ActivationRequestDTO request) {

        log.info("[ACTIVATION][PASSWORD] Demande uid={}, charteAccepted={}", request.getUid(), request.isCharteAccepted());
        ActivationResultDTO result = activationService.activate(request);
        log.info("[ACTIVATION][PASSWORD] Succès uid={} etat={}", result.getUid(), result.getEtat());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{uid}/avatar")
    public ResponseEntity<Void> updateAvatar(
            @PathVariable String uid,
            @RequestParam("file") MultipartFile file) throws Exception {

        String currentUid = getCurrentUid();
        if (!currentUid.equals(uid)) {
            log.warn("Tentative d'upload d'avatar non autorisée pour UID [{}] par [{}]", uid, currentUid);
            throw new AccessDeniedException("Vous ne pouvez modifier que votre propre avatar");
        }

        personneService.updateAvatar(uid, file.getBytes());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/structures/profils")
    public ResponseEntity<List<String>> getProfils() {
        log.info("[STRUCTURES] GET /structures/profils");
        List<String> profils = personneService.getDistinctCategories();
        log.info("[STRUCTURES] {} profil(s) trouvé(s)", profils.size());
        return ResponseEntity.ok(profils);
    }

    @GetMapping("/structures/types")
    public ResponseEntity<List<String>> getTypes() {
        log.info("[STRUCTURES] GET /structures/types");
        List<String> types = java.util.Arrays.stream(SurType.values())
                .map(SurType::name)
                .collect(Collectors.toList());
        log.info("[STRUCTURES] {} type(s) trouvé(s)", types.size());
        return ResponseEntity.ok(types);
    }

    @GetMapping("/structures/villes")
    public ResponseEntity<List<String>> getVilles(@RequestParam(required = false) String type) {
        log.info("[STRUCTURES] GET /structures/villes type={}", type);
        Set<String> villes;
        if (type != null && !type.isBlank()) {
            SurType surType;
            try {
                surType = SurType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(List.of("Type inconnu : " + type + ". Valeurs acceptées : " + SurType.acceptedValues()));
            }
            villes = structureService.findVillesBySurType(surType);
        } else {
            villes = structureService.getAllVilles();
        }
        return ResponseEntity.ok(List.copyOf(villes));
    }

    @GetMapping("/structures")
    public ResponseEntity<List<StructureResponseDTO>> getStructures(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String ville) {
        log.info("[STRUCTURES] GET /structures type={} ville={}", type, ville);
        List<StructureResponseDTO> result = structureService.getAllStructures().stream()
                .map(StructureResponseDTO::new)
                .filter(s -> type == null || type.isBlank() || (s.getType() != null && s.getType().equalsIgnoreCase(type)))
                .filter(s -> ville == null || ville.isBlank() || (s.getVille() != null && s.getVille().equalsIgnoreCase(ville)))
                .collect(Collectors.toList());
        log.info("[STRUCTURES] {} structure(s) trouvée(s)", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{uid}/avatar{suffix:.*}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String uid, @PathVariable(required = false) String suffix) {
        // Note : le paramètre {uid} de l'URL d'avatar est un HASH opaque (getHashFromUid), PAS l'uid réel.
        // C'est ce hash qui sert d'accès (non énumérable) au fichier : pas de garde currentUid.equals(uid).
        // Le POST /{uid}/avatar (upload) reste protégé par la vérification de propriété sur l'uid réel.
        byte[] image = personneService.getAvatar(uid);
        if (image == null) {
            throw new PersonneNotFoundException("Avatar non trouvé pour l'uid : " + uid);
        }
        return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .body(image);
    }

    private String getCurrentUid() {
        String sub = soffitHolder.getSub();
        if (sub == null || sub.isBlank() || GUEST_USER.equals(sub)) {
            throw new AccessDeniedException("Utilisateur non authentifié");
        }
        return sub;
    }

    private boolean canAccessRelationProfile(String currentUid, String id) {
        if (currentUid.equals(id)) {
            return true;
        }
        try {
            Collection<RelationEleveContact> relations = relationEleveService.allRelationEleves(currentUid);
            if (relations != null
                    && relations.stream().map(RelationEleveContact::getUidRelation)
                            .anyMatch(uid -> uid != null && uid.equals(id))) {
                return true;
            }

            PersonneDTO current = personneService.retrievePersonnebyUid(currentUid);
            if (current != null && current.getAPersonneBase() != null) {
                Long parentId = current.getAPersonneBase().getId();
                return relationEleveService.allEleveEnRelation(parentId).stream()
                        .map(RelationEleveContact::getUidRelation)
                        .anyMatch(uid -> uid != null && uid.equals(id));
            }
            return false;
        } catch (Exception e) {
            log.warn("Erreur lors du contrôle d'accès au profil relation {} par {} : {}", id, currentUid, e.getMessage());
            return false;
        }
    }
}
