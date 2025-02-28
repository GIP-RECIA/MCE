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
package fr.recia.mce.api.escomceapi.services.factories;

import javax.validation.constraints.NotNull;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.web.dto.InfoGeneralDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;

public interface IUserDTOFactory {

    APersonne from(@NotNull final UserDTO dtObject);

    UserDTO from(final IExternalUser extModel, final boolean withInternal);

    UserDTO from(final PersonneDTO model, final IExternalUser extModel);

    UserDTO from(@NotNull final PersonneDTO model);

    UserDTO from(@NotNull final String uid);

    InfoGeneralDTO showGeneralInfo();

    UserDTO getCurrentUser();

    String changePassword(final String uid, final PasswordChangeRequest req);
}
