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

import fr.recia.mce.api.escomceapi.configuration.bean.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final MCEProperties mceProperties;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, MCEProperties mceProperties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.mceProperties = mceProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/personne/mce/recover-uid",
                        "/api/personne/mce/forgot-password",
                        "/api/personne/mce/reset-password",
                        "/api/personne/mce/verify-email",
                        "/api/personne/mce/activation/connexion",
                        "/api/personne/mce/activation/password",
                        "/api/personne/mce/activation/self",
                        "/api/personne/mce/change-password",
                        "/api/personne/mce/update-email",
                        "/api/personne/mce/avatar");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsProperties cors = mceProperties.getCors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(cors.isAllowCredentials());
        config.setAllowedOriginPatterns(cors.getAllowedOrigins());
        config.setAllowedMethods(cors.getAllowedMethods());
        config.setAllowedHeaders(cors.getAllowedHeaders());
        config.setExposedHeaders(cors.getExposedHeaders());
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Sécurité par défaut : si CORS désactivé, on n'enregistre qu'une config vide
        // (aucune origine autorisée → aucun header CORS émis → requêtes cross-origin bloquées).
        if (cors.isEnable()) {
            source.registerCorsConfiguration("/api/**", config);
        } else {
            source.registerCorsConfiguration("/api/**", new CorsConfiguration());
        }
        return source;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Réactive le service des ressources statiques (spécifiquement pour les pages
        // publiques servies par la SPA Vue/TS) alors que spring.web.resources.add-mappings=false.
        // Elles restent protégées par la liste blanche PUBLIC_PAGES (voir SecurityConfiguration).
        registry.addResourceHandler("/", "/index.html")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
    }
}
