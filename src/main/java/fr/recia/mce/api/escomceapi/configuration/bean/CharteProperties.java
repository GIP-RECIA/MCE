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
import java.time.LocalDate;
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
     * Chemin externe du fichier CSV {@code dom;version} des versions de charte par domaine
     * (ex. {@code /etc/mce/chartes.csv}). Si absent ou introuvable, le fichier
     * {@code charte/chartes.csv} du classpath est utilisé en repli.
     */
    private String csvPath;

    /**
     * Dates de version par domaine (colonne {@code version}) chargées du CSV au démarrage, la
     * colonne {@code dom} portant le début de l'URL du site (ex. {@code www.touraine-eschool.fr}),
     * ou {@code default} pour les domaines non référencés.
     */
    private final Map<String, String> versions = new HashMap<>();

    @Override
    public void afterPropertiesSet() {
        loadVersions();
    }

    /**
     * Charge le CSV {@code dom;version} (domaine, chemin externe {@code csvPath} puis classpath) dans
     * {@link #versions}. Les lignes vides / {@code #} et une ligne d'en-tête {@code dom;version}
     * sont ignorées ; une 3e colonne éventuelle (nom/fichier) est ignorée.
     *
     * <p>Un même {@code dom} peut apparaître sur plusieurs lignes (la charte du service évolue) :
     * seule la ligne <strong>d'actualité</strong> est retenue — voir
     * {@link #putVersion(Map, String, String, LocalDate)}. Une ligne datée du futur n'écrase
     * donc jamais la version actuellement en vigueur.</p>
     */
    public void loadVersions() {
        Resource resource = resolveCsv();
        if (resource == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            versions.clear();
            LocalDate today = LocalDate.now();
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.split(";"))
                    .filter(cols -> cols.length >= 2 && !"dom".equalsIgnoreCase(cols[0].trim()))
                    .forEach(cols -> putVersion(versions, cols[0].trim(), cols[1].trim(), today));
            log.info("[CHARTE] {} version(s) de charte chargée(s) depuis '{}'", versions.size(), resource.getDescription());
        } catch (IOException e) {
            log.warn("[CHARTE] Impossible de lire le CSV des versions : {}", e.getMessage());
        }
    }

    /**
     * Retient, pour un même {@code dom}, la ligne d'actualité et ignore les autres :
     * <ul>
     *   <li>date ≤ {@code today} : la plus récente l'emporte (dernière version en vigueur) ;</li>
     *   <li>toutes les dates sont futures : la plus proche l'emporte (aucune version en vigueur) ;</li>
     *   <li>date illisible : elle n'écrase jamais une date valide et cède la place à la
     *       première date valide trouvée (à défaut, elle reste et
     *       {@link fr.recia.mce.api.escomceapi.services.CharteService#getCharteVersionDate(String)}
     *       la remplace par la date par défaut).</li>
     * </ul>
     */
    private static void putVersion(Map<String, String> target, String dom, String version, LocalDate today) {
        String current = target.get(dom);
        if (current == null) {
            target.put(dom, version);
            return;
        }

        LocalDate candidateDate = parseVersion(version);
        LocalDate currentDate = parseVersion(current);

        if (candidateDate == null) {
            log.warn("[CHARTE][VERSION] date '{}' illisible pour le domaine '{}' → ligne ignorée (version courante {})", version, dom, current);
            return;
        }
        if (currentDate == null) {
            target.put(dom, version);
            return;
        }

        boolean candidateIsCurrent = !candidateDate.isAfter(today);
        boolean currentIsCurrent = !currentDate.isAfter(today);
        if (candidateIsCurrent != currentIsCurrent) {
            if (candidateIsCurrent) {
                target.put(dom, version);
            }
            return;
        }

        boolean keepCandidate = candidateIsCurrent ? candidateDate.isAfter(currentDate) : candidateDate.isBefore(currentDate);
        if (keepCandidate) {
            target.put(dom, version);
        } else {
            log.debug("[CHARTE][VERSION] domaine '{}' : ligne '{}' ignorée, la version '{}' est d'actualité", dom, version, current);
        }
    }

    private static LocalDate parseVersion(String version) {
        if (StringUtils.isBlank(version)) {
            return null;
        }
        try {
            return LocalDate.parse(version.trim());
        } catch (Exception e) {
            return null;
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