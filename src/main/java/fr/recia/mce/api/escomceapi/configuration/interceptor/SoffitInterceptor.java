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
package fr.recia.mce.api.escomceapi.configuration.interceptor;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SoffitInterceptor implements HandlerInterceptor {

    private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";
    private static final String GUEST_USER_PREFIX = "guest";

    private final SoffitHolder soffitHolder;
    private final boolean requireAuthenticatedPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SoffitInterceptor(SoffitHolder soffitHolder, boolean requireAuthenticatedPrincipal) {
        this.soffitHolder = soffitHolder;
        this.requireAuthenticatedPrincipal = requireAuthenticatedPrincipal;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        log.debug("En-tête Authorization reçu : présent={}", request.getHeader("Authorization") != null);

        // 1) Source de confiance : le principal posé par le filtre Spring Security
        //    (SoffitApiPreAuthenticatedProcessingFilter), dont la signature HMAC a
        //    été vérifiée avec app.soffit.jwt-signature-key.
        if (resolveAuthenticatedPrincipal()) {
            return true;
        }

        // 2) Mode strict (app.soffit.require-authenticated-principal=true) : sans
        //    principal vérifié, aucun jeton n'est accepté. sub reste nul (ou "guest"),
        //    les contrôleurs / factories refusent la requête (401/403).
        if (requireAuthenticatedPrincipal) {
            log.warn("Mode strict : aucun principal authentifié (signature HMAC) pour le chemin : {}", request.getRequestURI());
            return true;
        }

        // 3) Fallback hérité : décodage tolérant du payload JWT (sans vérif. signature),
        //    conservé uniquement pour les déploiements dont le reverse proxy injecte un
        //    JWT non signé avec la clé configurée. À retirer une fois le proxy aligné sur
        //    la clé et le flag passé à true.
        return resolveFromBearerToken(request, response);
    }

    private boolean resolveAuthenticatedPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String) {
            String sub = (String) auth.getPrincipal();
            if (!sub.isBlank() && !ANONYMOUS_PRINCIPAL.equals(sub) && !sub.startsWith(GUEST_USER_PREFIX)) {
                soffitHolder.setSub(sub);
                log.debug("Utilisateur authentifié via SecurityContext (HMAC vérifiée) - sub : {}", sub);
                return true;
            }
        }
        return false;
    }

    private boolean resolveFromBearerToken(HttpServletRequest request, HttpServletResponse response) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Aucun jeton Bearer valide trouvé pour le chemin : {}", request.getRequestURI());
            return true;
        }

        String jwt = authHeader.replace("Bearer ", "").trim();

        try {
            // Décodage du payload (partie 2 du JWT)
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("JWT mal formé");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            log.debug("Payload JWT décodé ({} caractères)", payload.length());

            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            String sub = (String) claims.get("sub");
            Long exp = claims.get("exp") != null ? Long.valueOf(claims.get("exp").toString()) : null;

            if (sub == null || sub.isBlank()) {
                log.warn("Aucune revendication 'sub' trouvée dans le jeton pour le chemin : {}", request.getRequestURI());
                soffitHolder.setSub(null);
            } else {
                soffitHolder.setSub(sub);
                log.debug("Utilisateur authentifié via Soffit - sub : {}", sub);
            }

            // Vérification expiration (optionnelle, le filtre le fait déjà)
            if (exp != null && exp < Instant.now().getEpochSecond()) {
                log.warn("Jeton expiré pour le chemin : {}", request.getRequestURI());
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }

        } catch (Exception e) {
            log.error("Échec du décodage du jeton Soffit pour le chemin : {} - Détail : {}", request.getRequestURI(), e.getMessage());
        }

        return true;
    }

}