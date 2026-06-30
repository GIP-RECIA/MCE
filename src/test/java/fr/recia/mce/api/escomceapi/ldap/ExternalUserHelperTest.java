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

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ExternalUserHelperTest {

    @Test
    void shouldBuildAttributesSet() {
        ExternalUserHelper helper = new ExternalUserHelper(
                "uid", "displayName", "mail", "searchAttr",
                "groupAttr", "eleveRelation", "eleveTuteur",
                "tuteurEleve", "enseignement", "codeMatiere",
                "avatar", Collections.emptySet(), Collections.emptySet(), "dnSubPath");

        Set<String> attrs = helper.getAttributes();

        assertThat(attrs)
                .contains("uid", "displayName", "mail", "searchAttr",
                        "groupAttr", "eleveRelation", "eleveTuteur",
                        "tuteurEleve", "enseignement", "codeMatiere",
                        "avatar")
                .hasSize(11);
    }

    @Test
    void shouldIncludeOtherAttributes() {
        ExternalUserHelper helper = new ExternalUserHelper(
                "uid", null, null, null,
                null, null, null,
                null, null, null,
                null, Set.of("other1"), Set.of("otherDisplay1"), null);

        Set<String> attrs = helper.getAttributes();

        assertThat(attrs).contains("uid", "other1", "otherDisplay1");
    }

    @Test
    void shouldGetAndSetProperties() {
        ExternalUserHelper helper = new ExternalUserHelper();
        helper.setUserIdAttribute("uid");
        helper.setUserDisplayNameAttribute("displayName");
        helper.setUserEmailAttribute("mail");

        assertThat(helper.getUserIdAttribute()).isEqualTo("uid");
        assertThat(helper.getUserDisplayNameAttribute()).isEqualTo("displayName");
        assertThat(helper.getUserEmailAttribute()).isEqualTo("mail");
    }

}
