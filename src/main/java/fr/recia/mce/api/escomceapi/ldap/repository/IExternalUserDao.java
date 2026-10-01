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
package fr.recia.mce.api.escomceapi.ldap.repository;

import fr.recia.mce.api.escomceapi.ldap.IExternalUser;

import java.util.Collection;
import java.util.List;

public interface IExternalUserDao {

    IExternalUser getUserByUid(final String uid);

    List<IExternalUser> getByUids(final Collection<String> uids);

    void updatePassword(final String uid, final String newHashedPassword);

    /**
     * Mise à jour LDAP atomique du mot de passe et de l'état du compte (équivalent du legacy
     * {@code modifEtatLdapPassword}). Selon l'état fourni :
     * <ul>
     * <li>état du compte {@code Valide} → {@code userPassword} reçoit le hash réel {@code ldapHash} ;</li>
     * <li>état ≠ {@code Valide} → {@code userPassword} reçoit la sentinelle {@code {SCRIPT}LOCK} (verrouillage : aucun
     * bind LDAP ne passe, la valeur ne matche aucune regex de parsing) ;</li>
     * <li>l'attribut {@code etatCompte} ({@code ESCOPersonEtatCompte}) est toujours écrit, en majuscules ;</li>
     * <li>{@code sambaLMPassword} et {@code sambaNTPassword} sont écrits si les deux valeurs sont fournies.</li>
     * </ul>
     *
     * @param uid identifiant de l'utilisateur
     * @param ldapHash hash réel à écrire (seulement si l'état du compte est {@code Valide})
     * @param etatCompte état du compte en base (libellé {@link fr.recia.mce.api.escomceapi.services.AccountState})
     * @param sambaLm hash Samba LM, {@code null} = ne pas écrire
     * @param sambaNt hash Samba NT, {@code null} = ne pas écrire
     */
    void modifEtatLdapPassword(final String uid, final String ldapHash, final String etatCompte, final String sambaLm, final String sambaNt);

    void updateEmail(final String uid, final String newEmail);

    void updateAvatarLDAP(final String uid, final String newAvatarUrl);

    void updateEtatCompte(final String uid, final String etat);

    /**
     * Remplace l'attribut LDAP {@code ESCOPersonValidationCharteService} par la liste complète des validations de
     * charte de l'utilisateur (attribut multi-valué : une valeur par service, format
     * {@code <serviceId>;<versionDate>;<dateSignature>}, avec {@code versionDate} au format
     * {@code yyyyMMdd} et {@code dateSignature} au format UTC {@code yyyyMMddHHmmssZ}).
     * La liste complète est écrite en une fois (REPLACE) pour représenter fidèlement la base : une personne signant
     * la charte d'un service conserve les validations de ses autres services. Une liste vide retire l'attribut.
     *
     * @param uid    identifiant de l'utilisateur
     * @param values valeurs au format {@code serviceId;yyyyMMdd;yyyyMMddHHmmssZ}, une par service
     */
    void updateValidationsCharteService(final String uid, final List<String> values);
}
