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

import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Binding du application.yml réel vers les beans de configuration")
class ApplicationYmlBindingTest {

    @Test
    @DisplayName("server.forward-headers-strategy=native : IP client derrière proxy (rate limiter par IP réel)")
    void forwardHeadersStrategyIsNative() throws IOException {
        Object value = loadRealYamlSources().get(0).getProperty("server.forward-headers-strategy");
        assertThat(value)
                .as("sans 'native', getRemoteAddr() renvoie l'IP du reverse proxy pour TOUS les utilisateurs "
                        + "→ le rate limiter bloque collectivement tout le monde")
                .isEqualTo("native");
    }

    private List<PropertySource<?>> loadRealYamlSources() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).as("application.yml doit exister sur le classpath").isNotEmpty();
        return sources;
    }

    private Binder loadRealYaml() throws IOException {
        return new Binder(ConfigurationPropertySources.from(loadRealYamlSources()));
    }

    @Test
    @DisplayName("app.security.* : rate-limit, reset-policy et password-policy se bindent depuis le YAML")
    void securitySectionBindsFromYaml() throws IOException {
        SecurityProperties security = loadRealYaml()
                .bind("app.security", Bindable.of(SecurityProperties.class)).get();

        assertThat(security.getRateLimit().getPermitsPerSecond()).isEqualTo(10.0);
        assertThat(security.getRateLimit().getMaxEntries()).isEqualTo(10_000);
        assertThat(security.getResetPolicy().getMaxAttempts()).isEqualTo(5);
        assertThat(security.getResetPolicy().getResendCooldownMs()).isEqualTo(60_000L);
        assertThat(security.getPasswordPolicy().getMinLength()).isEqualTo(12);
        assertThat(security.getPasswordPolicy().getMinTypes()).isEqualTo(3);
    }

    @Test
    @DisplayName("mail.verification : code-length=6 et expiry-hours=1 (différent du défaut Java 24)")
    void mailVerificationBindsFromYaml() throws IOException {
        MailProperties mail = loadRealYaml().bind("mail", Bindable.of(MailProperties.class)).get();

        assertThat(mail.getVerification().getCodeLength()).isEqualTo(6);
        assertThat(mail.getVerification().getExpiryHours())
                .as("le YAML doit surcharger la valeur par défaut Java (24) — sinon la clé a été cassée")
                .isEqualTo(1);
        assertThat(mail.getVerification().getFrontendUrl()).isNotBlank();
        assertThat(mail.getFromEmail()).isNotBlank();
        assertThat(mail.getAcMailPattern()).isNotBlank();
    }

    @Test
    @DisplayName("mail.templates : les 2 gabarits contiennent les variables {{code}} et {{expiryHours}}")
    void mailTemplatesBindWithPlaceholders() throws IOException {
        MailProperties mail = loadRealYaml().bind("mail", Bindable.of(MailProperties.class)).get();

        for (MailProperties.EmailTemplates.Template template : List.of(mail.getTemplates().getVerification(), mail.getTemplates().getReset())) {
            assertThat(template.getSubject()).isNotBlank();
            assertThat(template.getBody()).contains("{{code}}").contains("{{expiryHours}}");
        }
    }

    static Stream<Arguments> profils() {
        return Stream.of(
                Arguments.of("application-dev.yml", 10.0, 1_000, 10, 10_000L),
                Arguments.of("application-test.yml", 1.0, 5_000, 10, 30_000L),
                Arguments.of("application-prod.yml", 0.167, 10_000, 5, 60_000L));
    }

    @ParameterizedTest
    @MethodSource("profils")
    @DisplayName("Overlays de profil : les surcharges app.security se bindent correctement")
    void profileSecurityOverridesBind(String fichier, double permitsPerSecond, int maxEntries,
            int maxAttempts, long resendCooldownMs) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(fichier, new ClassPathResource(fichier));
        assertThat(sources).as(fichier + " doit exister sur le classpath").isNotEmpty();

        SecurityProperties security = new Binder(ConfigurationPropertySources.from(sources))
                .bind("app.security", Bindable.of(SecurityProperties.class)).get();

        assertThat(security.getRateLimit().getPermitsPerSecond()).isEqualTo(permitsPerSecond);
        assertThat(security.getRateLimit().getMaxEntries()).isEqualTo(maxEntries);
        assertThat(security.getResetPolicy().getMaxAttempts()).isEqualTo(maxAttempts);
        assertThat(security.getResetPolicy().getResendCooldownMs()).isEqualTo(resendCooldownMs);
    }
}
