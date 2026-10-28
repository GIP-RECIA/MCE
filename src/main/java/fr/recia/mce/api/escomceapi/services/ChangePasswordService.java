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
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.services.factories.UserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChangePasswordService {

    private final PersonneService personneService;
    private final PasswordService passwordService;
    private final UserDTOFactory userDTOFactory;

    public ChangePasswordService(PersonneService personneService, PasswordService passwordService, UserDTOFactory userDTOFactory){
        this.personneService = personneService;
        this.passwordService = passwordService;
        this.userDTOFactory = userDTOFactory;
    }

    public void changePassword(String uid, PasswordChangeRequestDTO req) {
        DatabaseUserDto user = personneService.getUserDatabase(uid);
        EnumPublic pub = user.getEnumPublic();
        log.info("[CHANGE_PASSWORD] Diagnostique uid={} : enumPublic brut={}, isNtPass={}, source={}", uid, pub, user.isNtPass(), user.getSource());
        // TODO : pourquoi vérifier si c'est null ???? s'il faut qu'on la recalcule à chaque fois de toute manière
        if (pub == null) {
            try {
                userDTOFactory.evalPublic(user);
                pub = user.getEnumPublic();
                log.info("[CHANGE_PASSWORD] Après evalPublic uid={} : enumPublic={}, connectOk={}, educonnect={}, isNtPass={}, source={}",
                    uid, pub, pub == null ? "N/A" : pub.isConnectOk(), pub == null ? "N/A" : pub.isEduconnect(), user.isNtPass(), user.getSource());
            } catch (Exception e) {
                log.error("Échec de l'évaluation du profil public pour le changement de mot de passe [uid={}] - Détail : {}",
                    uid, e.getMessage());
            }
        }

        EnumPublic effectivePub = user.getEnumPublic();
        boolean eduConnect = effectivePub != null && effectivePub.isEduconnect();
        boolean editable = userDTOFactory.isPasswordEditable(user);
        log.info("[CHANGE_PASSWORD] uid={} computePassEditable={} (enumPublic={}, isNtPass={}, source={}, eduConnect={})",
            uid, editable, effectivePub, user.isNtPass(), user.getSource(), eduConnect);
        if (!editable) {
            if (eduConnect) {
                throw new AccessDeniedException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
            }
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre mot de passe");
        }

        passwordService.changePassword(user, req);
        personneService.clearUserCaches(uid);
    }

}
