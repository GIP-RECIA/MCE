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

import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.core.JsonProcessingException;

import fr.recia.mce.api.escomceapi.configuration.bean.CustomLdapProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.SoffitProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Configuration
@ConfigurationProperties(prefix = "app", ignoreUnknownFields = false)
@Data
@Validated
@Slf4j
public class MCEProperties {

    private CustomLdapProperties ldap = new CustomLdapProperties();
    private ServiceProperties service = new ServiceProperties();
    private SoffitProperties soffit = new SoffitProperties();

    @PostConstruct
    private void init() throws JsonProcessingException {
        log.info("Loaded properties: {}", this);
    }

    @Override
    public String toString() {
        return "{\n\"MCEProperties\":{"
                + ",\n\t \"ldap\":" + ldap
                + ",\n\t \"service\":" + service
                + ",\n\t \"soffit\":" + soffit
                + "\n\t}\n}";
    }

}
