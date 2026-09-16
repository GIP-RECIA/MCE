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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.services.CharteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import lombok.extern.slf4j.Slf4j;

/**
 * Intercepteur global qui bloque l'accès aux API authentifiées tant que la charte
 * du domaine courant de l'utilisateur n'est pas signée.
 *
 * <p>Règle métier actuelle : {@link CharteService#isCharteRequired(String)} détermine
 * le besoin de signature à partir de la seule colonne {@code validationCharte} de
 * l'{@code APersonne}. Quand la gestion « charte par domaine » sera en place (voir le
 * TODO de {@code PersonneService.signCharte}), il suffira d'adapter cette vérification
 * pour ne contrôler que la signature du domaine courant, sans bloquer l'utilisateur
 * pour d'autres domaines dont la charte ne serait pas signée.</p>
 *
 * <p>S'exécute après {@link SoffitInterceptor} : on ne vérifie la charte que pour les
 * requêtes réellement authentifiées (présence d'un {@code sub} non nul et différent de
 * « guest »), et uniquement sur les chemin d'API authentifiés. Les flux critiques
 * (activation, récupération de mot de passe) et les endpoints de consultation/signature
 * de charte sont exclus afin d'éviter toute impasse.</p>
 */
@Slf4j
public class CharteInterceptor implements HandlerInterceptor {

    private static final String GUEST_USER = "guest";

    private final SoffitHolder soffitHolder;
    private final CharteService charteService;

    /**
     * Chemins sur lesquels la charte n'est jamais vérifiée. Ils doivent rester accessibles
     * même si la charte n'est pas signée :
     * <ul>
     *   <li>{@code /charte-status} : permet au frontend de connaître le statut (sinon impasse) ;</li>
     *   <li>{@code /charte/accept} : l'endpoint qui permet de signer la charte (sinon boucle) ;</li>
     *   <li>les flux d'activation et de récupération de mot de passe, déjà gérés par leur propre logique ;</li>
     *   <li>{@code /debug-id} : utilitaire de diagnostic.</li>
     * </ul>
     */
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/api/personne/mce/charte-status",
            "/api/personne/mce/charte/accept",
            "/api/personne/mce/activation",
            "/api/personne/mce/recover-uid",
            "/api/personne/mce/reset-password",
            "/api/personne/mce/forgot-password",
            "/api/personne/mce/verify-email",
            "/api/personne/mce/debug-id"
    );

    public CharteInterceptor(SoffitHolder soffitHolder, CharteService charteService) {
        this.soffitHolder = soffitHolder;
        this.charteService = charteService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uid = soffitHolder.getSub();

        // Pas d'utilisateur authentifié : on laisse passer (les endpoints publics ne sont pas concernés,
        // et l'authentification elle-même est gérée ailleurs).
        if (uid == null || uid.isBlank() || GUEST_USER.equals(uid)) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        String requestPath = (contextPath != null && !contextPath.isEmpty())
                ? path.substring(contextPath.length())
                : path;

        // Chemins exclus : pas de contrôle de charte.
        if (isExcluded(requestPath)) {
            log.debug("[CHARTE][INTERCEPTOR] chemin exclu, aucune vérification pour uid={} path={}", uid, requestPath);
            return true;
        }

        // Ne contrôler que les API authentifiées ; on laisse passer le reste (pages publiques, etc.).
        if (!requestPath.startsWith("/api/")) {
            return true;
        }

        log.debug("[CHARTE][INTERCEPTOR] vérification de la charte pour uid={} path={}", uid, requestPath);

        boolean charteRequise = charteService.isCharteRequired(uid);
        if (charteRequise) {
            log.warn("[CHARTE][INTERCEPTOR] Accès refusé : charte non signée pour uid={} path={}", uid, requestPath);
            // Navigation navigateur (rendu HTML) : on redirige vers la page d'activation,
            // où la charte est proposée à la signature (le compte étant déjà activé, seul
            // le bloc charte s'affiche, sans identifiant ni mot de passe).
            if (isBrowserNavigation(request)) {
                String returnTo = request.getRequestURI();
                if (request.getQueryString() != null) {
                    returnTo += "?" + request.getQueryString();
                }
                String redirectUrl = request.getContextPath() + "/activation?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
                log.info("[CHARTE][INTERCEPTOR] Redirection navigateur pour uid={} vers {}", uid, redirectUrl);
                response.sendRedirect(redirectUrl);
                return false;
            }
            // Clients API/XHR : réponse JSON (gérée côté portlet frontend qui redirige vers /activation).
            String charteUrl = charteService.getCharteUrl(uid);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"CHARTE_REQUIRED\",\"message\":\"Veuillez accepter la charte d'utilisation avant de continuer\",\"charteUrl\":\"" + charteUrl + "\"}");
            return false;
        }

        log.debug("[CHARTE][INTERCEPTOR] charte signée, accès autorisé pour uid={} path={}", uid, requestPath);
        return true;
    }

    private boolean isExcluded(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.equals(excluded) || path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Détecte une navigation navigateur « pleine page » (entête Accept contenant text/html),
     * par opposition aux appels JSON/XHR du frontend et aux clients d'API. Seules les
     * navigations navigateur doivent être redirigées vers la page d'activation (signature de charte).
     */
    private boolean isBrowserNavigation(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.toLowerCase().contains("text/html");
    }
}
