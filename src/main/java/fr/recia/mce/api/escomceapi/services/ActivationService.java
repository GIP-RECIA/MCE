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
import fr.recia.mce.api.escomceapi.services.exception.InactiveAccountException;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.ActivationRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationResultDTO;
import fr.recia.mce.api.escomceapi.web.dto.ActivationStatusResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Slf4j
public class ActivationService {

    private static final String STEP_CHARTE = "CHARTE";
    private static final String STEP_COURRIEL = "COURRIEL";
    private static final String STEP_PASSWORD = "PASSWORD";
    private static final String STEP_FIN = "FIN";

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CharteService charteService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private IUserDTOFactory userDTOFactory;

    /**
     * Point d'entrée CONNEXION du parcours d'activation : identifiants de connexion + mot de passe temporaire.
     *
     * <p>
     * Le mot de passe est vérifié localement contre le hash stocké en base ({@link PasswordService#verifyPassword}), jamais par un bind LDAP, conformément
     * au comportement historique de Cerbère. L'accès est limité aux comptes à l'état {@code Invalide} dont le profil se connecte par mot de passe local
     * ({@link EnumPublic#isConnectOk()}).
     * </p>
     *
     * @param login
     *            identifiant de connexion (uid, login ou alias)
     * @param password
     *            mot de passe temporaire
     * @return l'uid du compte à activer
     * @throws IllegalArgumentException
     *             si les identifiants sont incorrects ou le compte non activable
     */
    public String connexion(String login, String password) {
        if (StringUtils.isBlank(login) || StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("Le login et le mot de passe sont obligatoires");
        }
        login = login.trim();

        APersonne personne = aPersonneRepository.findByLogin(login);
        if (personne == null || !AccountState.INVALIDE.equals(personne.getEtat())) {
            // Réponse volontairement vague pour ne rien divulguer sur l'existence du compte.
            log.warn("[ACTIVATION][CONNEXION] ÉCHEC pour login={} : compte absent ou état non activable", login);
            throw new IllegalArgumentException("Identifiants incorrects");
        }

        if (!passwordService.verifyPassword(new PersonneDTO(personne), password, true)) {
            log.warn("[ACTIVATION][CONNEXION] ÉCHEC pour uid={} : mot de passe temporaire invalide", personne.getUid());
            throw new IllegalArgumentException("Identifiants incorrects");
        }

        EnumPublic pub = userDTOFactory.evalPublic(new PersonneDTO(personne));
        if (pub == null || !pub.isConnectOk()) {
            log.warn("[ACTIVATION][CONNEXION] REFUS pour uid={} : profil {} non connectable par mot de passe", personne.getUid(), pub);
            throw new IllegalArgumentException("Ce compte ne peut pas être activé avec un mot de passe");
        }

        log.info("[ACTIVATION][CONNEXION] SUCCÈS uid={}", personne.getUid());
        return personne.getUid();
    }

    /**
     * Détermine le parcours d'activation applicable au compte, par transposition des règles historiques de Cerbère.
     */
    @Transactional(readOnly = true)
    public ActivationStatusResponseDTO getActivationStatus(String uid) {
        if (StringUtils.isBlank(uid)) {
            throw new IllegalArgumentException("L'identifiant est obligatoire");
        }
        APersonne personne = aPersonneRepository.findByUid(uid.trim());
        if (personne == null) {
            throw new PersonneNotFoundException("Utilisateur introuvable : " + uid);
        }

        EnumPublic pub = userDTOFactory.evalPublic(new PersonneDTO(personne));

        boolean charteValide = !charteService.isCharteRequired(personne);
        boolean passwordRequise = pub != null && pub.isConnectOk();
        // Les profils sans mot de passe local (AGRI, CVDL, PARENT_EDUC, EDUCATION, ELEVE_EDUC) n'ont pas d'étape COURRIEL.
        boolean emailRequise = passwordRequise && StringUtils.isBlank(personne.getEmail());

        String etape;
        if (!charteValide) {
            etape = STEP_CHARTE;
        } else if (!passwordRequise) {
            etape = STEP_FIN;
        } else if (pub.isEleve() || emailRequise) {
            // Élèves/apprentis : l'étape COURRIEL précède toujours la création du mot de passe dans le parcours historique.
            etape = STEP_COURRIEL;
        } else {
            etape = STEP_PASSWORD;
        }

        log.info("[ACTIVATION][STATUS] uid={}, profil={}, etat={}, charteValide={}, passwordRequise={}, emailRequise={}, etape={}",
                uid, pub, personne.getEtat(), charteValide, passwordRequise, emailRequise, etape);

        return ActivationStatusResponseDTO.builder()
                .uid(personne.getUid())
                .etat(personne.getEtat())
                .charteRequise(!charteValide)
                .charteSignee(charteValide)
                .emailRequise(emailRequise)
                .passwordRequise(passwordRequise)
                .etapeSuivante(etape)
                .build();
    }

    /**
     * Point d'entrée PASSWORD du parcours d'activation : signature de la charte, création du mot de passe (si le profil se connecte par mot de passe local),
     * envoi éventuel d'un code de vérification d'email saisi, puis passage du compte à l'état {@code Valide}.
     */
    @Transactional
    public ActivationResultDTO activate(ActivationRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getUid())) {
            throw new IllegalArgumentException("L'identifiant est obligatoire");
        }
        String uid = request.getUid().trim();

        APersonne personne = aPersonneRepository.findByUid(uid);
        if (personne == null) {
            throw new PersonneNotFoundException("Utilisateur introuvable : " + uid);
        }
        if (AccountState.DELETE.equals(personne.getEtat())) {
            throw new IllegalArgumentException("Ce compte a été supprimé et ne peut pas être activé : " + uid);
        }

        PersonneDTO personneDTO = personneService.getUserByUid(uid);
        if (personneDTO == null) {
            throw new InactiveAccountException("Impossible de charger votre profil. Réessayez plus tard.");
        }

        if (charteService.isCharteRequired(personne)) {
            if (!request.isCharteAccepted()) {
                throw new CharteNotAcceptedException("Vous devez accepter les conditions générales d'utilisation avant de poursuivre l'activation");
            }
            personneService.signCharte(uid);
            // signCharte pose toujours une date courante : on la reflète sur le DTO déjà chargé
            // pour éviter un 2ᵉ chargement complet (DB + LDAP) après l'éviction du cache.
            personneDTO.setDateValideCharte(new Date());
        }

        EnumPublic pub = userDTOFactory.evalPublic(personneDTO);
        boolean passwordRequise = pub != null && pub.isConnectOk();
        if (passwordRequise) {
            if (StringUtils.isBlank(request.getNewPassword())) {
                throw new IllegalArgumentException("Le nouveau mot de passe est obligatoire pour ce compte");
            }
            passwordService.resetPassword(personneDTO, request.getNewPassword(), request.getConfirmPassword());
            personneService.clearUserCaches(uid);
        }

        boolean emailEnAttente = false;
        if (StringUtils.isNotBlank(request.getEmail())) {
            String email = request.getEmail().trim();
            personneService.validateEmailForUpdate(uid, email);
            emailVerificationService.sendVerificationEmail(uid, email);
            emailEnAttente = true;
        }

        personneService.valideCompte(uid);

        log.info("[ACTIVATION][PASSWORD] SUCCÈS uid={}, profil={}, password={}, emailRéconfirmé={}", uid, pub, passwordRequise, emailEnAttente);
        return new ActivationResultDTO(uid, AccountState.VALIDE, emailEnAttente);
    }

}