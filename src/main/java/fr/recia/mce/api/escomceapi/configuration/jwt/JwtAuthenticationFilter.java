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

package fr.recia.mce.api.escomceapi.configuration.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Filtre d’authentification JWT personnalisé.
 *
 * Ce filtre intercepte chaque requête HTTP afin d’identifier l’utilisateur
 * et de l’enregistrer dans le SecurityContext Spring.
 *
 * Deux modes d’authentification sont supportés :
 *
 * 1. Header X-User-Id (priorité haute)
 *    - Utilisé pour les environnements de test ou internes
 *
 * 2. Authorization: Bearer JWT
 *    - Extraction du claim "sub" depuis un token JWT
 *    - Le token est uniquement décodé (Base64), sans vérification de signature
 *
 * Attention :
 * Ce mécanisme ne vérifie pas la signature du JWT et ne doit pas être utilisé
 * en production. Il est destiné à un usage de développement ou environnement contrôlé.
 *
 * En production, il est recommandé d’utiliser Spring Security OAuth2 Resource Server.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    /**
     * Génère la clé de signature HMAC à partir de la configuration.
     *
     * @return clé de signature ou null si la configuration est invalide
     */
    private SecretKey getSigningKey() {
        String secret = jwtSecret != null ? jwtSecret.trim() : "";
        if (secret.length() < 32) {
            log.error("JWT secret trop court ou absent");
            return null;
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Intercepte la requête HTTP et initialise l’authentification utilisateur
     * dans le contexte Spring Security si un utilisateur est identifié.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String username = extractUsername(request);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            User userDetails = new User(
                    username,
                    "",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.info("Authentication réussie pour l'utilisateur : {}", username);
        } else {
            log.debug("Aucune authentification trouvée pour {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrait l'identité utilisateur depuis la requête HTTP.
     *
     * Priorité des sources :
     * 1. Header X-User-Id
     * 2. JWT Bearer (claim "sub")
     *
     * Le JWT est décodé uniquement sans validation de signature.
     *
     * @param request requête HTTP entrante
     * @return identifiant utilisateur ou null si non authentifié
     */
    private String extractUsername(HttpServletRequest request) {

        String xUserId = request.getHeader("X-User-Id");
        if (xUserId != null && !xUserId.trim().isEmpty()) {
            log.info("Auth via X-User-Id : {}", xUserId.trim());
            return xUserId.trim();
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            try {
                String[] chunks = token.split("\\.");
                if (chunks.length < 2) {
                    log.warn("JWT mal formé");
                    return null;
                }

                String payload = new String(
                        java.util.Base64.getUrlDecoder().decode(chunks[1]),
                        java.nio.charset.StandardCharsets.UTF_8
                );

                String sub = payload.split("\"sub\":\"")[1].split("\"")[0];

                log.info("Auth via JWT (no verify) - sub = {}", sub);
                return sub;

            } catch (Exception e) {
                log.warn("JWT invalide : {}", e.getMessage());
            }
        }

        return null;
    }
}