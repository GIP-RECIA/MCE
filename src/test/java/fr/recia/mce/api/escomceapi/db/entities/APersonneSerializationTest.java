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
package fr.recia.mce.api.escomceapi.db.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APersonneSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("L'entité APersonne ne sérialise ni identifiants techniques ni secrets")
    void neSerialiseNiIdentifiantsNiSecrets() throws Exception {
        APersonne personne = new APersonne();
        personne.setId(2181544L);
        personne.setUid("F20102xc");
        personne.setPassword("{SSHA}IotkRaOAVNDIXfADtTKZvanBWQCDg+ItwVNhfg==");
        personne.setCle("FICTIF172");
        personne.setSambaLmpassword("lmSecret");
        personne.setSambaNtpassword("ntSecret");
        personne.setVersion(42L);

        String json = objectMapper.writeValueAsString(personne);

        assertThat(json).doesNotContain("\"id\"");
        assertThat(json).doesNotContain("\"uid\"");
        assertThat(json).doesNotContain("\"version\"");
        assertThat(json).doesNotContain("\"password\"");
        assertThat(json).doesNotContain("\"cle\"");
        assertThat(json).doesNotContain("sambaLmpassword");
        assertThat(json).doesNotContain("sambaNtpassword");
        assertThat(json).doesNotContain("SSHA");
        assertThat(json).doesNotContain("FICTIF172");
    }

    @Test
    @DisplayName("Les identifiants techniques restent lisibles en Java pour les usages internes")
    void lesGettersJavaRestentUtilisables() {
        APersonne personne = new APersonne();
        personne.setId(2181544L);
        personne.setUid("F20102xc");
        personne.setVersion(42L);
        personne.setPassword("{SSHA}hash");
        personne.setCle("FICTIF172");
        personne.setSambaLmpassword("lmSecret");
        personne.setSambaNtpassword("ntSecret");

        assertThat(personne.getId()).isEqualTo(2181544L);
        assertThat(personne.getUid()).isEqualTo("F20102xc");
        assertThat(personne.getVersion()).isEqualTo(42L);
        assertThat(personne.getPassword()).isEqualTo("{SSHA}hash");
        assertThat(personne.getCle()).isEqualTo("FICTIF172");
        assertThat(personne.getSambaLmpassword()).isEqualTo("lmSecret");
        assertThat(personne.getSambaNtpassword()).isEqualTo("ntSecret");
    }

    @Test
    @DisplayName("Les champs métier restent sérialisés pour ne pas casser le contrat JSON")
    void lesChampsMetierRestentSerialises() throws Exception {
        APersonne personne = new APersonne();
        personne.setUid("F20102xc");
        personne.setDisplayName("Sara VAR");
        personne.setGivenName("Sara");
        personne.setSn("VAR");
        personne.setCivilite("Mme");
        personne.setEtat("Valide");
        personne.setSource("COLL-CD36");
        personne.setCategorie("Eleve");
        personne.setEmail("sara.var@chercan.fr");

        String json = objectMapper.writeValueAsString(personne);

        assertThat(json).contains("\"displayName\":\"Sara VAR\"");
        assertThat(json).contains("\"givenName\":\"Sara\"");
        assertThat(json).contains("\"sn\":\"VAR\"");
        assertThat(json).contains("\"civilite\":\"Mme\"");
        assertThat(json).contains("\"etat\":\"Valide\"");
        assertThat(json).contains("\"source\":\"COLL-CD36\"");
        assertThat(json).contains("\"categorie\":\"Eleve\"");
        assertThat(json).contains("\"email\":\"sara.var@chercan.fr\"");
    }
}
