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
import fr.recia.mce.api.escomceapi.db.services.DatabaseUserService;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChangePasswordService {

    private final PersonneService personneService;
    private final PasswordService passwordService;

    public ChangePasswordService(PersonneService personneService, PasswordService passwordService){
        this.personneService = personneService;
        this.passwordService = passwordService;
    }

    public void changePassword(String uid, PasswordChangeRequestDTO req) {
        UserDto userDto = personneService.getUser(uid);
        boolean eduConnect = userDto.getEnumPublic() != null && userDto.getEnumPublic().isEduconnect();
        boolean editable = personneService.canEditPassword(userDto);
        log.info("[CHANGE_PASSWORD] uid={} computePassEditable={} (enumPublic={}, isNtPass={}, source={}, eduConnect={})",
            uid, editable, userDto.getEnumPublic(), userDto.getDatabaseUserDto().isNtPass(), userDto.getDatabaseUserDto().getSource(), eduConnect);
        if (!editable) {
            if (eduConnect) {
                throw new AccessDeniedException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
            }
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre mot de passe");
        }
        passwordService.changePassword(userDto.getDatabaseUserDto(), req);
        personneService.clearUserCaches(uid);
    }

}
