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
package fr.recia.mce.api.escomceapi.interceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.recia.mce.api.escomceapi.interceptor.bean.SoffitHolder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SoffitInterceptor implements HandlerInterceptor {

    private final SoffitHolder soffitHolder;

    public SoffitInterceptor(SoffitHolder soffitHolder) {
        this.soffitHolder = soffitHolder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (path.startsWith("/api")) {
            List<String> excludedPaths = List.of("^/api/config$", "^/api/file/\\d+/resource/.+$");
            if (excludedPaths.stream().anyMatch(path::matches)) {
                log.debug("Path {} start with /api but is excluded form SoffitInterceptor", path);

                return true;
            }
        } else
            return true;

        String token = request.getHeader("Authorization");
        if (token == null) {
            log.debug("No Authorization header found");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(token.replace("Bearer ", "").split("\\.")[1]));

        Map<String, String> soffit;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            soffit = objectMapper.readValue(payload, new TypeReference<>() {
            });
            log.debug("Soffit : {}", soffit);
            if (Long.parseLong(soffit.get("exp")) < Instant.now().getEpochSecond()) {
                log.debug("Token has expired");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }
            soffitHolder.setSub(soffit.get("sub"));
        } catch (IOException ignored) {
            log.error("Unable to read soffit");
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return false;
        }
        return true;
    }
}
