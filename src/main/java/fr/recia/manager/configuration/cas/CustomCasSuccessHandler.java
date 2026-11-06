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

package fr.recia.manager.configuration.cas;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Classe custom qui permet d'enregistrer un mapping ST -> ID de session lors d'une authentification CAS réussie
 */
@Slf4j
@Component
public class CustomCasSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Autowired
    private CustomSessionMappingStorage redisService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("Authentification terminé avec succès utilisateur authentifié : {}", authentication.getName());

        // Authentification et session
        log.debug("Utilisateur authentifié : {}", authentication.getName());
        String credentials = (String) authentication.getCredentials();
        log.debug("Credentials (Session Ticket) : {}", credentials);
        String sessionId = request.getSession(false).getId();
        log.debug("Session ID : {}", sessionId);

        if (credentials == null) {
            throw new RuntimeException("Ticket perdu pour la session: " + sessionId);
        }

        log.debug("Création du mappage entre le ticket [{}] et l'ID de session [{}] dans le cache Redis", credentials, sessionId);
        redisService.setSessionTicketSessionIdPair(credentials, sessionId);

        super.onAuthenticationSuccess(request, response, authentication);
    }



}
