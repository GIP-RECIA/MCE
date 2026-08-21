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

import java.util.Arrays;

import fr.recia.mce.api.escomceapi.db.enums.SurType;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import lombok.Getter;

@Getter
public class StructureResponseDTO {

    private final String siren;
    private final String uai;
    private final String nom;
    private final String type;
    private final String surtype;
    private final String ville;
    private final String source;
    private final String domsource;
    private final String[] domaines;

    public StructureResponseDTO(IExternalStructure s) {
        this.siren = s.getId();
        this.uai = s.getUai();
        this.nom = s.getName();
        this.type = s.getType();
        this.ville = s.getVille();
        this.source = s.getSource();
        this.domaines = s.getDomaines();

        SurType st = SurType.fromLdapType(s.getType());
        this.surtype = st != null ? st.name() : null;

        this.domsource = (s.getSource() != null && s.getSource().contains("-"))
                ? s.getSource().substring(0, s.getSource().indexOf('-'))
                : null;
    }
}
