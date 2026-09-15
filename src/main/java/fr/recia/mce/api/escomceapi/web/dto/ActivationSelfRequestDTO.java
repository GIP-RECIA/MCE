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

import lombok.Getter;
import lombok.Setter;

/**
 * Requête d'activation du compte courant (profil SSO déjà authentifié) : le uid est
 * résolu côté serveur à partir du jeton, jamais confié au client.
 */
@Getter
@Setter
public class ActivationSelfRequestDTO {

    /** Signature de la charte (obligatoire si le compte n'a pas encore signé). */
    private boolean charteAccepted;

    /** Email personnel saisi pendant l'étape COURRIEL (facultatif selon le profil). */
    private String email;

}