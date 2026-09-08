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

import fr.recia.mce.api.escomceapi.configuration.interceptor.CharteInterceptor;
import fr.recia.mce.api.escomceapi.configuration.interceptor.SoffitInterceptor;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.services.CharteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private final SoffitHolder soffitHolder;
    private final CharteService charteService;

    public InterceptorConfig(SoffitHolder soffitHolder, CharteService charteService) {
        this.soffitHolder = soffitHolder;
        this.charteService = charteService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // L'ordre est important : SoffitInterceptor doit s'exécuter en premier pour
        // renseigner le SoffitHolder.sub, que CharteInterceptor consomme ensuite.
        registry.addInterceptor(soffitInterceptor());
        registry.addInterceptor(charteInterceptor());
    }

    @Bean
    public SoffitInterceptor soffitInterceptor() {
        return new SoffitInterceptor(soffitHolder);
    }

    @Bean
    public CharteInterceptor charteInterceptor() {
        return new CharteInterceptor(soffitHolder, charteService);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public SoffitHolder soffitHolder() {
        return new SoffitHolder();
    }

}
