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

package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "redis")
@Data
@Validated
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class RedisProperties {

    private String hostName;
    private int port;
    private String userName;
    private String password;
    private int databaseIndex;
    private String mappingPrefix;
    private String indexPrefix;

    @PostConstruct
    public void setupAndDebug() {
        if(databaseIndex < 0){
            databaseIndex = 0;
            log.warn("Negative database index provided, defaulted to 0");
        }
    }

}
