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
package fr.recia.mce.api.escomceapi.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import fr.recia.mce.api.escomceapi.services.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final MCEProperties mceProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitInterceptor(MCEProperties mceProperties) {
        this.mceProperties = mceProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String ip = request.getRemoteAddr();
        var policy = mceProperties.getSecurity().getRateLimit();

        RateLimiter limiter = limiters.compute(ip, (key, existing) -> {
            if (existing == null && limiters.size() < policy.getMaxEntries()) {
                return RateLimiter.create(policy.getPermitsPerSecond());
            }
            return existing;
        });

        if (limiter != null && !limiter.tryAcquire()) {
            log.warn("[RATE_LIMIT] Requête bloquée pour IP={}, URI={}", ip, request.getRequestURI());
            writeJsonResponse(response, HttpStatus.TOO_MANY_REQUESTS,
                    new ErrorResponse("RATE_LIMIT_EXCEEDED",
                            "Trop de requêtes. Veuillez patienter quelques instants avant de réessayer."));
            return false;
        }
        return true;
    }

    private void writeJsonResponse(HttpServletResponse response, HttpStatus status, ErrorResponse body) {
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.error("Erreur lors de l'écriture de la réponse rate-limit : {}", e.getMessage());
        }
    }
}
