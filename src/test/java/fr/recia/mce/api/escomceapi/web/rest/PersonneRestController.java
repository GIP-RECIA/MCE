package fr.recia.mce.api.escomceapi.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.impl.RelationEleveServiceImpl;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonneRestController.class)
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

    @Test
    @DisplayName("OK - change password")
    @WithMockUser(username = "test.user")
    void shouldChangePasswordSuccessfully() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("OldPass123!");
        request.setNewPass("NewPass456!");
        request.setConfirmPass("NewPass456!");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("test.user");

        doNothing().when(userDTOFactory)
                .changePassword(eq("test.user"), any());

        mockMvc.perform(post("/api/personne/mce/test.user/change-password")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userDTOFactory).changePassword(eq("test.user"), any());
    }

    @Test
    @DisplayName("FORBIDDEN - change password d'un autre utilisateur")
    @WithMockUser(username = "test.user")
    void shouldReturnForbiddenWhenChangingAnotherUserPassword() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("OldPass123!");
        request.setNewPass("NewPass456!");
        request.setConfirmPass("NewPass456!");

        mockMvc.perform(post("/api/personne/mce/autre.user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(userDTOFactory, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("UNAUTHORIZED - change password sans être authentifié")
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("OldPass123!");
        request.setNewPass("NewPass456!");
        request.setConfirmPass("NewPass456!");

        mockMvc.perform(post("/api/personne/mce/test.user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(userDTOFactory, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("BAD REQUEST - body invalide (champs vides)")
    @WithMockUser(username = "test.user")
    void shouldReturnBadRequestWhenBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/personne/mce/test.user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(userDTOFactory, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("INTERNAL SERVER ERROR - exception inattendue dans le service")
    @WithMockUser(username = "test.user")
    void shouldReturnInternalServerErrorWhenServiceThrows() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPass("OldPass123!");
        request.setNewPass("NewPass456!");
        request.setConfirmPass("NewPass456!");

        doThrow(new RuntimeException("Erreur LDAP"))
                .when(userDTOFactory).changePassword(eq("test.user"), any());

        mockMvc.perform(post("/api/personne/mce/test.user/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(userDTOFactory).changePassword(eq("test.user"), any());
    }
}