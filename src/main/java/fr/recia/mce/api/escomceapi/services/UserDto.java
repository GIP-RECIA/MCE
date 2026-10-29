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

import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserDto {

    // Utilisateur chargé depuis la base de données
    private DatabaseUserDto databaseUserDto;
    // Utilisateur chargé depuis le LDAP
    private LdapUserDto ldapUserDto;
    // UID en direct pour pouvoir y accéder plus facilement
    private String uid;
    // Attributs calculés au moment de la création de l'utilisateur
    private EnumPublic enumPublic;
    private List<RelationEleveContact> apprentisList;
    private List<RelationEleveContact> respEleves;
    private List<RelationEleveContact> eleves;

}
