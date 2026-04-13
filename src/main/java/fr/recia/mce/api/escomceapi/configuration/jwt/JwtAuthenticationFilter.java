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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Base64;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Traite chaque requête HTTP et initialise l'authentification si un utilisateur est identifié.
     *
     * @param request requête HTTP entrante
     * @param response réponse HTTP
     * @param filterChain chaîne de filtres
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

            log.debug("Authentication réussie pour l'utilisateur : {}", username);
        } else {
            log.debug("Aucune authentification pour {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrait l'identifiant utilisateur depuis la requête.
     *
     * Priorité :
     * - header "X-User-Id"
     * - JWT Bearer (claim "sub")
     *
     * @param request requête HTTP
     * @return identifiant utilisateur ou null si absent
     */
    private String extractUsername(HttpServletRequest request) {

        // 1. Header prioritaire
        String xUserId = request.getHeader("X-User-Id");
        if (xUserId != null && !xUserId.trim().isEmpty()) {
            log.debug("Auth via X-User-Id : {}", xUserId.trim());
            return xUserId.trim();
        }

        // 2. JWT Bearer
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            try {
                String[] chunks = token.split("\\.");
                if (chunks.length < 2) {
                    log.warn("JWT mal formé");
                    return null;
                }

                String payloadJson = new String(
                        Base64.getUrlDecoder().decode(chunks[1]),
                        StandardCharsets.UTF_8
                );

                JsonNode payload = objectMapper.readTree(payloadJson);
                JsonNode subNode = payload.get("sub");

                if (subNode != null) {
                    String sub = subNode.asText();
                    log.debug("Auth via JWT (no verify) - sub = {}", sub);
                    return sub;
                }

            } catch (Exception e) {
                log.warn("JWT invalide : {}", e.getMessage());
            }
        }

        return null;
    }
}