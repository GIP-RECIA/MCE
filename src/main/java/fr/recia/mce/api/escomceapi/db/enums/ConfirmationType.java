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
package fr.recia.mce.api.escomceapi.db.enums;

import lombok.Getter;

@Getter
public enum ConfirmationType {

    EMAIL_VERIFICATION("VERIFY:"),
    PASSWORD_RESET("RESET:");

    private final String codePrefix;

    ConfirmationType(String codePrefix) {
        this.codePrefix = codePrefix;
    }

    /**
     * Retourne le préfixe SQL pour les clauses LIKE JPQL.
     * Ex: {@code CONCAT(:prefix, '%')} avec {@code EMAIL_VERIFICATION.getLikePattern()} = {@code "VERIFY:%"}
     */
    public String getLikePattern() {
        return codePrefix + "%";
    }

    /**
     * Déduit le type à partir du préfixe du code hashé stocké en base.
     * Retourne null si le préfixe ne correspond à aucun type connu.
     */
    public static ConfirmationType fromCode(String code) {
        if (code == null) return null;
        for (ConfirmationType type : values()) {
            if (code.startsWith(type.codePrefix)) return type;
        }
        return null;
    }
}
