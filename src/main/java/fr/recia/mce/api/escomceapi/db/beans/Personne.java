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
package fr.recia.mce.api.escomceapi.db.beans;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Personne {

    private String displayName;
    private String identifiant;
    private Date dateValidCharte;
    private String uid;
    private String mailFixe;
    private String mail;
    private boolean mailForward;
    // private LdapPassword ldapPassword;
    private boolean charteValide;
    // private EnumEtat etat;
    // private Structure structure;
    private String domaineDeConnexion;
    // private EnumPublic enumPublic;
    // private InternetAddress internetAddress;
    private String codeConfirmation;
    // private EnumTypeConfirmation typeCode;

    private boolean isCfa;

    private boolean mailFixeConfirm;
    private boolean mailPersoConfirm;
    private String mailConfirme;
    private boolean mailFixeConfiance;

    private String nom;
    private String prenom;
    private String aliasLogin;
    private Date naissance;

    private String avatarUrl;

    // private List<ReponseSecrete> reponseSecreteList;

    public String typeOfParent() {
        return null;
    }

    public String lienParente() {
        return null;
    }
}
