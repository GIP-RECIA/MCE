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

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.utils.LdapPassword;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Date;


@Service
@Slf4j
public class PasswordService {

    private static final int SALT_LENGTH = 8;

    @Autowired
    private IExternalUserDao externalUserDao;

    @Autowired
    private APersonneRepository aPersonneRepository;

    /**
     * Change le mot de passe d’un utilisateur.
     * <p>
     * Cette opération est transactionnelle avec propagation REQUIRES_NEW afin d’isoler le changement
     * de mot de passe des autres transactions en cours.
     * </p>
     *
     * @param person  utilisateur concerné
     * @param request  requête contenant ancien et nouveau mot de passe
     * @throws IllegalArgumentException si la requête est invalide ou si l’ancien mot de passe est incorrect
     * @throws RuntimeException en cas d’erreur technique lors de la mise à jour DB ou LDAP
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void changePassword(PersonneDTO person, PasswordChangeRequest request) {

        String uid = (person != null) ? person.getUid() : "UNKNOWN";
        log.info("Début changement mot de passe uid={}", uid);

        if (person == null) {
            throw new PersonneNotFoundException("Utilisateur introuvable");
        }

        validateRequest(person, request);

        String oldPassword = request.getOldPass();
        String newPassword = request.getNewPass();

        if (!isOldPasswordValid(person, oldPassword)) {
            log.warn("Ancien mot de passe incorrect uid={}", uid);
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }

        try {
            String hashedPassword = generateHashedPassword(newPassword);

            updatePasswordInDatabase(person, hashedPassword);
            updatePasswordInLdap(uid, hashedPassword);

        } catch (Exception e) {
            throw new RuntimeException("Erreur technique lors du changement de mot de passe", e);
        }
    }

    /**
     * Valide la requête de changement de mot de passe.
     *
     * @param person  utilisateur concerné
     * @param request requête contenant ancien et nouveau mot de passe
     * @throws IllegalArgumentException si les champs sont invalides
     * @throws IllegalStateException si l'état du mot de passe stocké est incohérent
     */
    private void validateRequest(PersonneDTO person, PasswordChangeRequest request) {
        if (person == null || request == null) {
            throw new IllegalArgumentException("Requête invalide");
        }

        String oldPassword = request.getOldPass();
        String newPassword = request.getNewPass();

        if (oldPassword == null || oldPassword.isBlank()) {
            throw new IllegalArgumentException("Ancien mot de passe requis");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Nouveau mot de passe requis");
        }

        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent");
        }

        if (!isPasswordStrongEnough(newPassword)) {
            throw new IllegalArgumentException("Mot de passe trop faible");
        }

        if (!hasValidStoredPassword(person.getAPersonneBase().getPassword())) {
            throw new IllegalStateException("Ancien mot de passe requis");
        }

        if (!isOldPasswordValid(person, oldPassword)) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }

        APersonne aPersonne = aPersonneRepository.findById(
                person.getAPersonneBase().getId()
        ).orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
    }

    /**
     * Génère un mot de passe hashé au format LDAP SSHA.
     *
     * @param newPassword mot de passe en clair
     * @return mot de passe encodé LDAP
     */
    private String generateHashedPassword(String newPassword) {
        byte[] saltBytes = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.encodeBase64String(saltBytes);

        LdapPassword ldapPassword = new LdapPassword(
                newPassword,
                salt,
                LdapPassword.Algo.SSHA,
                false
        );

        return ldapPassword.getCodageLdap();
    }

    /**
     * Met à jour le mot de passe en base de données.
     *
     * @param person         utilisateur
     * @param hashedPassword mot de passe hashé LDAP
     * @throws IllegalStateException si l’utilisateur n’est pas trouvé
     */
    private void updatePasswordInDatabase(PersonneDTO person, String hashedPassword) {

        APersonne apersonne = aPersonneRepository.findById(
                person.getAPersonneBase().getId()
        ).orElseThrow(() -> {
            log.error("Utilisateur introuvable en base uid={}", person.getUid());
            return new IllegalStateException("User not found in DB");
        });

        apersonne.setPassword(hashedPassword);
        apersonne.setDateModification(new Date());

        aPersonneRepository.saveAndFlush(apersonne);
    }

    /**
     * Met à jour le mot de passe dans LDAP.
     *
     * @param uid             identifiant utilisateur
     * @param hashedPassword  mot de passe hashé LDAP
     */
    private void updatePasswordInLdap(String uid, String hashedPassword) {
        externalUserDao.updatePassword(uid, hashedPassword);
    }

    /**
     * Vérifie si un mot de passe stocké est exploitable.
     *
     * @param oldPass mot de passe stocké
     * @return true si valide
     */
    public boolean hasValidStoredPassword(final String oldPass) {
        return oldPass != null
                && !oldPass.isBlank()
                && !oldPass.startsWith("{SSHA}Active=");
    }

    /**
     * Vérifie la robustesse d’un mot de passe.
     *
     * @param passPlainText mot de passe en clair
     * @return true si suffisamment robuste
     */
    public static boolean isPasswordStrongEnough(final String passPlainText) {
        if (passPlainText == null) return false;

        String pass = passPlainText.trim();
        int score = 17;

        if (pass.matches(".*\\W.*")) score = 15;
        if (pass.matches(".*\\p{Lower}.*")) score--;
        if (pass.matches(".*\\p{Upper}.*")) score--;
        if (pass.matches(".*\\d.*")) score--;

        return pass.length() >= score;
    }

    /**
     * Vérifie si l’ancien mot de passe est correct.
     *
     * @param user      utilisateur
     * @param passOld   mot de passe saisi
     * @return true si valide
     */
    private boolean isOldPasswordValid(final PersonneDTO user, String passOld) {

        if (passOld == null || passOld.isBlank()) {
            log.debug("Ancien mot de passe vide uid={}", user.getUid());
            return false;
        }

        boolean valid = verifyPassword(user, passOld, false);

        if (!valid) {
            log.debug("Ancien mot de passe invalide uid={}", user.getUid());
        }

        return valid;
    }

    /**
     * Vérifie si un mot de passe est en clair.
     *
     * @param pass mot de passe
     * @return true si en clair
     */
    private static boolean isPlainTextPassword(final String pass) {
        return pass != null && !pass.startsWith("{");
    }

    /**
     * Vérifie un mot de passe contre celui stocké (LDAP ou DB).
     *
     * @param personne        utilisateur
     * @param passClairATester mot de passe à tester
     * @param passClairOk      autorisation des mots de passe en clair
     * @return true si le mot de passe est valide
     */
    public boolean verifyPassword(final PersonneDTO personne,
                                  final String passClairATester,
                                  final boolean passClairOk) {

        LdapPassword ldapPassword = personne.getLdapPassword();
        String passwordFromDb;

        if (ldapPassword == null) {
            passwordFromDb = personne.getAPersonneBase().getPassword();

            if (passwordFromDb == null || passwordFromDb.isBlank()) {
                log.error("Mot de passe null pour user {}", personne.getIdentifiant());
                return false;
            }

            if (isPlainTextPassword(passwordFromDb)) {
                if (!passClairOk) {
                    log.error("Mot de passe en clair non autorisé user {}", personne.getIdentifiant());
                    return false;
                }
                return passwordFromDb.equals(passClairATester);
            }

            ldapPassword = new LdapPassword(passwordFromDb);
            personne.setLdapPassword(ldapPassword);
        }

        return ldapPassword.test(passClairATester);
    }
}