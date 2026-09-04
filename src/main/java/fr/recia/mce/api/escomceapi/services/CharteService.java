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
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CharteService {

    @Autowired
    private CharteProperties charteProperties;

    @Autowired
    private APersonneRepository aPersonneRepository;

    /**
     * Résout l'URL de la charte propre au domaine (source) de l'utilisateur, lue directement en base.
     * Retourne l'URL par défaut si la source est absente ou inconnue.
     */
    public String getCharteUrl(String uid) {
        log.info("[CHARTE][URL] getCharteUrl(uid={}) — defaultUrl={}", uid, charteProperties.getDefaultUrl());
        if (uid == null || uid.isBlank()) {
            log.warn("[CHARTE][URL] uid null ou vide → defaultUrl");
            return charteProperties.getDefaultUrl();
        }

        try {
            APersonne person = aPersonneRepository.findByUid(uid);
            if (person == null) {
                log.warn("[CHARTE][URL] personne introuvable pour uid={} → defaultUrl", uid);
                return charteProperties.getDefaultUrl();
            }

            String source = person.getSource();
            log.info("[CHARTE][URL] uid={} → source='{}' (urls dispo={})", uid, source, charteProperties.getUrls().keySet());
            if (source == null || source.isBlank()) {
                log.warn("[CHARTE][URL] source absente/vide pour uid={} → defaultUrl", uid);
                return charteProperties.getDefaultUrl();
            }

            String charteUrl = charteProperties.getUrls().get(source);
            if (charteUrl != null) {
                log.info("[CHARTE][URL] uid={} → source exacte '{}' → {}", uid, source, charteUrl);
                return charteUrl;
            }

            String prefix = source.contains("-") ? source.substring(0, source.indexOf('-')) : source;
            log.info("[CHARTE][URL] uid={} → pas de match exact pour '{}', essai du préfixe '{}'", uid, source, prefix);
            charteUrl = charteProperties.getUrls().get(prefix);
            if (charteUrl != null) {
                log.info("[CHARTE][URL] uid={} → préfixe '{}' → {}", uid, prefix, charteUrl);
                return charteUrl;
            }
            log.warn("[CHARTE][URL] uid={} → aucun match pour source='{}' ni préfixe='{}' → defaultUrl", uid, source, prefix);
        } catch (Exception e) {
            log.warn("Impossible de résoudre l'URL de la charte pour l'uid [{}] : {}", uid, e.getMessage());
        }

        return charteProperties.getDefaultUrl();
    }

    /**
     * La charte est requise tant qu'aucune date de signature n'est enregistrée en base
     * (colonne {@code validationCharte} de {@code aPersonne}).
     */
    public boolean isCharteRequired(String uid) {
        log.info("[CHARTE][REQUIRED] isCharteRequired(uid={})", uid);
        if (uid == null || uid.isBlank()) {
            log.warn("[CHARTE][REQUIRED] uid null ou vide → charte requise");
            return true;
        }

        try {
            APersonne person = aPersonneRepository.findByUid(uid);
            if (person == null) {
                log.warn("[CHARTE][REQUIRED] personne introuvable pour uid={} → charte requise", uid);
                return true;
            }
            log.info("[CHARTE][REQUIRED] uid={} → validationCharte={} → charte requise ? {}",
                    uid, person.getValidationCharte(), person.getValidationCharte() == null);
            return person.getValidationCharte() == null;
        } catch (Exception e) {
            log.warn("Impossible de vérifier l'état de la charte pour l'uid [{}] : {}", uid, e.getMessage());
            return true;
        }
    }
}
