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
package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.Data;

@Data
public class SoffitProperties {

    private String jwtSignatureKey;

    /**
     * Mode strict (recommandé en production) : le `sub` n'est lu que depuis le
     * SecurityContext (principal HMAC vérifié par le filtre Soffit). Quand `false`
     * (valeur par défaut, rétro-compatible), le SoffitInterceptor retombe sur le
     * décodage tolérant du payload pour les déploiements dont le reverse proxy
     * injecte un JWT non signé avec la clé configurée.
     */
    private boolean requireAuthenticatedPrincipal;

    @Override
    public String toString() {
        return "\"SoffitProperties\": {" +
                "\n\t\"jwtSignatureKey\": \"" + jwtSignatureKey + "\"" +
                ",\n\t\"requireAuthenticatedPrincipal\": " + requireAuthenticatedPrincipal +
                "\n}";
    }

}
