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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalUserTest {

    private ExternalUser user;

    @BeforeEach
    void setUp() {
        user = new ExternalUser();
        user.setId("uid123");
        user.setDisplayName("John Doe");
        user.setEmail("john@test.com");
    }

    @Test
    void shouldGetAttributeByExactCase() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("mail", Arrays.asList("john@test.com"));
        attrs.put("uid", Arrays.asList("uid123"));
        user.setAttributes(attrs);

        assertThat(user.getAttribute("mail")).containsExactly("john@test.com");
        assertThat(user.getAttribute("uid")).containsExactly("uid123");
    }

    @Test
    void shouldGetAttributeByLowerCaseFallback() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("mail", Arrays.asList("john@test.com"));
        user.setAttributes(attrs);

        assertThat(user.getAttribute("MAIL")).containsExactly("john@test.com");
    }

    @Test
    void shouldReturnEmptyListWhenAttributeNotFound() {
        user.setAttributes(Collections.emptyMap());

        assertThat(user.getAttribute("nonexistent")).isEmpty();
    }

    @Test
    void shouldBuildToString() {
        String result = user.toString();

        assertThat(result).contains("uid123", "John Doe", "john@test.com");
    }

    @Test
    void shouldSetAndGetProperties() {
        assertThat(user.getId()).isEqualTo("uid123");
        assertThat(user.getDisplayName()).isEqualTo("John Doe");
        assertThat(user.getEmail()).isEqualTo("john@test.com");
    }

}
