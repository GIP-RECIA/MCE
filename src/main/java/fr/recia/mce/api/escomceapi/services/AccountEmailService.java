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
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collecte et vérification des adresses email associées à un compte : email principal, email personnel,
 * emails confirmés via Cerbère et email issu de l'annuaire LDAP.
 */
@Service
@Slf4j
public class AccountEmailService {

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    public List<String> collectAccountEmails(UserDto userDto) {
        Set<String> emails = new LinkedHashSet<>();
        if (userDto.getDatabaseUserDto().getEmail() != null && !userDto.getDatabaseUserDto().getEmail().isBlank()) {
            emails.add(userDto.getDatabaseUserDto().getEmail().trim().toLowerCase());
        }
        if (userDto.getDatabaseUserDto().getEmailPersonnel() != null && !userDto.getDatabaseUserDto().getEmailPersonnel().isBlank()) {
            emails.add(userDto.getDatabaseUserDto().getEmailPersonnel().trim().toLowerCase());
        }
        for (CerbereConfirmation c : cerbereConfirmationRepository.findConfirmedByPersonId(userDto.getDatabaseUserDto().getId())) {
            if (c.getMail() != null && !c.getMail().isBlank()) {
                emails.add(c.getMail().trim().toLowerCase());
            }
        }
        String ldapMail = ldapEmail(userDto);
        if (ldapMail != null) {
            emails.add(ldapMail.trim().toLowerCase());
        }
        return new ArrayList<>(emails);
    }

    /**
     * L'email fourni doit correspondre (insensible à la casse) à l'email du compte, à l'email personnel, à un email confirmé via Cerbère, ou à l'email LDAP.
     *
     * @return true si l'email fourni est associé au compte (via une source connue)
     */
    public boolean isEmailAssociatedWithAccount(UserDto userDto, String providedEmail) {
        if (providedEmail == null || providedEmail.isBlank()) {
            return false;
        }
        String candidate = providedEmail.trim();
        return collectAccountEmails(userDto).stream().anyMatch(email -> sameEmail(candidate, email));
    }

    public boolean sameEmail(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b.trim());
    }

    /**
     * Récupère l'email principal de l'utilisateur depuis l'annuaire LDAP. Retourne {@code null} si la personne est absente de l'annuaire ou sans email.
     */
    public String ldapEmail(UserDto userDto) {
        try {
            if (userDto.getLdapUserDto().getEmail() != null && !userDto.getLdapUserDto().getEmail().isBlank()) {
                return userDto.getLdapUserDto().getEmail().trim();
            }
        } catch (Exception e) {
            log.warn("[LDAP_EMAIL] Impossible de récupérer l'email LDAP pour uid={} : {}", userDto.getLdapUserDto().getId(), e.getMessage());
        }
        return null;
    }

}
