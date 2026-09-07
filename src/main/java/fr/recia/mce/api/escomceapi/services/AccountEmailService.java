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
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
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

    @Autowired
    private PersonneService personneService;

    public List<String> collectAccountEmails(APersonne person) {
        Set<String> emails = new LinkedHashSet<>();
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            emails.add(person.getEmail().trim().toLowerCase());
        }
        if (person.getEmailPersonnel() != null && !person.getEmailPersonnel().isBlank()) {
            emails.add(person.getEmailPersonnel().trim().toLowerCase());
        }
        for (CerbereConfirmation c : cerbereConfirmationRepository.findConfirmedByPersonId(person.getId())) {
            if (c.getMail() != null && !c.getMail().isBlank()) {
                emails.add(c.getMail().trim().toLowerCase());
            }
        }
        String ldapMail = ldapEmail(person);
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
    public boolean isEmailAssociatedWithAccount(APersonne person, String providedEmail) {
        if (providedEmail == null || providedEmail.isBlank()) {
            return false;
        }
        String candidate = providedEmail.trim();
        return collectAccountEmails(person).stream()
                .anyMatch(email -> sameEmail(candidate, email));
    }

    boolean sameEmail(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b.trim());
    }

    /**
     * Récupère l'email principal de l'utilisateur depuis l'annuaire LDAP. Retourne {@code null} si la personne est absente de l'annuaire ou sans email.
     */
    public String ldapEmail(APersonne person) {
        return ldapEmailByUid(person.getUid());
    }

    public String ldapEmailByUid(String uid) {
        try {
            IExternalUser ldapUser = personneService.retrievePersonLdap(uid);
            if (ldapUser != null && ldapUser.getEmail() != null && !ldapUser.getEmail().isBlank()) {
                return ldapUser.getEmail().trim();
            }
        } catch (Exception e) {
            log.warn("[LDAP_EMAIL] Impossible de récupérer l'email LDAP pour uid={} : {}", uid, e.getMessage());
        }
        return null;
    }

}