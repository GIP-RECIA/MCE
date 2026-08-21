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
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
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

    @Autowired
    private PersonneService personneService;

    public String getCharteUrl(String uid) {
        if (uid == null || uid.isBlank()) {
            return charteProperties.getDefaultUrl();
        }

        try {
            PersonneDTO personne = personneService.getUserByUid(uid);
            if (personne == null || personne.getStructureDto() == null) {
                return charteProperties.getDefaultUrl();
            }

            String source = personne.getSource();
            if (source == null) {
                return charteProperties.getDefaultUrl();
            }

            String charteUrl = charteProperties.getUrls().get(source);
            if (charteUrl != null) {
                return charteUrl;
            }

            String prefix = source.contains("-") ? source.substring(0, source.indexOf('-')) : source;
            charteUrl = charteProperties.getUrls().get(prefix);
            if (charteUrl != null) {
                return charteUrl;
            }
        } catch (Exception e) {
            log.warn("Impossible de résoudre l'URL de la charte pour l'uid [{}] : {}", uid, e.getMessage());
        }

        return charteProperties.getDefaultUrl();
    }

    public boolean isCharteRequired(String uid) {
        if (uid == null || uid.isBlank())
            return true;

        try {
            PersonneDTO personne = personneService.getUserByUid(uid);
            return personne == null || !personne.isCharteValide();
        } catch (Exception e) {
            return true;
        }
    }
}
