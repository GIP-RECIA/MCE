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
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;

import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/personne/mce")
public class PersonneRestController {

    private final PersonneService personneService;
    private final IUserDTOFactory userDTOFactory;
    private final SoffitHolder soffitHolder;
    private final EmailVerificationService emailVerificationService;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    public PersonneRestController(PersonneService personneService, IUserDTOFactory userDTOFactory,
                                  SoffitHolder soffitHolder, EmailVerificationService emailVerificationService) {
        this.personneService = personneService;
        this.userDTOFactory = userDTOFactory;
        this.soffitHolder = soffitHolder;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * Retourne l'UID de l'utilisateur actuellement authentifié.
     *
     * @return l'UID de l'utilisateur ou 401 si non authentifié
     */
    @GetMapping("/debug-id")
    public ResponseEntity<String> getCurrentUserId() {
        return ResponseEntity.ok(getCurrentUid());
    }

    /**
     * Retourne les informations complètes d'une personne (PersonneDTO) pour l'utilisateur connecté.
     *
     * @return PersonneDTO de l'utilisateur ou 401 si non authentifié
     */
    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid() {
        String uid = getCurrentUid();
        PersonneDTO personne = personneService.retrievePersonnebyUid(uid);

        if (personne == null) {
            throw new PersonneNotFoundException("Personne non trouvée pour l'uid : " + uid);
        }

        return ResponseEntity.ok(personne);
    }

    /**
     * Retourne la fiche LDAP brute de l'utilisateur connecté.
     *
     * @return IExternalUser ou 401/404 selon le cas
     */
    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap() {
        String uid = getCurrentUid();
        IExternalUser user = personneService.retrievePersonLdap(uid);

        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur LDAP non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    /**
     * Retourne le UserDTO complet de l'utilisateur connecté.
     *
     * @return UserDTO de l'utilisateur
     */
    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE() {
        String uid = getCurrentUid();
        UserDTO user = userDTOFactory.from(uid);

        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    /**
     * Retourne le UserDTO d'un enfant/élève par son identifiant.
     *
     * @param id
     *            identifiant de l'enfant
     * @return UserDTO de l'enfant
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String id) {
        UserDTO enfant = userDTOFactory.from(id);

        if (enfant == null) {
            throw new PersonneNotFoundException("Enfant non trouvé pour l'id : " + id);
        }

        return ResponseEntity.ok(enfant);
    }

    /**
     * Change le mot de passe de l'utilisateur connecté.
     *
     * @param uid
     *            UID de l'utilisateur dont on veut changer le mot de passe
     * @param request
     *            données du changement de mot de passe
     */
    @PostMapping("/{uid}/change-password")
    public ResponseEntity<Void> changePass(
        @PathVariable String uid,
        @Valid @RequestBody PasswordChangeRequestDTO request,
        HttpServletRequest httpRequest) {

        String currentUid = getCurrentUid();

        if (!currentUid.equals(uid)) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : Tentative de changement de mot de passe non autorisée pour uid={}", uid);
            throw new AccessDeniedException("Vous ne pouvez modifier que votre propre mot de passe");
        }

        userDTOFactory.changePassword(uid, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Demande la vérification d'une adresse email.
     * Un email de confirmation est envoyé à la nouvelle adresse.
     *
     * @param uid
     *            UID de l'utilisateur
     * @param request
     *            objet contenant le nouvel email
     */
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

    /**
     * Vérifie une adresse email avec le code reçu par email.
     *
     * @param uid
     *            UID de l'utilisateur
     * @param code
     *            code de vérification
     */
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(
        @RequestParam String uid,
        @RequestParam String code) {

        log.debug("[VERIFY_EMAIL] Réception requête pour uid={} avec code={}", uid, code);

        String frontendUrl = emailVerificationService.getVerificationFrontendUrl();

        try {
            emailVerificationService.verifyEmail(uid, code);
            log.info("[VERIFY_EMAIL] SUCCÈS uid={}", uid);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(frontendUrl + "?status=success"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("[VERIFY_EMAIL] ÉCHEC uid={} : {}", uid, e.getMessage());
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(frontendUrl + "?status=error&message="
                            + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8)))
                    .build();
        }
    }

    @PostMapping("/{uid}/avatar")
    public ResponseEntity<Void> updateAvatar(
        @PathVariable String uid,
        @RequestParam("file") MultipartFile file) throws Exception {

        log.debug("Réception d'une requête d'upload d'avatar pour l'UID [{}]", uid);
        log.debug("Fichier reçu : nom={}, type={}, taille={} octets",
            file.getOriginalFilename(), file.getContentType(), file.getSize());

        String currentUid = getCurrentUid();
        if (!currentUid.equals(uid)) {
            log.warn("Tentative d'upload d'avatar non autorisée pour UID [{}] par l'utilisateur [{}]", uid, currentUid);
            throw new AccessDeniedException("Vous ne pouvez modifier que votre propre avatar");
        }

        personneService.updateAvatar(uid, file.getBytes());
        log.debug("Avatar mis à jour avec succès pour l'UID [{}]", uid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Récupère l'image de l'avatar d'un utilisateur.
     *
     * @param uid
     *            L'UID de l'utilisateur.
     * @param suffix
     *            Suffixe optionnel permettant d'ignorer les extensions de fichier (ex: .jpg).
     * @return La réponse contenant l'image en octets.
     */
    @GetMapping("/{uid}/avatar{suffix:.*}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String uid, @PathVariable(required = false) String suffix) {
        byte[] image = personneService.getAvatar(uid);
        if (image == null) {
            throw new PersonneNotFoundException("Avatar non trouvé pour l'uid : " + uid);
        }
        return ResponseEntity.ok()
            .header("Content-Type", "image/jpeg")
            .body(image);
    }

    /**
     * Récupère l'UID de l'utilisateur depuis le SoffitHolder (rempli par SoffitInterceptor).
     *
     * @return l'UID ou null si l'utilisateur n'est pas authentifié
     */
    private String getCurrentUid() {
        String sub = soffitHolder.getSub();
        if (sub == null || sub.isBlank() || "guest".equals(sub)) {
            throw new AccessDeniedException("Utilisateur non authentifié");
        }
        return sub;
    }
}
