/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
    private String userName;
    private String identifiant;
    private String etab;
    private String userMail;
    private Date bod;
    private String avatar;
    private String avatarUrl;
    private String etat;
    private Boolean mdp;
    private List<String> userPublic;
    private List<String> listMenu;
    private InfoGeneralDTO fonctionClassesGroupe;
    private List<RelationEleveContact> parentEleve;
    private List<RelationEleveContact> relationEleve;
    private List<RelationEleveContact> apprentis;

    public UserDTO(Long id, String uid, String userName, String identifiant, String etab, String userMail, Date bod,
                   String avatar, String etat, Boolean mdp, List<String> userPublic, List<String> listMenu,
                   InfoGeneralDTO fonctionClassesGroupe,
                   List<RelationEleveContact> parentEleve,
                   List<RelationEleveContact> relationEleve,
                   List<RelationEleveContact> apprentis) {

        this.id = id;
        this.uid = uid;
        this.userName = userName;
        this.identifiant = identifiant;
        this.etab = etab;
        this.userMail = userMail;
        this.bod = bod;
        this.avatar = avatar;
        this.etat = etat;
        this.mdp = mdp;
        this.userPublic = userPublic;
        this.listMenu = listMenu;
        this.fonctionClassesGroupe = fonctionClassesGroupe;
        this.parentEleve = parentEleve;
        this.relationEleve = relationEleve;
        this.apprentis = apprentis;

        log.debug("UserDTO construit - uid={} | etab={} | menu={}", uid, etab, listMenu);

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

    public UserDTO(Long id, String uid, String userName, String identifiant, String etab, String userMail, Date bod,
                   String avatar, String etat, List<String> listMenu) {
        this(id, uid, userName, identifiant, etab, userMail, bod, avatar, etat,
                null, null, listMenu, null, null, null, null);
    }

    @Override
    public String toString() {
        return "UserDTO{" +
                "id=" + id +
                ", uid='" + uid + '\'' +
                ", userName='" + userName + '\'' +
                ", identifiant='" + identifiant + '\'' +
                ", etab='" + etab + '\'' +
                ", userMail='" + userMail + '\'' +
                ", etat='" + etat + '\'' +
                ", mdp=" + mdp +
                ", parentEleveSize=" + (parentEleve != null ? parentEleve.size() : 0) +
                ", relationEleveSize=" + (relationEleve != null ? relationEleve.size() : 0) +
                ", apprentisSize=" + (apprentis != null ? apprentis.size() : 0) +
                '}';
    }
}