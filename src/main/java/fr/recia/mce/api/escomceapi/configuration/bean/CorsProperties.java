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

import java.util.List;
import java.util.stream.Collectors;

import static fr.recia.mce.api.escomceapi.configuration.Constants.PROPERTIES_TO_JSON_DELIMITER;
import static fr.recia.mce.api.escomceapi.configuration.Constants.PROPERTIES_TO_JSON_PREFIX;
import static fr.recia.mce.api.escomceapi.configuration.Constants.PROPERTIES_TO_JSON_SUFFIX;

import lombok.Data;

@Data
public class CorsProperties {

    private boolean enable;
    private boolean allowCredentials;
    private List<String> allowedOrigins;
    private List<String> exposedHeaders;
    private List<String> allowedHeaders;
    private List<String> allowedMethods;

    @Override
    public String toString() {
        return "\"CorsProperties\": {" +
                "\n\t\"enable\": " + enable +
                ",\n\t\"allowCredentials\": " + allowCredentials +
                ",\n\t\"allowedOrigins\": " + allowedOrigins.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(PROPERTIES_TO_JSON_DELIMITER, PROPERTIES_TO_JSON_PREFIX,
                                PROPERTIES_TO_JSON_SUFFIX))
                +
                ",\n\t\"exposedHeaders\": " + exposedHeaders.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(PROPERTIES_TO_JSON_DELIMITER, PROPERTIES_TO_JSON_PREFIX,
                                PROPERTIES_TO_JSON_SUFFIX))
                +
                ",\n\t\"allowedHeaders\": " + allowedHeaders.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(PROPERTIES_TO_JSON_DELIMITER, PROPERTIES_TO_JSON_PREFIX,
                                PROPERTIES_TO_JSON_SUFFIX))
                +
                ",\n\t\"allowedMethods\": " + allowedMethods.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(PROPERTIES_TO_JSON_DELIMITER, PROPERTIES_TO_JSON_PREFIX,
                                PROPERTIES_TO_JSON_SUFFIX))
                +
                "\n}";
    }

}
