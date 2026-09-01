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
package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.configuration.bean.CharteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests - CharteUrlResolver (résolution par domaine)")
class CharteUrlResolverTest {

    private CharteUrlResolver resolver;

    private final Map<String, String> urls = new HashMap<>();
    private final Map<String, String> serverMapping = new HashMap<>();

    @BeforeEach
    void setUp() {
        urls.put("COLL-37", "https://www.touraine-eschool.fr/files/textes/droits_usage.html");
        urls.put("LYCEE", "https://lycees.netocentre.fr/files/textes/droits_usage.html");

        serverMapping.put("www.touraine-eschool.fr", "COLL-37");
        serverMapping.put("lycees.netocentre.fr", "LYCEE");

        CharteProperties properties = new CharteProperties();
        properties.setDefaultUrl("https://lycees.netocentre.fr/files/textes/droits_usage.html");
        properties.setUrls(urls);
        properties.setServerMapping(serverMapping);

        resolver = new CharteUrlResolver(properties);
    }

    @Test
    @DisplayName("resolve : domaine mappé → URL de la clé correspondante")
    void mappedDomainResolvesCharteUrl() {
        assertThat(resolver.resolve("www.touraine-eschool.fr"))
                .isEqualTo("https://www.touraine-eschool.fr/files/textes/droits_usage.html");
        assertThat(resolver.resolve("lycees.netocentre.fr"))
                .isEqualTo("https://lycees.netocentre.fr/files/textes/droits_usage.html");
    }

    @Test
    @DisplayName("resolve : domaine non mappé → URL par défaut")
    void unknownDomainReturnsDefaultUrl() {
        assertThat(resolver.resolve("laclasse.com")).isEqualTo("https://lycees.netocentre.fr/files/textes/droits_usage.html");
    }

    @Test
    @DisplayName("resolve : clé mappée absente de urls → URL par défaut")
    void mappedKeyWithoutUrlReturnsDefaultUrl() {
        serverMapping.put("ent.colleges41.fr", "COLL-41");

        assertThat(resolver.resolve("ent.colleges41.fr")).isEqualTo("https://lycees.netocentre.fr/files/textes/droits_usage.html");
    }

    @Test
    @DisplayName("resolve : domaine null ou vide → URL par défaut")
    void blankDomainReturnsDefaultUrl() {
        assertThat(resolver.resolve(null)).isEqualTo("https://lycees.netocentre.fr/files/textes/droits_usage.html");
        assertThat(resolver.resolve("")).isEqualTo("https://lycees.netocentre.fr/files/textes/droits_usage.html");
        assertThat(resolver.resolve("   ")).isEqualTo("https://lycees.netocentre.fr/files/textes/droits_usage.html");
    }
}