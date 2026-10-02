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

import fr.recia.mce.api.escomceapi.configuration.bean.CasProperties;
import fr.recia.mce.api.escomceapi.configuration.cas.CustomCas30ServiceTicketValidator;
import fr.recia.mce.api.escomceapi.configuration.cas.CustomCasAuthenticationEntryPoint;
import fr.recia.mce.api.escomceapi.configuration.cas.CustomCasSuccessHandler;
import fr.recia.mce.api.escomceapi.configuration.cas.CustomSessionMappingStorage;
import fr.recia.mce.api.escomceapi.security.AppUser;
import org.jasig.cas.client.session.SingleSignOutFilter;
import org.jasig.cas.client.validation.Assertion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAssertionAuthenticationToken;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final MCEProperties mceProperties;
    private final CasProperties casProperties;
    private final CustomSessionMappingStorage customSessionMappingStorage;
    private final CustomCasSuccessHandler customCasSuccessHandler;

    public SecurityConfiguration(MCEProperties mceProperties, CasProperties casProperties,
                                 CustomSessionMappingStorage customSessionMappingStorage, CustomCasSuccessHandler customCasSuccessHandler) {
        this.mceProperties = mceProperties;
        this.casProperties = casProperties;
        this.customSessionMappingStorage = customSessionMappingStorage;
        this.customCasSuccessHandler = customCasSuccessHandler;
    }

    // Swagger ne doit être accessible qu'hors production (documentation complète des
    // endpoints, DTOs et schémas). En prod, l'UI et /v3/api-docs restent interdits
    // (tombent sur denyAll()).
    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    /**
     * Propriétés du service CAS, avec notamment serviceID associé côté CAS
     */
    @Bean
    public ServiceProperties serviceProperties() {
        ServiceProperties serviceProperties = new ServiceProperties();
        serviceProperties.setService(casProperties.getCasServiceId());
        serviceProperties.setSendRenew(false);
        return serviceProperties;
    }

    /**
     * Redirection vers le CAS sur une route protégée
     */
    @Bean
    public CasAuthenticationEntryPoint casAuthenticationEntryPoint(ServiceProperties serviceProperties) {
        CasAuthenticationEntryPoint entryPoint = new CustomCasAuthenticationEntryPoint();
        entryPoint.setLoginUrl(casProperties.getCasServerUrl() + "/login");
        entryPoint.setServiceProperties(serviceProperties);
        return entryPoint;
    }

    /**
     * Filtre qui intercepte la requête vers le service depuis le CAS (en passant par le navigateur) avec le ticket dans l'URL
     */
    @Bean
    public CasAuthenticationFilter casAuthenticationFilter(AuthenticationManager authenticationManager) {
        CasAuthenticationFilter filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager);
        filter.setFilterProcessesUrl(casProperties.getCasTicketCallback());
        filter.setAuthenticationSuccessHandler(customCasSuccessHandler);
        return filter;
    }

    /**
     * Fournisseur d'authentitication qui va faire la requête au CAS pour valider le ticket reçu
     */
    @Bean
    public CasAuthenticationProvider casAuthenticationProvider(ServiceProperties serviceProperties, CasProperties casProperties) {
        CasAuthenticationProvider provider = new CasAuthenticationProvider();
        provider.setServiceProperties(serviceProperties);
        provider.setTicketValidator(new CustomCas30ServiceTicketValidator(casProperties.getCasServerUrl(), false, casProperties));
        provider.setAuthenticationUserDetailsService(customUserDetailsService());
        provider.setKey(casProperties.getCasProviderKey());
        return provider;
    }

    /**
     * Transformer l’utilisateur CAS en utilisateur Spring Security pour remplir les User Details
     * Calcule et stocke les droits de l'utilisateur pour les réutiliser plus tard sans avoir à tout recalculer
     */
    @Bean
    public AuthenticationUserDetailsService<CasAssertionAuthenticationToken> customUserDetailsService() {
        return (CasAssertionAuthenticationToken token) -> {
            final Assertion assertion = token.getAssertion();
            final Map<String, Object> attributes = assertion.getPrincipal().getAttributes();
            // TODO : attributs en dur pour tester
            final String uid = assertion.getPrincipal().getName();
            final String domaine = "TODO";
            final String username = assertion.getPrincipal().getName();
            log.debug("User {} logged in with rights {} and {}", username, uid, domaine);
            return new AppUser(username, "", new ArrayList<>(), "", "");
        };
    }

    /**
     * Transfert la demande d'auth depuis le filter vers le provider
     */
    @Bean
    public AuthenticationManager authenticationManager(CasAuthenticationProvider casAuthenticationProvider) {
        return new ProviderManager(casAuthenticationProvider);
    }

    /**
     * Gérer les requêtes de SLO qui arrivent du CAS pour détruire la session applicative
     */
    @Bean
    public Filter singleSignOutFilter() {
        SingleSignOutFilter delegate = new SingleSignOutFilter();
        delegate.setIgnoreInitConfiguration(true);
        delegate.setArtifactParameterName("ticket");
        delegate.setLogoutParameterName("logoutRequest");

        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                String logoutRequest = request.getParameter("logoutRequest");
                String ip = request.getRemoteAddr();
                String uri = request.getRequestURI();
                String method = request.getMethod();

                log.debug("[SLO] Requête entrante : {} {} depuis IP={}", method, uri, ip);

                if (logoutRequest != null) {
                    log.trace("[SLO] URI appelée : {}", uri);
                    log.trace("[SLO] Adresse IP appelante : {}", ip);
                    log.trace("[SLO] XML logoutRequest brut :\n{}", logoutRequest);

                    // Parsing XML SAML pour extraire le ticket (SessionIndex)
                    try {
                        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                        var builder = factory.newDocumentBuilder();
                        var doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(logoutRequest)));
                        doc.getDocumentElement().normalize();

                        var nameIdNode = doc.getElementsByTagName("saml:NameID").item(0);
                        var sessionIndexNode = doc.getElementsByTagName("samlp:SessionIndex").item(0);

                        String nameId = nameIdNode != null ? nameIdNode.getTextContent() : "inconnu";
                        String ticket = sessionIndexNode != null ? sessionIndexNode.getTextContent() : "inconnu";

                        // Lors du logout, le CAS envoie aussi des messages pour invalider les PGT, mais ici on ne traite que les
                        // SessionTicket, qui commencent par ST

                        int index = ticket.indexOf('-');
                        boolean isSessionTicket = false;

                        if (index != -1) {
                            String beforeDash = ticket.substring(0, index + 1);
                            if("ST-".equals(beforeDash)){
                                isSessionTicket = true;
                            }
                        }

                        if(isSessionTicket){
                            log.debug("[SLO] Ticket Invalidation Request will be handled: {}", ticket);
                        }else {
                            log.debug("[SLO] Ticket Invalidation Request will be ignored: {}", ticket);
                            filterChain.doFilter(request, response);
                            return;
                        }

                        String sessionId = customSessionMappingStorage.getSessionIdFromSessionTicket(ticket);

                        log.debug("[SLO] Utilisateur CAS (NameID) : {}", nameId);
                        log.debug("[SLO] Session id: {}", sessionId);

                        customSessionMappingStorage.removeSessionTicket(ticket);
                        log.debug("[SLO] Le cache associé au mappage ticket-sessionID [{}:{}] a été supprimé avec succès.", ticket, sessionId);
                        customSessionMappingStorage.deleteSessionContext(sessionId);
                        log.debug("[SLO] Invalidation réussie de la session [{}].", sessionId);

                    } catch (Exception e) {
                        log.error("[SLO] Erreur de parsing XML logoutRequest", e);
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment) throws Exception {
        // TODO : enable CSRF
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(Customizer.withDefaults());
        http.authorizeHttpRequests(authz -> {
            if (!environment.acceptsProfiles(Profiles.of("prod"))) {
                authz.antMatchers(SWAGGER_WHITELIST).permitAll();
            }
            authz
                .antMatchers("/health-check", "/", "/ui/**").permitAll()
                .antMatchers("/api/**").authenticated()
                // Cet endpoint doit être accessible car c'est le callback du CAS vers l'appli spring pour faire valider le ticket
                .antMatchers(casProperties.getCasTicketCallback()).permitAll()
                .anyRequest().denyAll();
        });
        http.exceptionHandling(e -> e
            .authenticationEntryPoint(casAuthenticationEntryPoint(serviceProperties()))
        );

        return http.build();
    }
}
