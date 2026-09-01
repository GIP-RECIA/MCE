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
package fr.recia.mce.api.escomceapi.configuration;

import fr.recia.mce.api.escomceapi.configuration.bean.CharteProperties;
import fr.recia.mce.api.escomceapi.services.CharteUrlResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Charte : résolution de l'URL pour tous les hosts du server-mapping réel")
class CharteUrlResolverAllHostsTest {

    private CharteProperties loadRealCharteProperties() throws IOException {
        List<org.springframework.core.env.PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).as("application.yml doit exister sur le classpath").isNotEmpty();

        CharteProperties charte = new Binder(ConfigurationPropertySources.from(sources))
                .bind("charte", Bindable.of(CharteProperties.class)).get();
        assertThat(charte.getDefaultUrl()).isNotBlank();
        assertThat(charte.getUrls()).isNotEmpty();
        assertThat(charte.getServerMapping()).isNotEmpty();
        return charte;
    }

    @Test
    @DisplayName("Chaque host de server-mapping résout l'URL exacte de sa clé (pas de repli par défaut)")
    void everyMappedHostResolvesExpectedUrl() throws IOException {
        CharteProperties charte = loadRealCharteProperties();
        CharteUrlResolver resolver = new CharteUrlResolver(charte);

        assertThat(charte.getServerMapping()).as("server-mapping ne doit pas être vide").isNotEmpty();

        for (Map.Entry<String, String> entry : charte.getServerMapping().entrySet()) {
            String host = entry.getKey();
            String key = entry.getValue();

            assertThat(charte.getUrls())
                    .as("la clé '%s' pointée par le host '%s' doit exister dans charte.urls", key, host)
                    .containsKey(key);

            String resolved = resolver.resolve(host);
            assertThat(resolved)
                    .as("le host '%s' (%s) doit résoudre l'URL exacte de sa clé", host, key)
                    .isEqualTo(charte.getUrls().get(key))
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("Un host inconnu retombe proprement sur l'URL par défaut")
    void unknownHostFallsBackToDefault() throws IOException {
        CharteProperties charte = loadRealCharteProperties();
        CharteUrlResolver resolver = new CharteUrlResolver(charte);

        assertThat(resolver.resolve("inconnu.example.fr")).isEqualTo(charte.getDefaultUrl());
    }

    @Test
    @DisplayName("Host null, vide ou blanc → URL par défaut")
    void blankHostsFallBackToDefault() throws IOException {
        CharteProperties charte = loadRealCharteProperties();
        CharteUrlResolver resolver = new CharteUrlResolver(charte);

        assertThat(resolver.resolve(null)).isEqualTo(charte.getDefaultUrl());
        assertThat(resolver.resolve("")).isEqualTo(charte.getDefaultUrl());
        assertThat(resolver.resolve("   ")).isEqualTo(charte.getDefaultUrl());
    }
}