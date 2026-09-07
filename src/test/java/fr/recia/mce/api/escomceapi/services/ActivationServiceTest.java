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
package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.services.exception.CharteNotAcceptedException;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.ActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationResultDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationStatusResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests - ActivationService")
class ActivationServiceTest {

    @Mock
    private APersonneRepository aPersonneRepository;

    @Mock
    private PersonneService personneService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private IUserDTOFactory userDTOFactory;

    // Spy : isCharteRequired(APersonne) exécute la vraie règle de domaine
    // (charte requise tant que validationCharte est null), comme l'ancien contrôle inline.
    @Spy
    private CharteService charteService = new CharteService();

    @InjectMocks
    private ActivationService activationService;

    private APersonne personne(String uid, String etat, String categorie, String email, Date validationCharte) {
        APersonne p = new APersonne();
        p.setUid(uid);
        p.setEtat(etat);
        p.setCategorie(categorie);
        p.setEmail(email);
        p.setValidationCharte(validationCharte);
        p.setVersion(0L);
        return p;
    }

    private ActivationRequestDTO request(String uid, boolean charteAccepted, String email,
            String newPassword, String confirmPassword) {
        ActivationRequestDTO r = new ActivationRequestDTO();
        r.setUid(uid);
        r.setCharteAccepted(charteAccepted);
        r.setEmail(email);
        r.setNewPassword(newPassword);
        r.setConfirmPassword(confirmPassword);
        return r;
    }

    @Nested
    @DisplayName("Connexion (login + mot de passe temporaire)")
    class ConnexionTests {

        @Test
        @DisplayName("Succès avec login = uid")
        void shouldReturnUidWhenCredentialsValid() {
            APersonne p = personne("dupontj", "Invalide", "Enseignant", "jean@ac.fr", new Date());
            when(aPersonneRepository.findByLogin("dupontj")).thenReturn(p);
            when(passwordService.verifyPassword(any(PersonneDTO.class), eq("TempPass1!"), eq(true))).thenReturn(true);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PERSONNEL);

            assertThat(activationService.connexion("dupontj", "TempPass1!")).isEqualTo("dupontj");
        }

        @Test
        @DisplayName("Mauvais mot de passe → Identifiants incorrects")
        void shouldRejectWrongPassword() {
            APersonne p = personne("dupontj", "Invalide", "Enseignant", null, null);
            when(aPersonneRepository.findByLogin("dupontj")).thenReturn(p);
            when(passwordService.verifyPassword(any(PersonneDTO.class), anyString(), anyBoolean())).thenReturn(false);

            assertThatThrownBy(() -> activationService.connexion("dupontj", "Mauvais"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Identifiants incorrects");
        }

        @Test
        @DisplayName("Compte déjà actif → refus sans divulguer l'existence du compte")
        void shouldRejectAlreadyActiveAccount() {
            APersonne p = personne("dupontj", "Valide", "Enseignant", null, null);
            when(aPersonneRepository.findByLogin("dupontj")).thenReturn(p);

            assertThatThrownBy(() -> activationService.connexion("dupontj", "nimporte"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Identifiants incorrects");

            verify(passwordService, never()).verifyPassword(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Profil SSO uniquement (EDUCATION) → refus")
        void shouldRejectSsoOnlyProfile() {
            APersonne p = personne("dupontj", "Invalide", "Enseignant", null, null);
            when(aPersonneRepository.findByLogin("dupontj")).thenReturn(p);
            when(passwordService.verifyPassword(any(PersonneDTO.class), anyString(), anyBoolean())).thenReturn(true);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.EDUCATION);

            assertThatThrownBy(() -> activationService.connexion("dupontj", "TempPass1!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ne peut pas être activé");
        }

        @Test
        @DisplayName("Login vide → IllegalArgumentException")
        void shouldRejectBlankLogin() {
            assertThatThrownBy(() -> activationService.connexion("  ", "TempPass1!"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(aPersonneRepository, never()).findByLogin(anyString());
        }
    }

    @Nested
    @DisplayName("Statut d'activation")
    class StatusTests {

        private PersonneDTO dto(APersonne p) {
            return new PersonneDTO(p);
        }

        @Test
        @DisplayName("PERSONNEL sans charte → CHARTE, email et mdp requis")
        void shouldRouteToCharteWhenCharteMissing() {
            APersonne p = personne("dupontj", "Invalide", "Enseignant", null, null);
            when(aPersonneRepository.findByUid("dupontj")).thenReturn(p);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PERSONNEL);

            ActivationStatusResponseDTO s = activationService.getActivationStatus("dupontj");

            assertThat(s.getEtapeSuivante()).isEqualTo("CHARTE");
            assertThat(s.isCharteRequise()).isTrue();
            assertThat(s.isPasswordRequise()).isTrue();
            assertThat(s.isEmailRequise()).isTrue();
        }

        @Test
        @DisplayName("PERSONNEL charte signée avec email → PASSWORD")
        void shouldRouteToPasswordWhenEmailPresent() {
            APersonne p = personne("dupontj", "Invalide", "Enseignant", "jean@ac.fr", new Date());
            when(aPersonneRepository.findByUid("dupontj")).thenReturn(p);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PERSONNEL);

            ActivationStatusResponseDTO s = activationService.getActivationStatus("dupontj");

            assertThat(s.getEtapeSuivante()).isEqualTo("PASSWORD");
            assertThat(s.isCharteRequise()).isFalse();
            assertThat(s.isPasswordRequise()).isTrue();
            assertThat(s.isEmailRequise()).isFalse();
        }

        @Test
        @DisplayName("ELEVE charte signée → COURRIEL avant PASSWORD")
        void shouldRouteEleveToCourriel() {
            APersonne p = personne("eleve1", "Invalide", "Eleve", "eleve@ent.fr", new Date());
            when(aPersonneRepository.findByUid("eleve1")).thenReturn(p);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.ELEVE);

            ActivationStatusResponseDTO s = activationService.getActivationStatus("eleve1");

            assertThat(s.getEtapeSuivante()).isEqualTo("COURRIEL");
            assertThat(s.isPasswordRequise()).isTrue();
        }

        @Test
        @DisplayName("CVDL charte signée → FIN sans mot de passe")
        void shouldRouteCvdlToFin() {
            APersonne p = personne("cvdl1", "Invalide", "Non_enseignant_collectivite_locale", null, new Date());
            when(aPersonneRepository.findByUid("cvdl1")).thenReturn(p);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.CVDL);

            ActivationStatusResponseDTO s = activationService.getActivationStatus("cvdl1");

            assertThat(s.getEtapeSuivante()).isEqualTo("FIN");
            assertThat(s.isPasswordRequise()).isFalse();
            assertThat(s.isEmailRequise()).isFalse();
        }

        @Test
        @DisplayName("Uid inconnu → PersonneNotFoundException")
        void shouldFailWhenUidUnknown() {
            when(aPersonneRepository.findByUid("ghost")).thenReturn(null);

            assertThatThrownBy(() -> activationService.getActivationStatus("ghost"))
                    .isInstanceOf(PersonneNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Activation du compte")
    class ActivateTests {

        @Test
        @DisplayName("Charte non signée + acceptée → signCharte puis resetPassword puis Valide")
        void shouldSignCharteAndSetPassword() {
            APersonne ihm = personne("dupontj", "Invalide", "Enseignant", null, null);
            PersonneDTO dtoSansCharte = new PersonneDTO(ihm);

            when(aPersonneRepository.findByUid("dupontj")).thenReturn(ihm);
            when(personneService.getUserByUid("dupontj")).thenReturn(dtoSansCharte);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PERSONNEL);

            ActivationResultDTO result = activationService.activate(
                    request("dupontj", true, null, "N3wPassw0rd!X", "N3wPassw0rd!X"));

            verify(personneService).signCharte("dupontj");
            // Pas de 2ᵉ chargement : la charte est reflétée en mémoire sur le même DTO.
            verify(personneService).getUserByUid("dupontj");
            assertThat(dtoSansCharte.isCharteValide()).isTrue();
            verify(passwordService).resetPassword(eq(dtoSansCharte), eq("N3wPassw0rd!X"), eq("N3wPassw0rd!X"));
            verify(personneService).valideCompte("dupontj");
            assertThat(result.getUid()).isEqualTo("dupontj");
            assertThat(result.getEtat()).isEqualTo("Valide");
            assertThat(result.isEmailEnAttenteDeVerification()).isFalse();
        }

        @Test
        @DisplayName("Charte non acceptée → CharteNotAcceptedException")
        void shouldRefuseWhenCharteNotAccepted() {
            APersonne ihm = personne("dupontj", "Invalide", "Enseignant", null, null);
            when(aPersonneRepository.findByUid("dupontj")).thenReturn(ihm);
            when(personneService.getUserByUid("dupontj")).thenReturn(new PersonneDTO(ihm));

            assertThatThrownBy(() -> activationService.activate(
                    request("dupontj", false, null, "N3wPassw0rd!X", "N3wPassw0rd!X")))
                    .isInstanceOf(CharteNotAcceptedException.class);

            verify(personneService, never()).signCharte(anyString());
            verify(personneService, never()).valideCompte(anyString());
        }

        @Test
        @DisplayName("Profil SSO (PARENT_EDUC) → aucun mot de passe créé, compte activé")
        void shouldActivateWithoutPasswordForSsoProfile() {
            APersonne ihm = personne("parent1", "Invalide", "Personne_relation_eleve", "parent@x.fr", new Date());
            PersonneDTO dto = new PersonneDTO(ihm);
            when(aPersonneRepository.findByUid("parent1")).thenReturn(ihm);
            when(personneService.getUserByUid("parent1")).thenReturn(dto);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PARENT_EDUC);

            ActivationResultDTO result = activationService.activate(
                    request("parent1", false, null, null, null));

            verify(passwordService, never()).resetPassword(any(), any(), any());
            verify(personneService).valideCompte("parent1");
            assertThat(result.getEtat()).isEqualTo("Valide");
        }

        @Test
        @DisplayName("Mot de passe manquant alors qu'il est requis → IllegalArgumentException")
        void shouldRequirePasswordWhenProfileConnectOk() {
            APersonne ihm = personne("dupontj", "Invalide", "Enseignant", null, new Date());
            PersonneDTO dto = new PersonneDTO(ihm);
            when(aPersonneRepository.findByUid("dupontj")).thenReturn(ihm);
            when(personneService.getUserByUid("dupontj")).thenReturn(dto);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PERSONNEL);

            assertThatThrownBy(() -> activationService.activate(
                    request("dupontj", false, null, "", "x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatoire");

            verify(personneService, never()).valideCompte(anyString());
        }

        @Test
        @DisplayName("Email saisi → code de vérification envoyé (étape COURRIEL)")
        void shouldSendVerificationEmailWhenEmailProvided() {
            APersonne ihm = personne("dupontj", "Invalide", "Enseignant", "jean@ac.fr", new Date());
            PersonneDTO dto = new PersonneDTO(ihm);
            when(aPersonneRepository.findByUid("dupontj")).thenReturn(ihm);
            when(personneService.getUserByUid("dupontj")).thenReturn(dto);
            when(userDTOFactory.evalPublic(any(PersonneDTO.class))).thenReturn(EnumPublic.PERSONNEL);

            ActivationResultDTO result = activationService.activate(
                    request("dupontj", false, "jean.perso@example.fr", "N3wPassw0rd!X", "N3wPassw0rd!X"));

            verify(personneService).validateEmailForUpdate("dupontj", "jean.perso@example.fr");
            verify(emailVerificationService).sendVerificationEmail("dupontj", "jean.perso@example.fr");
            assertThat(result.isEmailEnAttenteDeVerification()).isTrue();
        }

        @Test
        @DisplayName("Compte supprimé → refus")
        void shouldRefuseDeletedAccount() {
            APersonne ihm = personne("dead", "Delete", "Enseignant", null, null);
            when(aPersonneRepository.findByUid("dead")).thenReturn(ihm);

            assertThatThrownBy(() -> activationService.activate(
                    request("dead", true, null, "N3wPassw0rd!X", "N3wPassw0rd!X")))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(personneService, never()).valideCompte(anyString());
        }
    }

}