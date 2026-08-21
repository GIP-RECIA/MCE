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
package fr.recia.mce.api.escomceapi.web.rest;

import fr.recia.mce.api.escomceapi.services.CharteUrlResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/charte")
public class CharteController {

    private final CharteUrlResolver charteUrlResolver;

    public CharteController(CharteUrlResolver charteUrlResolver) {
        this.charteUrlResolver = charteUrlResolver;
    }

    @GetMapping("/url")
    public ResponseEntity<Map<String, String>> getCharteUrl(
            @RequestHeader("Host") String host) {
        String serverName = host.split(":")[0];
        String url = charteUrlResolver.resolve(serverName);
        return ResponseEntity.ok(Map.of("charteUrl", url));
    }
}
