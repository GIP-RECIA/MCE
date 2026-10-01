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

import lombok.Data;

@Data
public class CorsProperties {

    private static final String JSON_DELIMITER = "\", \"";
    private static final String JSON_PREFIX = "[ \"";
    private static final String JSON_SUFFIX = "\" ]";

    private boolean enable;
    private boolean allowCredentials;
    private List<String> allowedOrigins;
    private List<String> exposedHeaders;
    private List<String> allowedHeaders;
    private List<String> allowedMethods;

}
