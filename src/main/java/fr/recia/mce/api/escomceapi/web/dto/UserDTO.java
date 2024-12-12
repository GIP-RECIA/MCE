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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
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
    private String etat;
    private List<String> listMenu;
    private InfoGeneralDTO fonctionClassesGroupe;
    private List<RelationEleveContact> parentEleve;
    private List<RelationEleveContact> relationEleve;
    private List<RelationEleveContact> apprentis;

    public UserDTO(Long id, String uid, String userName, String identifiant, String etab, String userMail, Date bod,
            String avatar, String etat, List<String> listMenu) {
        this.id = id;
        this.uid = uid;
        this.userName = userName;
        this.identifiant = identifiant;
        this.etab = etab;
        this.userMail = userMail;
        this.bod = bod;
        this.avatar = avatar;
        this.etat = etat;
        this.listMenu = listMenu;
    }

    @Override
    public String toString() {
        return "UserDTO [id=" + id + ", uid=" + uid + ", userName=" + userName + ", identifiant=" + identifiant
                + ", etab=" + etab + ", userMail=" + userMail + ", bod=" + bod + ", avatar=" + avatar + ", etat=" + etat
                + ", listMenu=" + listMenu + "]";
    }

}
