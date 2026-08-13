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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tests - EnumPublic (droits mot de passe / connexion)")
class EnumPublicTest {

    @ParameterizedTest(name = "{0} → isConnectOk=false (mdp interdit)")
    @EnumSource(value = EnumPublic.class, names = {
            "EDUCATION", "AGRI", "CVDL", "ELEVE_EDUC", "PARENT_EDUC"
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
            "EDUCATION", "AGRI", "CVDL", "PERSONNEL", "PARENT", "ELEVE", "APPRENANT", "EXTERIEUR", "AUTRE"
    })
    @DisplayName("Profils non EduConnect")
    void nonEduConnectProfiles(EnumPublic profile) {
        assertThat(profile.isEduconnect()).isFalse();
    }

    @ParameterizedTest(name = "{0} → isPassEtab=true")
    @EnumSource(value = EnumPublic.class, names = {
            "EDUCATION", "AGRI", "CVDL", "PERSONNEL", "ELEVE_EDUC", "APPRENANT", "ELEVE"
    })
    @DisplayName("Profils avec changement de mot de passe établissement")
    void profilesWithPassEtab(EnumPublic profile) {
        assertThat(profile.isPassEtab()).isTrue();
    }

    @ParameterizedTest(name = "{0} → isPassEtab=false")
    @EnumSource(value = EnumPublic.class, names = {
            "PARENT", "PARENT_EDUC", "EXTERIEUR", "AUTRE"
    })
    @DisplayName("Profils sans changement de mot de passe établissement")
    void profilesWithoutPassEtab(EnumPublic profile) {
        assertThat(profile.isPassEtab()).isFalse();
    }

}
