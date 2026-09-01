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
package fr.recia.mce.api.escomceapi.web.dto;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivationRequestDTO {

    @NotBlank(message = "L'identifiant est obligatoire")
    private String uid;

    private boolean charteAccepted;

    /** Email personnel saisi pendant l'étape COURRIEL (facultatif selon le profil). */
    private String email;

    /** Nouveau mot de passe ENT (obligatoire si le profil se connecte avec un mot de passe local). */
    private String newPassword;

    private String confirmPassword;

}