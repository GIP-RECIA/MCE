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

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import fr.recia.mce.api.escomceapi.db.dto.StructureDTO.DomSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests - EnumPublic (droits mot de passe / connexion)")
class EnumPublicTest {

    @ParameterizedTest(name = "{0} → isConnectOk=false (mdp interdit)")
    @EnumSource(value = EnumPublic.class, names = {
            "EDUCATION", "AGRI", "CVDL", "ELEVE_EDUC", "PARENT_EDUC", "ELEVE_AGRI", "PARENT_AGRI"
    })
    @DisplayName("Profils sans droit de changement de mot de passe local")
    void profilesWithoutLocalPasswordChange(EnumPublic profile) {
        assertThat(profile.isConnectOk()).isFalse();
    }

    @ParameterizedTest(name = "{0} → isConnectOk=true (mdp autorisé)")
    @EnumSource(value = EnumPublic.class, names = {
            "PERSONNEL", "PARENT", "ELEVE", "APPRENANT", "EXTERIEUR", "AUTRE"
    })
    @DisplayName("Profils avec droit de changement de mot de passe local")
    void profilesWithLocalPasswordChange(EnumPublic profile) {
        assertThat(profile.isConnectOk()).isTrue();
    }

    @ParameterizedTest(name = "{0} → isEduconnect=true")
    @EnumSource(value = EnumPublic.class, names = {"ELEVE_EDUC", "PARENT_EDUC"})
    @DisplayName("Profils EduConnect")
    void eduConnectProfiles(EnumPublic profile) {
        assertThat(profile.isEduconnect()).isTrue();
    }

    @ParameterizedTest(name = "{0} → isEduconnect=false")
    @EnumSource(value = EnumPublic.class, names = {
            "EDUCATION", "AGRI", "CVDL", "PERSONNEL", "PARENT", "ELEVE", "ELEVE_AGRI", "PARENT_AGRI", "APPRENANT", "EXTERIEUR", "AUTRE"
    })
    @DisplayName("Profils non EduConnect")
    void nonEduConnectProfiles(EnumPublic profile) {
        assertThat(profile.isEduconnect()).isFalse();
    }

    @ParameterizedTest(name = "{0} → isPassEtab=true")
    @EnumSource(value = EnumPublic.class, names = {
            "EDUCATION", "AGRI", "CVDL", "PERSONNEL", "ELEVE_EDUC", "APPRENANT", "ELEVE", "ELEVE_AGRI"
    })
    @DisplayName("Profils avec changement de mot de passe établissement")
    void profilesWithPassEtab(EnumPublic profile) {
        assertThat(profile.isPassEtab()).isTrue();
    }

    @ParameterizedTest(name = "{0} → isPassEtab=false")
    @EnumSource(value = EnumPublic.class, names = {
            "PARENT", "PARENT_EDUC", "PARENT_AGRI", "EXTERIEUR", "AUTRE"
    })
    @DisplayName("Profils sans changement de mot de passe établissement")
    void profilesWithoutPassEtab(EnumPublic profile) {
        assertThat(profile.isPassEtab()).isFalse();
    }

    @ParameterizedTest(name = "categorie={0} ds={1} local={2} collectivite={3} → {4}")
    @MethodSource("resolveMatrix")
    @DisplayName("resolve() reproduit le mapping historique de evalPublic")
    void resolveProfile(EnumCategorie categorie, DomSource source, boolean local, boolean collectivite, EnumPublic expected) {
        assertThat(EnumPublic.resolve(categorie, source, local, collectivite)).isEqualTo(expected);
    }

    static Stream<Arguments> resolveMatrix() {
        return Stream.of(
                Arguments.of(null, DomSource.AC, false, false, EnumPublic.AUTRE),
                Arguments.of(EnumCategorie.ELEVE, null, false, false, EnumPublic.ELEVE),
                Arguments.of(EnumCategorie.ELEVE, DomSource.CFA, true, false, EnumPublic.APPRENANT),
                Arguments.of(EnumCategorie.ELEVE, DomSource.CFA, false, false, EnumPublic.APPRENANT),
                Arguments.of(EnumCategorie.ELEVE, DomSource.AC, true, false, EnumPublic.ELEVE),
                Arguments.of(EnumCategorie.ELEVE, DomSource.AC, false, false, EnumPublic.ELEVE_EDUC),
                Arguments.of(EnumCategorie.ELEVE, DomSource.LA, true, false, EnumPublic.ELEVE_AGRI),
                Arguments.of(EnumCategorie.ELEVE, DomSource.LA, false, false, EnumPublic.ELEVE_AGRI),
                Arguments.of(EnumCategorie.ELEVE, DomSource.GIP, true, false, EnumPublic.ELEVE),
                Arguments.of(EnumCategorie.PARENT, DomSource.AC, true, false, EnumPublic.PARENT),
                Arguments.of(EnumCategorie.PARENT, DomSource.AC, false, false, EnumPublic.PARENT_EDUC),
                Arguments.of(EnumCategorie.PARENT, DomSource.LA, true, false, EnumPublic.PARENT_AGRI),
                Arguments.of(EnumCategorie.PARENT, DomSource.GIP, true, false, EnumPublic.PARENT),
                Arguments.of(EnumCategorie.PROF, DomSource.AC, true, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.PROF, DomSource.AC, false, false, EnumPublic.EDUCATION),
                Arguments.of(EnumCategorie.PROF, DomSource.LA, true, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.PROF, DomSource.LA, false, false, EnumPublic.AGRI),
                Arguments.of(EnumCategorie.PROF, DomSource.GIP, false, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.ENTREPRISE, null, false, false, EnumPublic.EXTERIEUR),
                Arguments.of(EnumCategorie.TUTEUR, null, false, false, EnumPublic.EXTERIEUR),
                Arguments.of(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.GIP, true, true, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.GIP, false, true, EnumPublic.CVDL),
                Arguments.of(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.AC, false, false, EnumPublic.EDUCATION),
                Arguments.of(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.LA, false, false, EnumPublic.AGRI),
                Arguments.of(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.GIP, false, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.NON_PROF_ETAB, DomSource.AC, true, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.NON_PROF_ETAB, DomSource.AC, false, false, EnumPublic.EDUCATION),
                Arguments.of(EnumCategorie.NON_PROF_ETAB, DomSource.LA, false, false, EnumPublic.AGRI),
                Arguments.of(EnumCategorie.NON_PROF_ETAB, null, false, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.NON_PROF_ACAD, DomSource.AC, true, false, EnumPublic.PERSONNEL),
                Arguments.of(EnumCategorie.NON_PROF_ACAD, DomSource.AC, false, false, EnumPublic.EDUCATION),
                Arguments.of(EnumCategorie.NON_PROF_ACAD, DomSource.LA, false, false, EnumPublic.AGRI),
                Arguments.of(EnumCategorie.NON_PROF_ACAD, DomSource.GIP, false, false, EnumPublic.AUTRE),
                Arguments.of(EnumCategorie.NON_PROF_ACAD, null, false, false, EnumPublic.AUTRE));
    }

}
