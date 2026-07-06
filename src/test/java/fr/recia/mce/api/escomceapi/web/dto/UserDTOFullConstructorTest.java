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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact.SensRel;

class UserDTOFullConstructorTest {

    private RelationEleveContact relation(String uid, String name, String type) {
        RelationEleveContact r = new RelationEleveContact(SensRel.ELEVE2CONTACT);
        r.setUidRelation(uid);
        r.setDisplayNameRelation(name);
        r.setTypeRelation(type);
        return r;
    }

    @Test
    void fullConstructorWithPopulatedRelationLists() {
        List<String> userPublic = List.of("PUBLIC");
        List<String> listMenu = List.of("MENU1", "MENU2");
        InfoGeneralDTO info = new InfoGeneralDTO();
        List<RelationEleveContact> parentEleve = List.of(relation("p1", "Parent One", "PERE"));
        List<RelationEleveContact> relationEleve = List.of(relation("e1", "Eleve One", "ENFANT"));
        List<RelationEleveContact> apprentis = List.of(relation("a1", "Apprenti One", "APPRENTI"));
        Date bod = new Date();

        UserDTO dto = new UserDTO(1L, "uid", "userName", "given", "sn", "M.", "ELEVE", true,
                "ident", "0450001A", "mail@x.fr", "perso@x.fr", bod,
                "avatar", "etat", false, null, userPublic, listMenu, info,
                parentEleve, relationEleve, apprentis);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUid()).isEqualTo("uid");
        assertThat(dto.getUserName()).isEqualTo("userName");
        assertThat(dto.getGivenName()).isEqualTo("given");
        assertThat(dto.getSn()).isEqualTo("sn");
        assertThat(dto.getCivilite()).isEqualTo("M.");
        assertThat(dto.getCategorie()).isEqualTo("ELEVE");
        assertThat(dto.getCanEditEmail()).isTrue();
        assertThat(dto.getIdentifiant()).isEqualTo("ident");
        assertThat(dto.getEtab()).isEqualTo("0450001A");
        assertThat(dto.getEmail()).isEqualTo("mail@x.fr");
        assertThat(dto.getEmailPersonnel()).isEqualTo("perso@x.fr");
        assertThat(dto.getBod()).isEqualTo(bod);
        assertThat(dto.getAvatar()).isEqualTo("avatar");
        assertThat(dto.getEtat()).isEqualTo("etat");
        assertThat(dto.getMdp()).isFalse();
        assertThat(dto.getUserPublic()).containsExactly("PUBLIC");
        assertThat(dto.getListMenu()).containsExactly("MENU1", "MENU2");
        assertThat(dto.getFonctionClassesGroupe()).isSameAs(info);
        assertThat(dto.getParentEleve()).hasSize(1);
        assertThat(dto.getRelationEleve()).hasSize(1);
        assertThat(dto.getApprentis()).hasSize(1);
        assertThat(dto.toString()).contains("UserDTO").contains("parentEleveSize=1");
    }

    @Test
    void fullConstructorWithNullRelationLists() {
        UserDTO dto = new UserDTO(2L, "uid2", "user2", "given2", "sn2", "Mme", "ENS", false,
                "ident2", "etab2", "mail2@x.fr", "perso2@x.fr", new Date(),
                "avatar2", "etat2", true, null, null, null, null,
                null, null, null);

        assertThat(dto.getId()).isEqualTo(2L);
        assertThat(dto.getParentEleve()).isNull();
        assertThat(dto.getRelationEleve()).isNull();
        assertThat(dto.getApprentis()).isNull();
        assertThat(dto.toString()).contains("parentEleveSize=0");
    }
}
