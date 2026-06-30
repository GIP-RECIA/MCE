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

class ExternalStructureTest {

    private ExternalStructure struct;

    @BeforeEach
    void setUp() {
        struct = new ExternalStructure();
        struct.setId("struct1");
        struct.setName("Lycee Test");
        struct.setDisplayName("Lycee Test - 0450001A");
        struct.setUai("0450001A");
        struct.setType("LYCEE");
        struct.setDomaines(new String[]{"ac-test.fr", "lycees.test.fr"});
    }

    @Test
    void shouldGetAttributeByExactCase() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("supannCodeEntite", Arrays.asList("0450001A"));
        struct.setAttributes(attrs);

        assertThat(struct.getAttribute("supannCodeEntite")).containsExactly("0450001A");
    }

    @Test
    void shouldGetAttributeByLowerCaseFallback() {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("supanncodeentite", Arrays.asList("0450001A"));
        struct.setAttributes(attrs);

        assertThat(struct.getAttribute("supannCodeEntite")).containsExactly("0450001A");
    }

    @Test
    void shouldReturnEmptyListWhenAttributeNotFound() {
        struct.setAttributes(Collections.emptyMap());

        assertThat(struct.getAttribute("nonexistent")).isEmpty();
    }

    @Test
    void shouldBuildToString() {
        String result = struct.toString();

        assertThat(result).contains("struct1", "Lycee Test", "0450001A", "LYCEE");
    }

    @Test
    void shouldSetAndGetProperties() {
        assertThat(struct.getId()).isEqualTo("struct1");
        assertThat(struct.getName()).isEqualTo("Lycee Test");
        assertThat(struct.getDisplayName()).isEqualTo("Lycee Test - 0450001A");
        assertThat(struct.getUai()).isEqualTo("0450001A");
        assertThat(struct.getType()).isEqualTo("LYCEE");
        assertThat(struct.getDomaines()).containsExactly("ac-test.fr", "lycees.test.fr");
    }

}
