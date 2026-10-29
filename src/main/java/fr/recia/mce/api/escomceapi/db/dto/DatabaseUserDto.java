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
package fr.recia.mce.api.escomceapi.db.dto;

import java.util.Date;

import lombok.Data;

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.db.entities.CerbereEnfant;
import fr.recia.mce.api.escomceapi.db.entities.Login;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class DatabaseUserDto {

    private Long id;
    private String uid;
    private String displayName;
    private String givenName;
    private String sn;
    private String civilite;
    private String categorie;
    private String etat;
    private Date dateNaissance;
    private String emailPersonnel;
    private String email;
    private String photo;
    private String source;
    private String password;
    private String typeOfParent;
    private String lienParente;
    private String login;
    private String structureDisplayName;
    private boolean ntPass;

    public DatabaseUserDto(final APersonne aPersonne, final AStructure aStructure, final Login login, final CerbereEnfant cerbereEnfant) {
        this.id = aPersonne.getId();
        this.uid = aPersonne.getUid();
        this.displayName = aPersonne.getDisplayName();
        this.givenName = aPersonne.getGivenName();
        this.sn = aPersonne.getSn();
        this.civilite = aPersonne.getCivilite();
        this.categorie = aPersonne.getCategorie();
        this.etat = aPersonne.getEtat();
        this.dateNaissance = aPersonne.getDateNaissance();
        this.emailPersonnel = aPersonne.getEmailPersonnel();
        this.email = aPersonne.getEmail();
        this.photo = aPersonne.getPhoto();
        this.source = aPersonne.getSource();
        this.password = aPersonne.getPassword();
        this.lienParente = cerbereEnfant == null ? null : cerbereEnfant.getLienParente();
        this.typeOfParent = cerbereEnfant == null ? null : cerbereEnfant.getTypeRelation();
        this.login = login.getNom();
        this.structureDisplayName = aStructure.getNom().replace('$', ' ');
        // TODO
        this.ntPass = false;
    }

    public DatabaseUserDto(final APersonne aPersonne) {
        this(aPersonne, aPersonne.getAStructure());
    }

    public DatabaseUserDto(final APersonne aPersonne, final AStructure aStructure) {
    }

}
