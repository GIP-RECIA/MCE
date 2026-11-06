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

package fr.recia.manager.services;

import fr.recia.manager.db.dto.DatabaseUserDto;
import fr.recia.manager.services.exception.InactiveAccountException;
import fr.recia.manager.services.exception.InvalidCodeException;
import fr.recia.manager.web.dto.NetworkPasswordResetStatusDTO;
import fr.recia.manager.web.dto.PasswordChangeRequestDTO;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChangePasswordService {

    private final PersonneService personneService;
    private final PasswordService passwordService;
    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);
    // TODO : Marqueur actif complet pour les comptes valides SANS mot de passe local : "{SSHA}Active" + 50 "="
    public static final String ACTIVE_WITHOUT_PASSWORD = "{SSHA}Active" + "=".repeat(50);

    public ChangePasswordService(PersonneService personneService, PasswordService passwordService){
        this.personneService = personneService;
        this.passwordService = passwordService;
    }

    /**
     * Change le mot de passe + mot de passe réseau si besoin
     */
    public void changePassword(UserDto user, PasswordChangeRequestDTO request) {
        final String uid = user.getUid();
        final boolean editable = user.isCanEditPassword();
        log.info("[CHANGE_PASSWORD] uid={} computePassEditable={} (enumPublic={}, isNtPass={}, source={}, eduConnect={})", uid, editable, user.getEnumPublic(), user.getDatabaseUserDto().isNtPass(), user.getDatabaseUserDto().getSource(), user.isEduconnect());
        if (!editable) {
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre mot de passe");
        }
        // TODO : vérifier qu'on a pas déjà tapé un mot de passe, car si c'est le cas pour le changer on va quand même demander l'ancien
        final boolean noOldPass = false;

        try {
            validateRequest(request, noOldPass);
        } catch (WeakPasswordException e) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Mot de passe trop faible", uid);
            throw e;
        } catch (IllegalArgumentException e) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : Erreur de vérification du mot de passe | Détail : {}", uid, e.getMessage());
            throw e;
        }

        // Dans le cas ou il faut donner l'ancien mot de passe
        if (!noOldPass) {
            // Si l'ancien mot de passe matche, alors on change le mot de passe
            if (passwordService.verifyPassword(user, request.getOldPass(), false)) {
                passwordService.updatePassword(user, request.getNewPass(), "CHANGE_PASSWORD");
            } else {
                specialLog.error("Audit [CHANGE_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : Erreur lors de la vérification du mot de passe", uid);
                throw new RuntimeException("Erreur technique lors de la vérification : ");
            }
            // Dans le cas ou on a pas besoin de donner l'ancien mot de passe, on change sans vérifier
        } else {
            passwordService.updatePassword(user, request.getNewPass(), "CHANGE_PASSWORD");
        }
        personneService.clearUserCaches(uid);
    }

    /**
     * Indique si un utilisateur à un mot de passe réseau qu'il peut changer sans avoir à fournir d'ancien mot de passe
     */
    public NetworkPasswordResetStatusDTO canChangeNetworkPasswordWithoutOldPassword(UserDto user) {
        return new NetworkPasswordResetStatusDTO(user.isPasswordNetwork() && hasOldPassword(user));
    }

    /**
     * Indique si l'utilisateur a un ancien mot de passe ou non
     */
    private boolean hasOldPassword(UserDto user){
        return user.getDatabaseUserDto().getPassword().equals(ACTIVE_WITHOUT_PASSWORD);
    }

    /**
     * Vérifie qu'une requête de modification de mot de passe est correcte
     * Lève une exception si ce n'est pas le cas
     */
    private void validateRequest(PasswordChangeRequestDTO request, boolean noOldPass) {
        if (request == null) {
            throw new IllegalArgumentException("Requête invalide");
        }
        if (!noOldPass && (request.getOldPass() == null || request.getOldPass().isBlank())) {
            throw new IllegalArgumentException("Ancien mot de passe requis");
        }
        if (request.getNewPass() == null || request.getNewPass().isBlank()) {
            throw new IllegalArgumentException("Nouveau mot de passe requis");
        }
        if (request.getOldPass() != null && request.getOldPass().equals(request.getNewPass())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien");
        }
        if (!request.getNewPass().equals(request.getConfirmPass())) {
            throw new IllegalArgumentException("La confirmation du mot de passe ne correspond pas");
        }
        passwordService.isPasswordStrongEnough(request.getNewPass());
    }

}
