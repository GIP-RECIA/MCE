package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
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

    public static final String PREFIXCODE = "{SSHA}";
    private static final int SALT_LENGTH = 8;

    @Autowired
    private IExternalUserDao externalUserDao;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String changePassword(PersonneDTO person, PasswordChangeRequest request) {

        String uid = (person != null) ? person.getUid() : "UNKNOWN";
        log.info("Début changement mot de passe uid={}", uid);

        // 1. Validation globale
        String validationError = validateRequest(person, request);
        if (validationError != null) {
            return validationError;
        }

        String oldPassword = request.getOldPass();
        String newPassword = request.getNewPass();

        // 2. Vérification ancien mot de passe
        if (!isOldPasswordValid(person, oldPassword)) {
            log.warn("Ancien mot de passe incorrect uid={}", uid);
            return "Ancien mot de passe incorrect.";
        }

        try {
            // 3. Génération du hash
            String hashedPassword = generateHashedPassword(newPassword);

            // 4. Mise à jour
            updatePasswordInDatabase(person, hashedPassword);
            updatePasswordInLdap(uid, hashedPassword);

            log.info("Mot de passe changé avec succès uid={}", uid);
            return "fin correct";

        } catch (Exception e) {
            log.error("Erreur changement mot de passe uid={} : {}", uid, e.getMessage(), e);
            return "An error occurred while saving the new password.";
        }
    }


    private String validateRequest(PersonneDTO person, PasswordChangeRequest request) {

        if (person == null || request == null) {
            log.warn("Requête invalide (person ou request null)");
            return "Requête invalide.";
        }

        String uid = person.getUid();
        String oldPassword = request.getOldPass();
        String newPassword = request.getNewPass();

        if (oldPassword == null || oldPassword.isBlank()) {
            log.warn("Ancien mot de passe vide uid={}", uid);
            return "Ancien mot de passe requis.";
        }

        if (newPassword == null || newPassword.isBlank()) {
            log.warn("Nouveau mot de passe vide uid={}", uid);
            return "Nouveau mot de passe requis.";
        }

        if (oldPassword.equals(newPassword)) {
            log.warn("Nouveau mot de passe identique uid={}", uid);
            return "Le nouveau mot de passe doit être différent.";
        }

        if (!isPasswordStrongEnough(newPassword)) {
            log.warn("Mot de passe trop faible uid={}", uid);
            return "Mot de passe trop faible.";
        }

        if (!hasValidStoredPassword(person.getAPersonneBase().getPassword())) {
            log.warn("Pas de mot de passe existant uid={}", uid);
            return "Ancien mot de passe requis.";
        }

        return null;
    }


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


    private void updatePasswordInDatabase(PersonneDTO person, String hashedPassword) {

        APersonne apersonne = aPersonneRepository.findById(
                person.getAPersonneBase().getId()
        ).orElseThrow(() -> {
            log.error("Utilisateur introuvable en base uid={}", person.getUid());
            return new RuntimeException("User not found in DB");
        });

        apersonne.setPassword(hashedPassword);
        apersonne.setDateModification(new Date());

        aPersonneRepository.saveAndFlush(apersonne);
    }

    private void updatePasswordInLdap(String uid, String hashedPassword) {
        externalUserDao.updatePassword(uid, hashedPassword);
    }


    public boolean hasValidStoredPassword(final String oldPass) {
        return oldPass != null
                && !oldPass.isBlank()
                && !oldPass.startsWith("{SSHA}Active=");
    }

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

    private static boolean isPlainTextPassword(final String pass) {
        return pass != null && !pass.startsWith("{");
    }

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