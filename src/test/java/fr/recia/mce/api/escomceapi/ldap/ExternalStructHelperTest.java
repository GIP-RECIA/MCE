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
package fr.recia.mce.api.escomceapi.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ExternalStructHelperTest {

    @Test
    void shouldBuildAttributesSet() {
        ExternalStructHelper helper = new ExternalStructHelper(
                "id", "name", "displayName", "type",
                "domaines", "jointure", "uai", "ville", "dnSubPath");

        Set<String> attrs = helper.getAttributes();

        assertThat(attrs)
                .contains("id", "name", "displayName", "type",
                        "domaines", "jointure", "uai", "ville")
                .hasSize(8);
    }

    @Test
    void shouldGetAndSetProperties() {
        ExternalStructHelper helper = new ExternalStructHelper();
        helper.setStructIdAttribute("id");
        helper.setStructNameAttribute("name");
        helper.setStructUaiAttribute("uai");

        assertThat(helper.getStructIdAttribute()).isEqualTo("id");
        assertThat(helper.getStructNameAttribute()).isEqualTo("name");
        assertThat(helper.getStructUaiAttribute()).isEqualTo("uai");
    }

}
