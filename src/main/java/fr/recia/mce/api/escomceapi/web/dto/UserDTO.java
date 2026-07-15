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
package fr.recia.mce.api.escomceapi.web.dto;

import java.util.Date;
import java.util.List;

import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class UserDTO {

    private Long id;
    private String uid;
    private String givenName;
    private String sn;
    private String civilite;
    private String categorie;
    private Boolean canEditEmail;
    private String email;
    private String emailPersonnel;
    private String userName;
    private String identifiant;
    private String etab;
    private Date bod;
    private String avatar;
    private String etat;
    private Boolean mdp;
    private List<String> userPublic;
    private InfoGeneralDTO fonctionClassesGroupe;
    private List<RelationEleveContact> parentEleve;
    private List<RelationEleveContact> relationEleve;
    private List<RelationEleveContact> apprentis;

    public UserDTO(Long id, String uid, String userName, String givenName, String sn, String civilite, String categorie, Boolean canEditEmail,
            String identifiant, String etab, String email, String emailPersonnel, Date bod,
            String avatar, String etat, Boolean mdp, List<String> userPublic,
            InfoGeneralDTO fonctionClassesGroupe,
            List<RelationEleveContact> parentEleve,
            List<RelationEleveContact> relationEleve,
            List<RelationEleveContact> apprentis) {

        this.id = id;
        this.uid = uid;
        this.userName = userName;
        this.givenName = givenName;
        this.sn = sn;
        this.civilite = civilite;
        this.categorie = categorie;
        this.canEditEmail = canEditEmail;
        this.email = email;
        this.emailPersonnel = emailPersonnel;
        this.identifiant = identifiant;
        this.etab = etab;
        this.bod = bod;
        this.avatar = avatar;
        this.etat = etat;
        this.mdp = mdp;
        this.userPublic = userPublic;
        this.fonctionClassesGroupe = fonctionClassesGroupe;
        this.parentEleve = parentEleve;
        this.relationEleve = relationEleve;
        this.apprentis = apprentis;

        log.debug("UserDTO construit - uid={} | etab={}", uid, etab);

        log.debug("parentEleve (ELEVE → CONTACT) : {} élément(s)",
                parentEleve != null ? parentEleve.size() : 0);
        if (parentEleve != null) {
            for (RelationEleveContact r : parentEleve) {
                log.debug("   → parentEleve : uid={} | nom={} | type={}",
                        r.getUidRelation(),
                        r.getDisplayNameRelation(),
                        r.getTypeRelation());
            }
        }

        log.debug("relationEleve (CONTACT → ELEVE) : {} élément(s)",
                relationEleve != null ? relationEleve.size() : 0);
        if (relationEleve != null) {
            for (RelationEleveContact r : relationEleve) {
                log.debug("   → relationEleve : uid={} | nom={} | type={}",
                        r.getUidRelation(),
                        r.getDisplayNameRelation(),
                        r.getTypeRelation());
            }
        }

        log.debug("apprentis : {} élément(s)",
                apprentis != null ? apprentis.size() : 0);
        if (apprentis != null) {
            for (RelationEleveContact r : apprentis) {
                log.debug("   → apprenti : uid={} | nom={} | type={}",
                        r.getUidRelation(),
                        r.getDisplayNameRelation(),
                        r.getTypeRelation());
            }
        }
    }

    public UserDTO(Long id, String uid, String userName, String identifiant, String etab, String email, String emailPersonnel, Date bod,
            String avatar, String etat) {
        this(id, uid, userName, null, null, null, null, null, identifiant, etab, email, emailPersonnel, bod, avatar, etat,
                null, null, null, null, null, null);
    }

    @Override
    public String toString() {
        return "UserDTO{" +
                "id=" + id +
                ", uid='" + uid + '\'' +
                ", userName='" + userName + '\'' +
                ", identifiant='" + identifiant + '\'' +
                ", etab='" + etab + '\'' +
                ", email='" + email + '\'' +
                ", etat='" + etat + '\'' +
                ", mdp=" + mdp +
                ", parentEleveSize=" + (parentEleve != null ? parentEleve.size() : 0) +
                ", relationEleveSize=" + (relationEleve != null ? relationEleve.size() : 0) +
                ", apprentisSize=" + (apprentis != null ? apprentis.size() : 0) +
                '}';
    }
}
