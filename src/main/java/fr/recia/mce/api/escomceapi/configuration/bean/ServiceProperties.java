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

import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Validated
public class ServiceProperties {

    private ClasseCalculatorProperties classeProperties = new ClasseCalculatorProperties();
    private GrpPedagoCalculator grpPedagoProperties = new GrpPedagoCalculator();
    private CustomParams customParams = new CustomParams();

    @Data
    @Validated
    public static class ClasseCalculatorProperties {

        private String ldapAttributsClasse;

        private String regexSirenAndClasse;

        private int groupSiren;

        private int groupClasse;

        private int groupMatiere;

    }

    @Getter
    @Setter
    @Validated
    public static class GrpPedagoCalculator extends ClasseCalculatorProperties {

        public GrpPedagoCalculator() {
            this.setLdapAttributsClasse(getLdapAttributsClasse());
            this.setRegexSirenAndClasse(getRegexSirenAndClasse());
            this.setGroupSiren(getGroupSiren());
            this.setGroupClasse(getGroupClasse());
            this.setGroupMatiere(getGroupMatiere());
        }

    }

    @Data
    @Validated
    public static class CustomParams {

        private String lienEdu;
        private String lienPassEtab;
        private String domaineEtabRecia;

    }

    @Override
    public String toString() {
        return "{\n\"ServiceProperties\":{"
                + ",\n \"classeProperties\":" + classeProperties
                + ",\n \"grpPedagoProperties\":" + grpPedagoProperties
                + ",\n \"CustomParams\":" + customParams
                + "\n}\n}";
    }

}
