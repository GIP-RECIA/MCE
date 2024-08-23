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

public enum EnumOnglet {

    /*
     * Menu tab de mes informations générales
     */
    GENERALE,

    /**
     * Menu tab de mes services ENT
     */
    SERVICE,

    /**
     * Menu tab de mes responsables
     */
    PARENT_ELEVE,

    /**
     * Menu tab de mes relations
     */
    RELATION_ELEVE,

    /**
     * Menu tab de mes apprentis
     */
    APPRENTIS;

    public static EnumOnglet fromString(String code) {
        for (EnumOnglet type : EnumOnglet.values()) {
            if (type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
