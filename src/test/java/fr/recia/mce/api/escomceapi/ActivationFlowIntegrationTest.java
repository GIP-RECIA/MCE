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
package fr.recia.mce.api.escomceapi;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.services.EmailVerificationService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie les scénarios du document <em>docs/test-front-activation.md</em> au niveau HTTP :
 * controller {@code PersonneRestController} réel, {@code ActivationService} réel, base H2 réelle ;
 * les dépendances lourdes (LDAP / DTO) sont mockées comme dans {@code PasswordResetIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class ActivationFlowIntegrationTest {

    private static final String TEMP_PASSWORD = "Temp1234";
    private static final String NEW_PASSWORD = "N3wPassw0rd!X";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private APersonneRepository personneRepository;

    @Autowired
    private CerbereConfirmationRepository confirmationRepository;

    @Autowired
    private MCEProperties mceProperties;

    @MockBean
    private PersonneService personneService;

    @SpyBean
    private PasswordService passwordService;

    @MockBean
    private IUserDTOFactory userDTOFactory;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        mceProperties.getSecurity().getRateLimit().setPermitsPerSecond(1_000_000.0);
        confirmationRepository.deleteAll();
        personneRepository.deleteAll();
        reset(mailSender, passwordService, personneService, userDTOFactory);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private APersonne savePersonne(String uid, String etat, String email, boolean charteSignee, String storedPassword) {
        APersonne p = new APersonne();
        p.setUid(uid);
        p.setSource("test");
        p.setCle(uid + "-cle");
        p.setVersion(0L);
        p.setEtat(etat);
        p.setCategorie("Enseignant");
        p.setEmail(email);
        if (storedPassword != null) {
            p.setPassword(storedPassword);
        }
        if (charteSignee) {
            p.setValidationCharte(new Date());
        }
        return personneRepository.saveAndFlush(p);
    }

    /** Stub : retourne un {@code PersonneDTO} portant l'état charte demandé, pour des appels successifs. */
    private void stubUserByUid(String uid, boolean charteValide) {
        APersonne base = new APersonne();
        base.setUid(uid);
        base.setSource("test");
        if (charteValide) {
            base.setValidationCharte(new Date());
        }
        when(personneService.getUserByUid(uid)).thenReturn(new PersonneDTO(base));
    }

    /** Stub : premier appel charte non signée, puis second appel charte signée (après signature). */
    private void stubUserByUidChartePuisSignee(String uid) {
        APersonne sansCharte = new APersonne();
        sansCharte.setUid(uid);
        APersonne signee = new APersonne();
        signee.setUid(uid);
        signee.setValidationCharte(new Date());
        when(personneService.getUserByUid(uid)).thenReturn(new PersonneDTO(sansCharte), new PersonneDTO(signee));
    }

    private void stubProfil(EnumPublic profil) {
        when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(profil);
    }

    private void connexionOk(String uid) throws Exception {
        stubProfil(EnumPublic.PERSONNEL);
        mockMvc.perform(post("/api/personne/mce/activation/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + uid + "\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value(uid));
    }

    // ---------------------------------------------------------------
    // Scénarios nominaux (docs/test-front-activation.md §4)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A - CHARTE puis PASSWORD : profil local, charte non signée, activation complète")
    void scenarioA_completChartePassword() throws Exception {
        savePersonne("act-a", "Invalide", "a@exemple.fr", false, TEMP_PASSWORD);
        stubUserByUidChartePuisSignee("act-a");
        stubProfil(EnumPublic.PERSONNEL);
        doNothing().when(passwordService).resetPassword(any(PersonneDTO.class), anyString(), anyString());

        // ① connexion
        connexionOk("act-a");

        // ② statut → CHARTE
        mockMvc.perform(get("/api/personne/mce/activation/status").param("uid", "act-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeSuivante").value("CHARTE"))
                .andExpect(jsonPath("$.charteRequise").value(true))
                .andExpect(jsonPath("$.charteSignee").value(false));

        // ③ activation
        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-a\",\"charteAccepted\":true,"
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("Valide"))
                .andExpect(jsonPath("$.emailEnAttenteDeVerification").value(false));

        verify(personneService).signCharte("act-a");
        verify(passwordService).resetPassword(any(PersonneDTO.class), anyString(), anyString());
        verify(personneService).valideCompte("act-a");
    }

    @Test
    @DisplayName("B - COURRIEL : élève sans email, charte signée → email + mot de passe puis vérification du code")
    void scenarioB_eleveCourriel() throws Exception {
        savePersonne("act-b", "Invalide", null, true, TEMP_PASSWORD);
        stubUserByUid("act-b", true);
        stubProfil(EnumPublic.ELEVE);
        doNothing().when(passwordService).resetPassword(any(PersonneDTO.class), anyString(), anyString());

        // ① connexion
        mockMvc.perform(post("/api/personne/mce/activation/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"act-b\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("act-b"));

        // ② statut → COURRIEL (élève, email vide)
        mockMvc.perform(get("/api/personne/mce/activation/status").param("uid", "act-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeSuivante").value("COURRIEL"))
                .andExpect(jsonPath("$.emailRequise").value(true))
                .andExpect(jsonPath("$.charteRequise").value(false));

        // ③ activation : email ET mot de passe (le profil élève se connecte par mot de passe local)
        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-b\",\"charteAccepted\":true,"
                                + "\"email\":\"b@exemple.fr\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("Valide"))
                .andExpect(jsonPath("$.emailEnAttenteDeVerification").value(true));

        // Code reçu par email puis vérifié
        org.mockito.ArgumentCaptor<SimpleMailMessage> captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        Matcher codeMatcher = Pattern.compile("\\b\\d{6}\\b").matcher(captor.getValue().getText());
        assertThat(codeMatcher.find()).as("l'email doit contenir un code à 6 chiffres").isTrue();
        String code = codeMatcher.group();

        mockMvc.perform(post("/api/personne/mce/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-b\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("C - FIN : profil sans mot de passe local → connexion refusée, statut FIN")
    void scenarioC_profilSansMdpLocal() throws Exception {
        savePersonne("act-c", "Invalide", "c@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-c", true);
        stubProfil(EnumPublic.AGRI);

        // ① connexion refusée : pas de mot de passe local pour ce profil
        mockMvc.perform(post("/api/personne/mce/activation/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"act-c\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Ce compte ne peut pas être activé avec un mot de passe"));

        // ② statut → FIN
        mockMvc.perform(get("/api/personne/mce/activation/status").param("uid", "act-c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeSuivante").value("FIN"))
                .andExpect(jsonPath("$.passwordRequise").value(false))
                .andExpect(jsonPath("$.charteRequise").value(false));
    }

    @Test
    @DisplayName("D - PASSWORD direct : profil local, charte signée, email déjà présent")
    void scenarioD_passwordDirect() throws Exception {
        savePersonne("act-d", "Invalide", "d@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-d", true);
        stubProfil(EnumPublic.PERSONNEL);
        doNothing().when(passwordService).resetPassword(any(PersonneDTO.class), anyString(), anyString());

        connexionOk("act-d");

        mockMvc.perform(get("/api/personne/mce/activation/status").param("uid", "act-d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeSuivante").value("PASSWORD"))
                .andExpect(jsonPath("$.passwordRequise").value(true))
                .andExpect(jsonPath("$.emailRequise").value(false));

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-d\",\"charteAccepted\":true,"
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("Valide"));
    }

    // ---------------------------------------------------------------
    // Cas d'erreur (docs/test-front-activation.md §5)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("E1 - login inexistant : 400 BAD_REQUEST vague")
    void erreurE1_loginInconnu() throws Exception {
        mockMvc.perform(post("/api/personne/mce/activation/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"nobody\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Identifiants incorrects"));
    }

    @Test
    @DisplayName("E1 bis - mot de passe erroné : 400 BAD_REQUEST vague (vérification hash réelle)")
    void erreurE1_motDePasseErrone() throws Exception {
        savePersonne("act-e1", "Invalide", "e1@exemple.fr", false, TEMP_PASSWORD);
        stubProfil(EnumPublic.PERSONNEL);

        mockMvc.perform(post("/api/personne/mce/activation/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"act-e1\",\"password\":\"MauvaisMdp\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Identifiants incorrects"));
    }

    @Test
    @DisplayName("E2 - compte déjà Valide : 400 BAD_REQUEST vague")
    void erreurE2_compteDejaValide() throws Exception {
        savePersonne("act-e2", "Valide", "e2@exemple.fr", true, TEMP_PASSWORD);
        stubProfil(EnumPublic.PERSONNEL);

        mockMvc.perform(post("/api/personne/mce/activation/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"act-e2\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Identifiants incorrects"));
    }

    @Test
    @DisplayName("E3 - statut uid inconnu : 404 NOT_FOUND ; uid vide : 400 BAD_REQUEST")
    void erreurE3_uidInconnu() throws Exception {
        mockMvc.perform(get("/api/personne/mce/activation/status").param("uid", "inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/personne/mce/activation/status").param("uid", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("E4 - activation uid inconnu : 404 NOT_FOUND")
    void erreurE4_activationUidInconnu() throws Exception {
        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"inconnu\",\"charteAccepted\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("E5 - charte non signée et non acceptée : 400 CHARTE_REQUIRED")
    void erreurE5_charteNonAcceptee() throws Exception {
        savePersonne("act-e5", "Invalide", "e5@exemple.fr", false, TEMP_PASSWORD);
        stubUserByUid("act-e5", false);
        stubProfil(EnumPublic.PERSONNEL);

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-e5\",\"charteAccepted\":false,"
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHARTE_REQUIRED"));
    }

    @Test
    @DisplayName("E6 - mot de passe vide : 400 BAD_REQUEST")
    void erreurE6_motDePasseVide() throws Exception {
        savePersonne("act-e6", "Invalide", "e6@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-e6", true);
        stubProfil(EnumPublic.PERSONNEL);

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-e6\",\"charteAccepted\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Le nouveau mot de passe est obligatoire pour ce compte"));
    }

    @Test
    @DisplayName("E7 - confirmation différente : 400 BAD_REQUEST (réel resetPassword)")
    void erreurE7_confirmationDifferente() throws Exception {
        savePersonne("act-e7", "Invalide", "e7@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-e7", true);
        stubProfil(EnumPublic.PERSONNEL);

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-e7\",\"charteAccepted\":true,"
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"AutreMdp!2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("La confirmation du mot de passe ne correspond pas"));
    }

    @Test
    @DisplayName("E8 - mot de passe faible : 400 WEAK_PASSWORD (réel resetPassword)")
    void erreurE8_motDePasseFaible() throws Exception {
        savePersonne("act-e8", "Invalide", "e8@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-e8", true);
        stubProfil(EnumPublic.PERSONNEL);

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-e8\",\"charteAccepted\":true,"
                                + "\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    @DisplayName("E9 - email invalide : 400 BAD_REQUEST")
    void erreurE9_emailInvalide() throws Exception {
        savePersonne("act-e9", "Invalide", "e9@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-e9", true);
        stubProfil(EnumPublic.PERSONNEL);
        doNothing().when(passwordService).resetPassword(any(PersonneDTO.class), anyString(), anyString());
        doThrow(new IllegalArgumentException("Le format de l'adresse email n'est pas valide"))
                .when(personneService).validateEmailForUpdate("act-e9", "email-invalide");

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-e9\",\"charteAccepted\":true,"
                                + "\"email\":\"email-invalide\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("E0 - conjonction (A sans acceptation pas en double) : la charte déjà signée ne redemande rien")
    void charteDejaSigneeAucunReappelSignature() throws Exception {
        savePersonne("act-e0", "Invalide", "e0@exemple.fr", true, TEMP_PASSWORD);
        stubUserByUid("act-e0", true);
        stubProfil(EnumPublic.PERSONNEL);
        doNothing().when(passwordService).resetPassword(any(PersonneDTO.class), anyString(), anyString());

        mockMvc.perform(post("/api/personne/mce/activation/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"act-e0\",\"charteAccepted\":false,"
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("Valide"));

        verify(personneService, never()).signCharte("act-e0");
    }
}