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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "charte")
@Slf4j
@Data
public class CharteProperties implements InitializingBean {

    private static final String CSV_PATH = "charte/chartes.csv";

    private String defaultUrl;
    private Map<String, String> urls = new HashMap<>();

    /**
     * Correspondance hôte du site d'arrivée → clé de charte (service). Complète
     * {@link #urls} pour les hôtes qui n'y figurent pas directement (ex. hôtes de test)
     * et permet de surcharger la résolution déduite des URL.
     */
    private Map<String, String> domains = new HashMap<>();

    /**
     * Chemin externe du fichier CSV {@code dom;version} des versions de charte par service
     * (ex. {@code /etc/mce/chartes.csv}). Si absent ou introuvable, le fichier
     * {@code charte/chartes.csv} du classpath est utilisé en repli.
     */
    private String csvPath;

    /**
     * Dates de version par service (colonne {@code version}) chargées du CSV au démarrage.
     */
    private final Map<String, String> versions = new HashMap<>();

    @Override
    public void afterPropertiesSet() {
        loadVersions();
    }

    /**
     * Charge le CSV {@code dom;version} (chemin externe {@code csvPath} puis classpath) dans
     * {@link #versions}. Les lignes vides / {@code #} et une ligne d'en-tête {@code dom;version}
     * sont ignorées ; une 3e colonne éventuelle (nom/fichier) est ignorée.
     */
    public void loadVersions() {
        Resource resource = resolveCsv();
        if (resource == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            versions.clear();
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.split(";"))
                    .filter(cols -> cols.length >= 2 && !"dom".equalsIgnoreCase(cols[0].trim()))
                    .forEach(cols -> versions.put(cols[0].trim(), cols[1].trim()));
            log.info("[CHARTE] {} version(s) de charte chargée(s) depuis '{}'", versions.size(), resource.getDescription());
        } catch (IOException e) {
            log.warn("[CHARTE] Impossible de lire le CSV des versions : {}", e.getMessage());
        }
    }

    private Resource resolveCsv() {
        if (StringUtils.isNotBlank(csvPath)) {
            String normalized = (csvPath.startsWith("classpath:") || csvPath.startsWith("file:"))
                    ? csvPath : "file:" + csvPath;
            Resource external = new DefaultResourceLoader().getResource(normalized);
            if (external.exists()) {
                return external;
            }
            log.warn("[CHARTE] Chemin externe '{}' introuvable → repli sur le classpath '{}'", csvPath, CSV_PATH);
        }
        Resource classpath = new ClassPathResource(CSV_PATH);
        return classpath.exists() ? classpath : null;
    }
}