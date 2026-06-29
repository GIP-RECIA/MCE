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
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SoffitInterceptor implements HandlerInterceptor {


    private final SoffitHolder soffitHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SoffitInterceptor(SoffitHolder soffitHolder) {
        this.soffitHolder = soffitHolder;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String authHeader = request.getHeader("Authorization");

        log.debug("Authorization header received: {}", authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("No valid Bearer token found for path: {}", request.getRequestURI());
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
            log.debug("JWT Payload: {}", payload);

            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            String sub = (String) claims.get("sub");
            Long exp = claims.get("exp") != null ? Long.valueOf(claims.get("exp").toString()) : null;

            if (sub == null || sub.isBlank()) {
                log.warn("No 'sub' claim found in token");
                soffitHolder.setSub(null);
            } else {
                soffitHolder.setSub(sub);
                log.debug("User authenticated via Soffit - sub: {}", sub);
            }

            // Vérification expiration (optionnelle, le filtre le fait déjà)
            if (exp != null && exp < Instant.now().getEpochSecond()) {
                log.warn("Token has expired");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to parse Soffit JWT", e);
        }

        return true;
    }

}