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

import java.util.HashSet;
import java.util.Set;

import javax.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Data
@Validated
public class CustomLdapProperties {

    private BranchProperties userBranch = new BranchProperties();

    private StructureBranchProperties structBranch = new StructureBranchProperties();

    @Data
    @Validated
    public static class BranchProperties {

        @NotBlank
        private String baseDN = "ou=people";

        @NotBlank
        private String idAttribute = "uid";

        @NotBlank
        private String displayNameAttribute = "displayName";

        @NotBlank
        private String mailAttribute = "mail";

        @NotBlank
        private String searchAttribute = "cn";

        @NotBlank
        private String groupAttribute = "isMemberOf";

        private String eleveRelation;

        private String eleveTuteurEntr;

        private String tuteurEleves;

        private String eleveEnseignements;

        private String codeMatiereEnseignement;

        @NonNull
        private Set<String> otherDisplayedAttributes = new HashSet<>();

        @NonNull
        private Set<String> otherBackendAttributes = new HashSet<>();

    }

    @Getter
    @Setter
    @Validated
    public static class StructureBranchProperties extends BranchProperties {

        private String domaines;

        private String nameStruct;

        private String structureJointure;

        private String skin;

        private String typeStruct;

        private String uai;

        private String ville;

        public StructureBranchProperties() {
            this.setBaseDN("ou=structures");
            this.setGroupAttribute("member");
            this.setIdAttribute("ENTStructureSIREN");
            this.setDisplayNameAttribute("ESCOStructureNomCourt");
        }

        @Override
        public String toString() {
            return "StructureBranchProperties [domaines=" + domaines + ", nameStruct=" + nameStruct
                    + ", structureJointure=" + structureJointure + ", skin=" + skin + ", typeStruct=" + typeStruct
                    + ", uai=" + uai + ", ville=" + ville + ", getBaseDN()=" + getBaseDN()
                    + ", getDisplayNameAttribute()=" + getDisplayNameAttribute() + ", getGroupAttribute()="
                    + getGroupAttribute() + ", getIdAttribute()=" + getIdAttribute() + "]";
        }

    }

    @Override
    public String toString() {
        return "{\n\"CustomLdapProperties\":{"
                + ",\n \"userBranch\":" + userBranch
                + ",\n \"structBranch\":" + structBranch
                + "\n}\n}";
    }

}
