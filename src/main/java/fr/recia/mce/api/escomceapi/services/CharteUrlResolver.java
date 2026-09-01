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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CharteUrlResolver {

    private final CharteProperties charteProperties;

    public CharteUrlResolver(CharteProperties charteProperties) {
        this.charteProperties = charteProperties;
    }

    public String resolve(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            log.warn("[CHARTE_URL] Host absent ou vide → URL par défaut : {}", charteProperties.getDefaultUrl());
            return charteProperties.getDefaultUrl();
        }

        String host = serverName.trim();
        String key = charteProperties.getServerMapping().get(host);
        if (key != null) {
            String url = charteProperties.getUrls().get(key);
            if (url != null) {
                log.info("[CHARTE_URL] Host '{}' → clé '{}' → URL : {}", host, key, url);
                return url;
            }
            log.warn("[CHARTE_URL] Host '{}' mappé vers la clé '{}' mais aucune URL déclarée pour cette clé → URL par défaut : {}",
                    host, key, charteProperties.getDefaultUrl());
        } else {
            log.info("[CHARTE_URL] Host '{}' non mappé dans charte.server-mapping → URL par défaut : {}", host,
                    charteProperties.getDefaultUrl());
        }

        return charteProperties.getDefaultUrl();
    }
}
