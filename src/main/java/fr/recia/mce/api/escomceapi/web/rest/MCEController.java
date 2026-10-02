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

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.enums.SurType;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.security.AppUser;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.EmailUpdateRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.StructureResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/mce")
public class MCEController {

    private final FonctionService fonctionService;
    private final PersonneService personneService;
    private final IRelationEleveService relationEleveService;
    private final IUserDTOFactory userDTOFactory;
    private final EmailVerificationService emailVerificationService;
    private final IStructureService structureService;

    public MCEController(FonctionService fonctionService, PersonneService personneService, IRelationEleveService relationEleveService,
                         IUserDTOFactory userDTOFactory, EmailVerificationService emailVerificationService, IStructureService structureService) {
        this.fonctionService = fonctionService;
        this.personneService = personneService;
        this.relationEleveService = relationEleveService;
        this.userDTOFactory = userDTOFactory;
        this.emailVerificationService = emailVerificationService;
        this.structureService = structureService;
    }


    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid(@AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        PersonneDTO personne = personneService.retrievePersonnebyUid(uid);

        if (personne == null) {
            throw new PersonneNotFoundException("Personne non trouvée pour l'uid : " + uid);
        }

        return ResponseEntity.ok(personne);
    }

    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap(@AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        IExternalUser user = personneService.retrievePersonLdap(uid);

        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur LDAP non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE(@AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        UserDTO user = userDTOFactory.from(uid);

        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur non trouvé pour l'uid : " + uid);
        }

        return ResponseEntity.ok(user);
    }

    // Le path variable est un UID (sub LDAP/relation), PAS un id numérique de la base
    // personne. La vérification d'accès et la résolution du profil utilisent la même
    // sémantique d'uid (cf. canAccessRelationProfile et UserDTOFactory.from).
    // TODO : uid dans la route c'est bizarre
    @GetMapping("/{uid}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String uid, @AuthenticationPrincipal AppUser principal) {
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

    @GetMapping("/fonction/{id}")
    public ResponseEntity<Collection<FonctionDTO>> getFonctionsOfPerson(@PathVariable Long id, @AuthenticationPrincipal AppUser principal) {
        String currentUid = principal.getUid();
        if (!canAccessFunctionProfile(currentUid, id)) {
            log.warn("Audit [GET_FONCTIONS] : Tentative d'accès non autorisé aux fonctions de la personne id={} par [{}]", id, currentUid);
            throw new AccessDeniedException("Vous ne pouvez consulter que vos fonctions ou celles des personnes en relation avec vous");
        }
        Collection<FonctionDTO> fonctions = fonctionService.getAllFonctionOfPersonne(id);
        log.debug("Fonctions de la personne [id={}] : {}", id, fonctions);
        return new ResponseEntity<>(fonctions, HttpStatus.OK);

    }

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

    @PostMapping("/avatar")
    public ResponseEntity<Void> updateAvatar(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AppUser principal) throws Exception {
        String uid = principal.getUid();
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

    /**
     * Récupère l'avatar d'un utilisateur.
     */
    // TODO : pourquoi un endpoint public ???
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
