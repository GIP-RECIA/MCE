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
package fr.recia.mce.api.escomceapi.services.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PasswordAuditLogger {

    private static final Logger auditLog =
            LoggerFactory.getLogger("AUDIT_PASSWORD");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Log un changement de mot de passe réussi
     *
     * @param uid      UID de l'utilisateur
     * @param algo     Algorithme utilisé (SSHA, ARGON2)
     * @param ip       IP de l'utilisateur
     * @param ldapHash Hash du nouveau mot de passe
     */
    public void logSuccess(String uid, String name, String algo, String ip, String ldapHash) {
        auditLog.info(
                "action=CHANGE_PASSWORD | status=SUCCESS | uid={} | name={} | algo={} | ip={} | date={} | hashStart={}",
                uid,
                name,
                algo,
                resolveIp(ip),
                LocalDateTime.now().format(FORMATTER),
                extractHashStart(ldapHash)
        );
    }

    /**
     * Log une tentative de changement de mot de passe échouée
     *
     * @param uid    UID de l'utilisateur
     * @param reason Raison de l'échec
     * @param ip     IP de l'utilisateur
     */
    public void logFailure(String uid, String name, String reason, String ip) {
        auditLog.info(
                "action=CHANGE_PASSWORD | status=FAILURE | uid={} | name={} | reason={} | ip={} | date={}",
                uid,
                name,
                reason,
                resolveIp(ip),
                LocalDateTime.now().format(FORMATTER)
        );
    }

    /**
     * Extrait les 8 premiers caractères du hash après le préfixe {SSHA} ou {ARGON2}
     */
    private String extractHashStart(String ldapHash) {
        if (ldapHash == null || ldapHash.isBlank()) return "***";

        String withoutPrefix = ldapHash.replaceAll("^\\{[^}]+\\}", "");

        if (withoutPrefix.length() < 8) return "***";

        return withoutPrefix.substring(0, 8) + "***";
    }

    /**
     * Retourne l'IP ou "unknown" si null
     */
    private String resolveIp(String ip) {
        return (ip != null && !ip.isBlank()) ? ip : "unknown";
    }
}