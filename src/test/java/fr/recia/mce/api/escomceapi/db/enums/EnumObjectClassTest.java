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
package fr.recia.mce.api.escomceapi.db.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests - EnumObjectClass (détection objectClass LDAP)")
class EnumObjectClassTest {

    @Test
    @DisplayName("containsMaitre avec ENTAuxTuteurStage → true")
    void containsTuteurStage() {
        List<String> objectClasses = Arrays.asList("top", "person", "ENTAuxTuteurStage");
        assertThat(EnumObjectClass.containsMaitre(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsMaitre sans ENTAuxTuteurStage → false")
    void doesNotContainTuteurStage() {
        List<String> objectClasses = Arrays.asList("top", "person");
        assertThat(EnumObjectClass.containsMaitre(objectClasses)).isFalse();
    }

    @Test
    @DisplayName("containsMaitre avec liste vide → false")
    void emptyList() {
        assertThat(EnumObjectClass.containsMaitre(Collections.emptyList())).isFalse();
    }

    @Test
    @DisplayName("containsMaitre avec null → false")
    void nullList() {
        assertThat(EnumObjectClass.containsMaitre(null)).isFalse();
    }

    @Test
    @DisplayName("containsMaitre est case-insensitive")
    void caseInsensitive() {
        List<String> objectClasses = List.of("entauxtuteurstage");
        assertThat(EnumObjectClass.containsMaitre(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsEleve avec ENTEleve → true")
    void containsEleve() {
        List<String> objectClasses = List.of("top", "ENTEleve");
        assertThat(EnumObjectClass.containsEleve(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsEleve sans ENTEleve → false")
    void doesNotContainEleve() {
        List<String> objectClasses = List.of("top", "person");
        assertThat(EnumObjectClass.containsEleve(objectClasses)).isFalse();
    }

    @Test
    @DisplayName("containsParent avec ENTAuxPersRelEleve → true")
    void containsParent() {
        List<String> objectClasses = List.of("ENTAuxPersRelEleve");
        assertThat(EnumObjectClass.containsParent(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsEnseignant avec ENTAuxEnseignant → true")
    void containsEnseignant() {
        List<String> objectClasses = List.of("ENTAuxEnseignant");
        assertThat(EnumObjectClass.containsEnseignant(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsNonEns avec ENTAuxNonEnsEtab → true")
    void containsNonEnsEtab() {
        List<String> objectClasses = List.of("ENTAuxNonEnsEtab");
        assertThat(EnumObjectClass.containsNonEns(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsNonEns avec ENTAuxNonEnsServAc → true")
    void containsNonEnsServAc() {
        List<String> objectClasses = List.of("ENTAuxNonEnsServAc");
        assertThat(EnumObjectClass.containsNonEns(objectClasses)).isTrue();
    }

    @Test
    @DisplayName("containsNonEns sans NonEns → false")
    void doesNotContainNonEns() {
        List<String> objectClasses = List.of("ENTAuxEnseignant");
        assertThat(EnumObjectClass.containsNonEns(objectClasses)).isFalse();
    }

    @Test
    @DisplayName("Utilisateur avec plusieurs objectClass — cascade complète")
    void multipleObjectClasses() {
        List<String> objectClasses = List.of("ENTAuxEnseignant", "ENTAuxTuteurStage");
        assertThat(EnumObjectClass.containsEnseignant(objectClasses)).isTrue();
        assertThat(EnumObjectClass.containsMaitre(objectClasses)).isTrue();
        assertThat(EnumObjectClass.containsEleve(objectClasses)).isFalse();
        assertThat(EnumObjectClass.containsParent(objectClasses)).isFalse();
        assertThat(EnumObjectClass.containsNonEns(objectClasses)).isFalse();
    }

    @Test
    @DisplayName("contains avec cible spécifique")
    void containsWithTarget() {
        List<String> objectClasses = List.of("ENTEleve", "ENTAuxPersRelEleve");
        assertThat(EnumObjectClass.contains(objectClasses, EnumObjectClass.ENTEleve)).isTrue();
        assertThat(EnumObjectClass.contains(objectClasses, EnumObjectClass.ENTAuxPersRelEleve)).isTrue();
        assertThat(EnumObjectClass.contains(objectClasses, EnumObjectClass.ENTAuxEnseignant)).isFalse();
    }

    @Test
    @DisplayName("containsAny avec plusieurs cibles")
    void containsAny() {
        List<String> objectClasses = List.of("ENTAuxNonEnsServAc");
        assertThat(EnumObjectClass.containsAny(objectClasses, EnumObjectClass.ENTAuxNonEnsEtab, EnumObjectClass.ENTAuxNonEnsServAc)).isTrue();
        assertThat(EnumObjectClass.containsAny(objectClasses, EnumObjectClass.ENTEleve, EnumObjectClass.ENTAuxPersRelEleve)).isFalse();
    }
}
