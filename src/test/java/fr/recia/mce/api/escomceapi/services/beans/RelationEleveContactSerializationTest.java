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
package fr.recia.mce.api.escomceapi.services.beans;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelationEleveContactSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private APersonne enfant() {
        AStructure structure = new AStructure();
        structure.setNom("CLG FICTIF CLG 18 BOURGES");
        APersonne a = new APersonne();
        a.setId(2181544L);
        a.setUid("F20102xc");
        a.setDisplayName("Sara VAR");
        a.setGivenName("Sara");
        a.setSn("VAR");
        a.setEtat("Valide");
        a.setAStructure(structure);
        return a;
    }

    @Test
    @DisplayName("L'enfant imbriqué dans parentEleve n'expose plus uid ni identifiant")
    void eleveImbriqueMasqueUidEtIdentifiant() throws Exception {
        RelationEleveContact rel = new RelationEleveContact(
                "CONTACT2ELEVE", null, enfant(), "Autorite_parentale", "Pere", true);

        String json = objectMapper.writeValueAsString(rel);
        JsonNode eleve = objectMapper.readTree(json).get("eleve");

        assertThat(eleve).isNotNull();
        assertThat(eleve.has("uid")).isFalse();
        assertThat(eleve.has("identifiant")).isFalse();
        assertThat(eleve.has("id")).isFalse();
    }

    @Test
    @DisplayName("Les autres champs de l'enfant imbriqué restent disponibles")
    void eleveImbriqueConserveSesChampsMetier() throws Exception {
        RelationEleveContact rel = new RelationEleveContact(
                "CONTACT2ELEVE", null, enfant(), "Autorite_parentale", "Pere", true);

        JsonNode eleve = objectMapper.readTree(objectMapper.writeValueAsString(rel)).get("eleve");

        assertThat(eleve.get("displayName").asText()).isEqualTo("Sara VAR");
        assertThat(eleve.get("prenom").asText()).isEqualTo("Sara");
        assertThat(eleve.get("source").asText()).isNotNull();
        assertThat(eleve.has("structureDto")).isTrue();
    }

    @Test
    @DisplayName("uidRelation et contact du parent ne sont pas affectés")
    void champsDuParentInchanges() throws Exception {
        APersonne parent = new APersonne();
        parent.setId(2181364L);
        parent.setUid("F209572x");
        parent.setDisplayName("Pierre VAR");

        RelationEleveContact rel = new RelationEleveContact(
                "CONTACT2ELEVE", parent, enfant(), "Autorite_parentale", "Pere", true);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(rel));

        assertThat(root.get("uidRelation").asText()).isEqualTo("F209572x");
        assertThat(root.get("displayNameRelation").asText()).isEqualTo("Pierre VAR");
        assertThat(root.get("contact").asLong()).isEqualTo(2181364L);
    }

    @Test
    @DisplayName("Une PersonneDTO en réponse racine conserve uid : le contrat /getuser est intact")
    void personneDtoRacineConserveUid() throws Exception {
        PersonneDTO dto = new PersonneDTO(enfant());

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertThat(root.get("uid").asText()).isEqualTo("F20102xc");
        assertThat(root.get("identifiant").asText()).isNotNull();
    }

    @Test
    @DisplayName("L'imbrication ne contamine pas les autres occurrences de PersonneDTO dans la même réponse")
    void imbricationIsolee() throws Exception {
        PersonneDTO racine = new PersonneDTO(enfant());
        RelationEleveContact rel = new RelationEleveContact(
                "CONTACT2ELEVE", null, enfant(), "Autorite_parentale", "Pere", true);

        String json = "{\"personne\":" + objectMapper.writeValueAsString(racine)
                + ",\"parentEleve\":[" + objectMapper.writeValueAsString(rel) + "]}";
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("personne").get("uid").asText()).isEqualTo("F20102xc");
        assertThat(root.get("parentEleve").get(0).get("eleve").has("uid")).isFalse();
    }

    @Test
    @DisplayName("Une liste de relations reste serialisable sans erreur")
    void listeDeRelationsSerialisable() throws Exception {
        List<RelationEleveContact> relations = List.of(
                new RelationEleveContact("CONTACT2ELEVE", null, enfant(), "Autorite_parentale", "Pere", true),
                new RelationEleveContact("CONTACT2ELEVE", null, enfant(), "Autorite_parentale", "Mere", false));

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(relations));

        assertThat(root).hasSize(2);
        assertThat(root.get(0).get("eleve").has("uid")).isFalse();
        assertThat(root.get(1).get("eleve").has("uid")).isFalse();
    }
}
