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
package fr.recia.mce.api.escomceapi.configuration.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;

class SoffitInterceptorTest {

    private SoffitHolder soffitHolder;
    private SoffitInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        soffitHolder = new SoffitHolder();
        interceptor = new SoffitInterceptor(soffitHolder);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void shouldReturnTrueWhenNoAuthHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(soffitHolder.getSub()).isNull();
    }

    @Test
    void shouldReturnTrueWhenNotBearerToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic xxx");
        when(request.getRequestURI()).thenReturn("/api/test");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(soffitHolder.getSub()).isNull();
    }

    @Test
    void shouldReturnTrueWhenMalformedJwt() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-jwt");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(soffitHolder.getSub()).isNull();
    }

    @Test
    void shouldReturnTrueWhenSubIsNull() throws Exception {
        String payload = Base64.getUrlEncoder().encodeToString("{\"exp\":9999999999}".getBytes());
        when(request.getHeader("Authorization")).thenReturn("Bearer header." + payload + ".signature");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(soffitHolder.getSub()).isNull();
    }

    @Test
    void shouldSetSubWhenValidToken() throws Exception {
        String payload = Base64.getUrlEncoder().encodeToString("{\"sub\":\"testuser\",\"exp\":9999999999}".getBytes());
        when(request.getHeader("Authorization")).thenReturn("Bearer header." + payload + ".signature");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(soffitHolder.getSub()).isEqualTo("testuser");
    }

    @Test
    void shouldReturn401WhenTokenExpired() throws Exception {
        String payload = Base64.getUrlEncoder().encodeToString("{\"sub\":\"testuser\",\"exp\":1}".getBytes());
        when(request.getHeader("Authorization")).thenReturn("Bearer header." + payload + ".signature");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        verify(response).setStatus(401);
    }

}
