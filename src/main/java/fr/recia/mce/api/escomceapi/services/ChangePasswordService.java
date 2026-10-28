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

package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.web.dto.NetworkPasswordResetStatusDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChangePasswordService {

    private final PersonneService personneService;
    private final PasswordService passwordService;
    private final ResendPolicyService resendPolicyService;

    public ChangePasswordService(PersonneService personneService, PasswordService passwordService, ResendPolicyService resendPolicyService){
        this.personneService = personneService;
        this.passwordService = passwordService;
        this.resendPolicyService = resendPolicyService;
    }

    public void changePassword(UserDto userDto, PasswordChangeRequestDTO req) {
        boolean eduConnect = userDto.getEnumPublic() != null && userDto.getEnumPublic().isEduconnect();
        boolean editable = personneService.canEditPassword(userDto);
        log.info("[CHANGE_PASSWORD] uid={} computePassEditable={} (enumPublic={}, isNtPass={}, source={}, eduConnect={})",
            userDto.getLdapUserDto().getId(), editable, userDto.getEnumPublic(), userDto.getDatabaseUserDto().isNtPass(), userDto.getDatabaseUserDto().getSource(), eduConnect);
        if (!editable) {
            if (eduConnect) {
                throw new AccessDeniedException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
            }
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre mot de passe");
        }
        passwordService.changePassword(userDto.getDatabaseUserDto(), req);
        personneService.clearUserCaches(userDto.getLdapUserDto().getId());
    }

    /**
     * Parcours « mot de passe réseau » pour un compte déjà authentifié dans Mon Compte. Conforme à l'ancienne application Cerbère : un compte CVDL ntPass
     * sans mot de passe local stocké ({@code noOldPass}) ne fournit ni ancien mot de passe ni code de
     * vérification — il change directement son mot de passe réseau (nouveau + confirmation). L'état du compte
     * doit être Valide ; la charte n'est pas re-demandée (l'ancien écran ne la redemandait pas non plus).
     *
     * @param uid identifiant de l'utilisateur authentifié (provenant du jeton, jamais du corps de requête)
     */
    @Transactional
    public void changeNetworkPassword(String uid, String newPassword, String confirmPassword) {
        log.info("[NETWORK_PASSWORD_RESET] Début changeNetworkPassword uid={}", uid);
        UserDto userDto = personneService.getUser(uid);
        if (uid == null || uid.isBlank()) {
            throw new InvalidCodeException("Le changement de mot de passe réseau a échoué.");
        }
        assertAccountActive(userDto.getDatabaseUserDto(), "NETWORK_PASSWORD_RESET");

        // Réservé aux comptes à mot de passe réseau seul : un compte disposant d'un mot de passe local
        // stocké doit passer par le changement avec l'ancien mot de passe.
        if (!passwordService.isNoOldPassEligible(userDto.getDatabaseUserDto())) {
            log.warn("[NETWORK_PASSWORD_RESET] REFUSÉ pour l'utilisateur [{}] - Raison : compte non éligible au mot de passe réseau seul", uid);
            throw new InvalidCodeException(
                "Ce parcours est réservé aux comptes à mot de passe réseau seul. Si votre compte dispose d'un mot de passe local, utilisez le changement avec votre ancien mot de passe.");
        }

        // Même règle de réinitialisation que le parcours public : CVDL ntPass autorisé.
        resendPolicyService.assertPasswordResetAllowed(userDto);
        passwordService.resetPassword(userDto.getDatabaseUserDto(), newPassword, confirmPassword);
        personneService.clearUserCaches(userDto.getDatabaseUserDto().getUid());

        log.info("[NETWORK_PASSWORD_RESET] Succès uid={}", userDto.getDatabaseUserDto().getUid());
    }

    /**
     * Statut du parcours « mot de passe réseau » du compte authentifié, exposé au portail Mon Compte afin
     * qu'il affiche l'écran de changement direct pour les comptes éligibles (CVDL ntPass sans mot de passe
     * local stocké et état Valide). {@code ntPass} n'étant pas présent dans le jeton OIDC, l'éligibilité se
     * calcule côté serveur.
     *
     * @param uid identifiant de l'utilisateur authentifié (provenant du jeton, jamais du corps de requête)
     * @return {@link NetworkPasswordResetStatusDTO} (jamais {@code null} ; uid inconnu ⇒ non éligible)
     */
    public NetworkPasswordResetStatusDTO getNetworkPasswordResetStatus(String uid) {
        log.info("[NETWORK_PASSWORD_RESET] Statut uid={}", uid);
        UserDto userDto = personneService.getUser(uid);
        boolean eligible = false;
        DatabaseUserDto databaseUserDto = userDto.getDatabaseUserDto();
        if (databaseUserDto != null) {
            if (resendPolicyService.isInactiveAccount(databaseUserDto)) {
                log.info("[NETWORK_PASSWORD_RESET] Compte non actif uid={}, statut non éligible", uid);
            } else {
                eligible = passwordService.isNoOldPassEligible(databaseUserDto);
            }
        } else {
            log.warn("[NETWORK_PASSWORD_RESET] Profil non chargeable pour uid={}, statut non éligible", uid);
        }

        log.info("[NETWORK_PASSWORD_RESET] Statut uid={} eligible={}", uid, eligible);
        return new NetworkPasswordResetStatusDTO(true);
    }

    /**
     * Garde commune aux parcours de réinitialisation et mot de passe réseau : refuse un compte dont l'état
     * n'est pas Valide (supprimé, bloqué, en attente...).
     */
    private void assertAccountActive(DatabaseUserDto person, String logPrefix) {
        if (resendPolicyService.isInactiveAccount(person)) {
            log.warn("[{}] État '{}' ≠ '{}' pour uid={}", logPrefix, person.getEtat(), ResendPolicyService.VALID_ACCOUNT_STATE, person.getUid());
            // TODO : c'est quoi ce message d'erreur ??????
            throw new InactiveAccountException("Votre compte n'est pas actif. Contactez votre administrateur.");
        }
    }

}
