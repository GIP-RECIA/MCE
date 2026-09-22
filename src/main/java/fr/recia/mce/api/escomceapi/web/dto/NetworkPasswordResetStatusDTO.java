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

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Statut du parcours « mot de passe réseau » (équivalent Cerbère NewPassRezo) pour le compte
 * authentifié : indique au portail si le compte est éligible (CVDL ntPass sans mot de passe local
 * stocké) et si un code de changement est déjà en attente.
 */
@Data
@AllArgsConstructor
public class NetworkPasswordResetStatusDTO {

    private boolean eligible;

    private boolean pendingCode;

}