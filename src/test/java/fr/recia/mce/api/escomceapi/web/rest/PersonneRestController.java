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

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.recia.mce.api.escomceapi.configuration.interceptor.SoffitInterceptor;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(PersonneRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PersonneRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersonneService personneService;

    @MockBean
    private RelationEleveServiceImpl relationEleveServiceImpl;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    @MockBean
    private PasswordService passwordService;

    @MockBean
    private LdapTemplate ldapTemplate;

    @MockBean
    private FonctionService fonctionService;

    @MockBean
    private SoffitHolder soffitHolder;

    @MockBean
    private SoffitInterceptor soffitInterceptor;

    private static final String BASE_URL = "/api/personne/mce/";
    private static final String USER = "test.user";

    @BeforeEach
    void setUp() throws Exception {
        // Simule utilisateur connecté
        when(soffitHolder.getSub()).thenReturn(USER);

        when(soffitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    private PasswordChangeRequest buildValidRequest() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("OldPass123!");
        request.setNewPass("NewPass456!");
        request.setConfirmPass("NewPass456!");
        return request;
    }

    @Test
    void shouldChangePasswordSuccessfully() throws Exception {
        PasswordChangeRequest request = buildValidRequest();

        doNothing().when(userDTOFactory).changePassword(eq(USER), any());

        mockMvc.perform(post(BASE_URL + USER + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userDTOFactory).changePassword(eq(USER), any());
    }

    @Test
    void shouldReturnForbiddenWhenChangingAnotherUserPassword() throws Exception {
        PasswordChangeRequest request = buildValidRequest();

        mockMvc.perform(post(BASE_URL + "autre.user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(userDTOFactory, never()).changePassword(any(), any());
    }

    @Test
    void shouldReturnForbiddenWhenNotAuthenticated() throws Exception {
        when(soffitHolder.getSub()).thenReturn(null);

        PasswordChangeRequest request = buildValidRequest();

        mockMvc.perform(post(BASE_URL + USER + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(userDTOFactory, never()).changePassword(any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenBodyIsInvalid() throws Exception {
        mockMvc.perform(post(BASE_URL + USER + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(userDTOFactory, never()).changePassword(any(), any());
    }

    @Test
    void shouldReturnInternalServerErrorWhenServiceThrows() throws Exception {
        PasswordChangeRequest request = buildValidRequest();

        doThrow(new RuntimeException("Erreur LDAP"))
                .when(userDTOFactory).changePassword(eq(USER), any());

        mockMvc.perform(post(BASE_URL + USER + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(userDTOFactory).changePassword(eq(USER), any());
    }
}