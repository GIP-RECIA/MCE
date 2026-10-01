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

package fr.recia.mce.api.escomceapi.configuration.cas;

import fr.recia.mce.api.escomceapi.configuration.bean.CasProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Classe custom qui permet de gérer le multidomaine lors de la demande d'émission d'un ST
 */
@Slf4j
public class CustomCasAuthenticationEntryPoint extends CasAuthenticationEntryPoint {

    @Autowired
    private CasProperties casProperties;

    @Override
    protected String createServiceUrl(HttpServletRequest request, HttpServletResponse response) {
        final String url = request.getRequestURL().toString();
        final String uri = request.getRequestURI();
        final String domain = url.substring(0, url.length() - uri.length());
        if(casProperties.getAuthorizedDomains().contains(domain)){
            return domain + casProperties.getCasServiceId();
        } else {
            throw new InvalidDomainException("Domain " + domain + " is not authorized !");
        }
    }

}
