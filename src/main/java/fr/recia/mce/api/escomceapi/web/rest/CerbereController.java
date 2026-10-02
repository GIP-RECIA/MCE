package fr.recia.mce.api.escomceapi.web.rest;

import fr.recia.mce.api.escomceapi.security.AppUser;
import fr.recia.mce.api.escomceapi.services.ActivationService;
import fr.recia.mce.api.escomceapi.services.CharteService;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.exception.ApiException;
import fr.recia.mce.api.escomceapi.services.exception.ChampsObligatoiresException;
import fr.recia.mce.api.escomceapi.services.exception.ContactAdminException;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import fr.recia.mce.api.escomceapi.services.exception.ResendCooldownActiveException;
import fr.recia.mce.api.escomceapi.web.dto.ActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationResultDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationSelfRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationStatusResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.CharteAcceptRequest;
import fr.recia.mce.api.escomceapi.web.dto.CharteStatusResponse;
import fr.recia.mce.api.escomceapi.web.dto.ConnexionActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ConnexionActivationResponseDTO;
import fr.recia.mce.api.escomceapi.web.dto.ForgotPasswordRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetStatusDTO;
import fr.recia.mce.api.escomceapi.web.dto.RecoverUidRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ResetPasswordRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.VerifyEmailRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/cerbere")
public class CerbereController {

    private final EmailVerificationService emailVerificationService;
    private final CharteService charteService;
    private final ActivationService activationService;
    private final PersonneService personneService;

    public CerbereController(EmailVerificationService emailVerificationService, CharteService charteService, ActivationService activationService, PersonneService personneService) {
        this.emailVerificationService = emailVerificationService;
        this.charteService = charteService;
        this.activationService = activationService;
        this.personneService = personneService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequestDTO request) {

        String login = request.getLogin();
        String email = request.getEmail();
        String profil = request.getProfil();

        log.info("[FORGOT_PASSWORD] Demande login={}, email={}, profil={}", login, email, profil);

        try {
            emailVerificationService.sendPasswordResetCode(login, email, profil);
        } catch (ResendCooldownActiveException e) {
            // Anti-double-clic : laisser le GlobalExceptionHandler répondre 429 avec le temps restant.
            throw e;
        } catch (ContactAdminException e) {
            log.warn("[FORGOT_PASSWORD] CONTACT_ADMIN login={} : {}", login, e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("CONTACT_ADMIN_REQUIRED", e.getMessage()));
        } catch (IllegalArgumentException | ApiException e) {
            log.warn("[FORGOT_PASSWORD] ÉCHEC login={} : {}", login, e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("FORGOT_PASSWORD_FAILED", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("[FORGOT_PASSWORD] ERREUR login={} : {}", login, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Une erreur interne est survenue"));
        }

        log.info("[FORGOT_PASSWORD] Code envoyé à {} pour login={}", email, login);
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

        String login = request.getLogin();
        log.info("[RESET_PASSWORD] Demande login={}, charteAccepted={}", login, request.isCharteAccepted());

        emailVerificationService.processResetPassword(
            login, request.getResetToken(), request.getCode(),
            request.getNewPassword(), request.getConfirmPassword(),
            request.isCharteAccepted());

        log.info("[RESET_PASSWORD] Succès login={}", login);
        return ResponseEntity.ok(new ErrorResponse("PASSWORD_RESET_SUCCESS",
            "Votre mot de passe a été réinitialisé avec succès"));
    }

    /**
     * Applique le changement direct du mot de passe réseau (conforme à l'ancienne application : pas
     * d'ancien mot de passe, pas de code).
     */
    @PostMapping("/network-password/reset")
    public ResponseEntity<?> networkPasswordReset(@Valid @RequestBody NetworkPasswordResetRequestDTO request, @AuthenticationPrincipal AppUser principal) {

        String uid = principal.getUid();
        log.info("[NETWORK_PASSWORD_RESET] Application pour uid={}", uid);

        emailVerificationService.changeNetworkPassword(
            uid, request.getNewPassword(), request.getConfirmPassword());

        log.info("[NETWORK_PASSWORD_RESET] Succès uid={}", uid);
        return ResponseEntity.ok(new ErrorResponse("NETWORK_PASSWORD_RESET_SUCCESS",
            "Votre mot de passe réseau a été modifié avec succès"));
    }

    /**
     * Statut du parcours « mot de passe réseau » du compte authentifié : éligibilité (CVDL ntPass sans
     * mot de passe local stocké, état Valide, détectée côté serveur car {@code ntPass} n'est pas présent
     * dans le jeton OIDC). Permet au portail d'afficher le bon écran.
     */
    @GetMapping("/network-password/status")
    public ResponseEntity<NetworkPasswordResetStatusDTO> networkPasswordStatus(@AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();
        NetworkPasswordResetStatusDTO status = emailVerificationService.getNetworkPasswordResetStatus(uid);
        log.info("[NETWORK_PASSWORD_RESET] Statut uid={} eligible={}", uid, status.isEligible());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO request, @AuthenticationPrincipal AppUser principal) {

        // Cas public (parcours d'activation sans jeton) : l'identifiant (login.nom) doit être fourni dans le corps.
        String uid = principal.getUid();
        if (uid == null) {
            uid = request.getLogin();
        }
        if (uid == null || uid.isBlank()) {
            log.warn("[VERIFY_EMAIL] ÉCHEC : aucun identifiant (jeton absent et champ login vide)");
            throw new ChampsObligatoiresException("L'identifiant est obligatoire");
        }

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
     */
    @PostMapping("/charte/accept")
    public ResponseEntity<CharteStatusResponse> accepterCharte(@Valid @RequestBody CharteAcceptRequest request, @AuthenticationPrincipal AppUser principal) {
        String uid = principal.getUid();

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

        // La charte est nécessairement signée ici : soit elle l'était déjà, soit elle vient de l'être.
        // Plus besoin de re-soumettre la requête à la base.
        String charteUrl = charteService.getCharteUrl(uid);
        return ResponseEntity.ok(new CharteStatusResponse(false, charteUrl, true));
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
     * L'identifiant est le {@code login.nom} saisi par l'utilisateur (ou un uid/alias, insensible à la casse).
     */
    @GetMapping("/activation/status")
    public ResponseEntity<ActivationStatusResponseDTO> activationStatus(@RequestParam String login) {
        log.info("[ACTIVATION][STATUS] Demande login={}", login);
        ActivationStatusResponseDTO status = activationService.getActivationStatus(login);
        log.info("[ACTIVATION][STATUS] Succès uid={} etape={}", status.getUid(), status.getEtapeSuivante());
        return ResponseEntity.ok(status);
    }

    /**
     * Point d'entrée PASSWORD du parcours d'activation : charte + mot de passe (et éventuellement email) puis activation du compte.
     */
    @PostMapping("/activation/password")
    public ResponseEntity<ActivationResultDTO> activerCompte(@Valid @RequestBody ActivationRequestDTO request, @AuthenticationPrincipal AppUser principal) {

        log.info("[ACTIVATION][PASSWORD] Demande login={}, charteAccepted={}", request.getLogin(), request.isCharteAccepted());
        ActivationResultDTO result = activationService.activate(request);
        log.info("[ACTIVATION][PASSWORD] Succès uid={} etat={}", result.getUid(), result.getEtat());
        return ResponseEntity.ok(result);
    }

    /**
     * Point d'entrée d'activation dédié aux profils SSO déjà authentifiés (EDUCATION, AGRI, CVDL, ELEVE_EDUC, ...) :
     * Aucun mot de passe n'est requis (ces profils ne se connectent pas par mot de passe local).
     */
    @PostMapping("/activation/self")
    public ResponseEntity<ActivationResultDTO> activerCompteCourant(@Valid @RequestBody ActivationSelfRequestDTO request, @AuthenticationPrincipal AppUser principal) {

        String uid = principal.getUid();
        log.info("[ACTIVATION][SELF] Demande uid={}, charteAccepted={}", uid, request.isCharteAccepted());

        ActivationRequestDTO inner = new ActivationRequestDTO();
        inner.setLogin(uid);
        inner.setCharteAccepted(request.isCharteAccepted());
        inner.setEmail(request.getEmail());

        ActivationResultDTO result = activationService.activate(inner);
        log.info("[ACTIVATION][SELF] Succès uid={} etat={}", result.getUid(), result.getEtat());
        return ResponseEntity.ok(result);
    }

}
