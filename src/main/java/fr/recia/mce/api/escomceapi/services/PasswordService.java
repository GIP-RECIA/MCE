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

import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.utils.LdapPassword;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PasswordService {

    public static final String PREFIXCODE = "{SSHA}";

    public String changePasswordLogic(PersonneDTO person, PasswordChangeRequest request) {

        // Verify if oldPass is exists
        if (!oldPasswordExist(person.getAPersonneBase().getPassword())) {
            return "newPass error, must enter the old password.";
        }

        // Verify old password
        if (!testOldPass(person, request.getOldPass())) {
            return "OldPass invalide.";
        }

        // Validate new password rules
        if (!isAcceptable(request.getNewPass())) {
            return "New password does not meet security requirements.";

        }

        // Confirm new password matches
        if (!request.getConfirmPass().equals(request.getNewPass())) {
            return "confirm pass is not correct";

        }

        // Update password
        // TO DO : save to database and ldap
        return "Password changed successfully.";

    }

    public boolean oldPasswordExist(final String oldPass) {
        if (oldPass.isBlank() || oldPass.startsWith("{SSHA}Active=")) {
            return false;
        }
        return true;
    }

    public static boolean isAcceptable(final String passPlainText) {
        if (passPlainText == null) {
            return false;
        }
        /*
         * pass doit faire au moins 12 caractères si tout type de caractère.
         * 14 caractères si que des Maj, Min et Chiffre,
         */
        String pass = passPlainText.trim();
        int score = 17;
        if (pass.matches(".*\\W.*")) {
            score = 15;
        }

        if (pass.matches(".*\\p{Lower}.*")) {
            score--;
        }
        if (pass.matches(".*\\p{Upper}.*")) {
            score--;
        }
        if (pass.matches(".*\\d.*")) {
            score--;
        }
        return pass.length() >= score;
    }

    private boolean testOldPass(final PersonneDTO user, String passOld) {
        boolean res = false;

        if (passOld.isBlank()) {
            log.debug("erreur de passOld = null");

        } else {
            if (testPassword(user, passOld, false)) {
                res = true;

            } else {
                log.debug("erreur de passOld invalide " + passOld);
            }
        }

        return res;
    }

    private static boolean isPassClair(final String pass) {
        return !pass.startsWith("{");
    }

    public boolean testPassword(final PersonneDTO personne, final String passClairATester, final boolean passClairOk) {
        LdapPassword lp = personne.getLdapPassword();
        String passFromDao;
        if (lp == null) {
            passFromDao = personne.getAPersonneBase().getPassword();
            if (passFromDao.isBlank()) {
                log.error("pass null for user {}", personne.getIdentifiant(), "[360]");
                return false;
            }
            if (isPassClair(passFromDao)) {
                if (!passClairOk) {
                    log.error("pass invalide for user {}", personne.getIdentifiant(), "[365]");
                    return false;
                }
                return passFromDao.equals(passClairATester);
            }
            lp = new LdapPassword(passFromDao);
            personne.setLdapPassword(lp);
        }
        return lp.test(passClairATester);
    }

}
