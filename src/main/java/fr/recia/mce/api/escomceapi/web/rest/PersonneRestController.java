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
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.exception.ChampsObligatoiresException;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.CharteStatusResponse;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ForgotPasswordRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ResetPasswordRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.SearchUidRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.SearchUidResponseDTO;
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
import java.util.List;
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
    private final PasswordService passwordService;
    private final CharteService charteService;
    private final APersonneRepository aPersonneRepository;
    private final CerbereConfirmationRepository cerbereConfirmationRepository;
    private final IStructureService structureService;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    public PersonneRestController(PersonneService personneService, IUserDTOFactory userDTOFactory,
            SoffitHolder soffitHolder, EmailVerificationService emailVerificationService,
            PasswordService passwordService, CharteService charteService,
            APersonneRepository aPersonneRepository,
            CerbereConfirmationRepository cerbereConfirmationRepository,
            IStructureService structureService) {
        this.personneService = personneService;
        this.userDTOFactory = userDTOFactory;
        this.soffitHolder = soffitHolder;
        this.emailVerificationService = emailVerificationService;
        this.passwordService = passwordService;
        this.charteService = charteService;
        this.aPersonneRepository = aPersonneRepository;
        this.cerbereConfirmationRepository = cerbereConfirmationRepository;
        this.structureService = structureService;
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
        } catch (IllegalArgumentException e) {
            log.warn("[FORGOT_PASSWORD] ÉCHEC uid={} : {}", uid, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("FORGOT_PASSWORD_FAILED", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[FORGOT_PASSWORD] ERREUR uid={} : {}", uid, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("INTERNAL_ERROR", "Une erreur interne est survenue"));
        }

        log.info("[FORGOT_PASSWORD] Code envoyé à {} pour uid={}", email, uid);
        return ResponseEntity.ok(new ErrorResponse("RESET_CODE_SENT",
                "Un code de réinitialisation a été envoyé à votre adresse email"));
    }

    @PostMapping("/search-uid")
    public ResponseEntity<?> searchUid(
            @RequestBody SearchUidRequestDTO request) {

        String nom = request.getNom();
        String prenom = request.getPrenom();
        String email = request.getEmail();
        String profil = request.getProfil();
        String typeEtablissement = request.getTypeEtablissement();
        String ville = request.getVille();
        String etablissement = request.getEtablissement();

        log.info("[SEARCH_UID] Demande nom={}, prenom={}, email={}, profil={}, type={}, ville={}, etab={}",
                nom, prenom, email, profil, typeEtablissement, ville, etablissement);

        if (nom == null || nom.isBlank()) {
            throw new ChampsObligatoiresException("Le nom est obligatoire");
        }
        if (prenom == null || prenom.isBlank()) {
            throw new ChampsObligatoiresException("Le prénom est obligatoire");
        }
        if (email == null || email.isBlank()) {
            throw new ChampsObligatoiresException("L'email est obligatoire");
        }
        if (profil == null || profil.isBlank()) {
            throw new ChampsObligatoiresException("Le profil est obligatoire");
        }
        if (typeEtablissement == null || typeEtablissement.isBlank()) {
            throw new ChampsObligatoiresException("Le type d'établissement est obligatoire");
        }
        if (ville == null || ville.isBlank()) {
            throw new ChampsObligatoiresException("La ville est obligatoire");
        }
        if (etablissement == null || etablissement.isBlank()) {
            throw new ChampsObligatoiresException("L'établissement est obligatoire");
        }

        List<Object[]> results;
        Collection<String> sirens = resolveSirens(typeEtablissement, ville, etablissement);
        log.info("[SEARCH_UID] Filtre structures : {} SIREN(s) pour profil={}", sirens.size(), profil);
        results = aPersonneRepository.searchByNomPrenomAndCategorieAndSirens(nom, prenom, profil, sirens);
        log.info("[SEARCH_UID] {} résultat(s) DB pour nom={}, prenom={}", results.size(), nom, prenom);

        if (!results.isEmpty()) {
            results = results.stream()
                    .filter(row -> {
                        String emailA = (String) row[3];
                        String emailPersonnel = (String) row[4];
                        if (email.equalsIgnoreCase(emailA) || email.equalsIgnoreCase(emailPersonnel)) {
                            return true;
                        }
                        Long personId = (Long) row[2];
                        List<CerbereConfirmation> confirmed = cerbereConfirmationRepository.findConfirmedByPersonId(personId);
                        return confirmed.stream().anyMatch(c -> email.equalsIgnoreCase(c.getMail()));
                    })
                    .collect(Collectors.toList());
            log.info("[SEARCH_UID] {} résultat(s) après filtrage email", results.size());
        }

        if (results.isEmpty()) {
            return ResponseEntity.ok(new ErrorResponse("SEARCH_NO_RESULT",
                    "Aucun utilisateur trouvé avec ces informations"));
        }

        List<SearchUidResponseDTO> uids = results.stream()
                .map(row -> new SearchUidResponseDTO((String) row[0], (String) row[1]))
                .collect(Collectors.toList());

        log.info("[SEARCH_UID] {} résultat(s) trouvé(s) pour nom={}, prenom={}", uids.size(), nom, prenom);
        return ResponseEntity.ok(uids);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {

        String uid = request.getUid();
        String maskedCode = (request.getCode() != null && request.getCode().length() >= 2)
                ? request.getCode().substring(0, 2) + "****"
                : "****";

        log.info("[RESET_PASSWORD] Demande uid={}, code={}, charteAccepted={}", uid, maskedCode, request.isCharteAccepted());

        emailVerificationService.processResetPassword(
                uid, request.getCode(),
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
        return ResponseEntity.ok(new CharteStatusResponse(charteRequired, charteUrl, charteSignee));
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
        List<String> profils = java.util.Arrays.stream(fr.recia.mce.api.escomceapi.db.enums.EnumCategorie.values())
                .map(fr.recia.mce.api.escomceapi.db.enums.EnumCategorie::name)
                .collect(Collectors.toList());
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
        byte[] image = personneService.getAvatar(uid);
        if (image == null) {
            throw new PersonneNotFoundException("Avatar non trouvé pour l'uid : " + uid);
        }
        return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .body(image);
    }

    private Collection<String> resolveSirens(String typeEtablissement, String ville, String etablissement) {
        SurType surType;
        try {
            surType = SurType.valueOf(typeEtablissement.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ChampsObligatoiresException("Type inconnu : " + typeEtablissement
                    + ". Valeurs acceptees : " + SurType.acceptedValues());
        }
        return structureService.getAllStructures().stream()
                .filter(s -> surType.matches(s.getType()))
                .filter(s -> ville.equalsIgnoreCase(s.getVille()))
                .filter(s -> etablissement.equals(s.getId()))
                .map(IExternalStructure::getId)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String getCurrentUid() {
        String sub = soffitHolder.getSub();
        if (sub == null || sub.isBlank() || GUEST_USER.equals(sub)) {
            throw new AccessDeniedException("Utilisateur non authentifié");
        }
        return sub;
    }
}
