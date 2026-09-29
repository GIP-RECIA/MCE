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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests - CharteProperties")
class ChartePropertiesTest {

    @Test
    @DisplayName("csv-path absent → versions depuis le CSV du classpath")
    void fallsBackToClasspath() {
        CharteProperties props = new CharteProperties();

        props.loadVersions();

        assertThat(props.getVersions())
                .containsEntry("www.touraine-eschool.fr", "2024-01-02")
                .containsEntry("cfa.netocentre.fr", "2024-01-02")
                .containsEntry("ent.recia.fr", "2024-01-02")
                .containsEntry("default", "2024-01-02")
                .doesNotContainKey("COLL-37");
    }

    @Test
    @DisplayName("csv-path externe → versions depuis le fichier système")
    void loadsFromExternalPath(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("chartes.csv"), List.of(
                "# commentaire ignoré",
                "dom;version",
                "www.touraine-eschool.fr;2025-05-05",
                "",
                "cfa.netocentre.fr;2023-11-30;une-3e-colonne-ignorée"
        ));
        CharteProperties props = new CharteProperties();
        props.setCsvPath(tempDir.resolve("chartes.csv").toString());

        props.loadVersions();

        assertThat(props.getVersions())
                .containsEntry("www.touraine-eschool.fr", "2025-05-05")
                .containsEntry("cfa.netocentre.fr", "2023-11-30");
    }

    @Test
    @DisplayName("csv-path introuvable → repli sur le CSV du classpath")
    void missingExternalPathFallsBackToClasspath() {
        CharteProperties props = new CharteProperties();
        props.setCsvPath("/tmp/inexistant-chartes.csv");

        props.loadVersions();

        assertThat(props.getVersions()).containsEntry("www.touraine-eschool.fr", "2024-01-02");
    }

    @Test
    @DisplayName("domaine en double → la version la plus récente déjà en vigueur, pas celle du futur")
    void duplicatedDomainKeepsCurrentVersionOverFutureOne(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("chartes.csv"), List.of(
                "dom;version",
                "www.touraine-eschool.fr;" + LocalDate.now().minusMonths(2),
                "www.touraine-eschool.fr;" + LocalDate.now().plusMonths(2),
                "www.touraine-eschool.fr;" + LocalDate.now().minusDays(1)
        ));
        CharteProperties props = new CharteProperties();
        props.setCsvPath(tempDir.resolve("chartes.csv").toString());

        props.loadVersions();

        assertThat(props.getVersions())
                .containsEntry("www.touraine-eschool.fr", LocalDate.now().minusDays(1).toString())
                .hasSize(1);
    }

    @Test
    @DisplayName("domaine en double → plusieurs versions en vigueur : la plus récente gagne")
    void duplicatedDomainKeepsMostRecentPastVersion(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("chartes.csv"), List.of(
                "dom;version",
                "cfa.netocentre.fr;2024-01-02",
                "cfa.netocentre.fr;2025-03-15",
                "cfa.netocentre.fr;2099-01-01"
        ));
        CharteProperties props = new CharteProperties();
        props.setCsvPath(tempDir.resolve("chartes.csv").toString());

        props.loadVersions();

        assertThat(props.getVersions()).containsEntry("cfa.netocentre.fr", "2025-03-15");
    }

    @Test
    @DisplayName("domaine en double → toutes les dates futures : la plus proche est retenue")
    void duplicatedDomainKeepsNearestFutureWhenNoneIsEffective(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("chartes.csv"), List.of(
                "dom;version",
                "ent.recia.fr;2099-12-31",
                "ent.recia.fr;2098-01-01"
        ));
        CharteProperties props = new CharteProperties();
        props.setCsvPath(tempDir.resolve("chartes.csv").toString());

        props.loadVersions();

        assertThat(props.getVersions()).containsEntry("ent.recia.fr", "2098-01-01");
    }

    @Test
    @DisplayName("date illisible → la ligne valide du même domaine reste prioritaire")
    void unparsableDateDoesNotOverrideValidOne(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("chartes.csv"), List.of(
                "dom;version",
                "lycees.netocentre.fr;pas-une-date",
                "lycees.netocentre.fr;2025-01-05"
        ));
        CharteProperties props = new CharteProperties();
        props.setCsvPath(tempDir.resolve("chartes.csv").toString());

        props.loadVersions();

        assertThat(props.getVersions()).containsEntry("lycees.netocentre.fr", "2025-01-05");
    }
}
