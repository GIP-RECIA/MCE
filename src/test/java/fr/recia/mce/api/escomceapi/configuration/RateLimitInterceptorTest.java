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

import fr.recia.mce.api.escomceapi.configuration.bean.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests - RateLimitInterceptor")
class RateLimitInterceptorTest {

    private MCEProperties mceProperties;
    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        mceProperties = new MCEProperties();
        interceptor = new RateLimitInterceptor(mceProperties);
    }

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/personne/mce/forgot-password");
        request.setRemoteAddr(ip);
        return request;
    }

    private SecurityProperties.RateLimit policy() {
        return mceProperties.getSecurity().getRateLimit();
    }

    @Test
    @DisplayName("La première requête d'une IP est autorisée")
    void allowsFirstRequest() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("10.0.0.1"), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Rafale immédiate : la seconde requête est bloquée en 429 RATE_LIMIT_EXCEEDED")
    void blocksBurstRequests() throws Exception {
        SecurityProperties.RateLimit limit = policy();
        limit.setPermitsPerSecond(0.001);

        assertThat(interceptor.preHandle(request("10.0.0.2"), new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("10.0.0.2"), blocked, new Object());

        assertThat(allowed).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(blocked.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("Les IPs sont limitées indépendamment")
    void limitsArePerIp() {
        policy().setPermitsPerSecond(0.001);

        assertThat(interceptor.preHandle(request("10.0.1.1"), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(request("10.0.1.1"), new MockHttpServletResponse(), new Object())).isFalse();

        assertThat(interceptor.preHandle(request("10.0.1.2"), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    @DisplayName("maxEntries atteint : les nouvelles IPs passent sans limiteur (dégradation contrôlée)")
    void stopsCreatingLimitersAtMaxEntries() {
        SecurityProperties.RateLimit limit = policy();
        limit.setMaxEntries(1);

        assertThat(interceptor.preHandle(request("10.1.0.1"), new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request("10.1.0.2"), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
