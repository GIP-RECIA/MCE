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
package fr.recia.mce.api.escomceapi.ldap;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalUserHelper {

    private String userIdAttribute;

    private String userDisplayNameAttribute;

    private String userEmailAttribute;

    private String userSearchAttribute;

    private String userGroupAttribute;

    private String userEleveRelationAttribute;

    private String userEleveTuteurAttribute;

    private String userTuteurEleveAttribute;

    private String userEleveEnseignement;

    private String userCodeMatiereEnseignement;

    private Set<String> otherUserAttributes;

    private Set<String> otherUserDisplayedAttributes;

    private String userDNSubPath;

    public Set<String> getAttributes() {
        Set<String> set = new HashSet<>();

        set.add(userIdAttribute);
        set.add(userDisplayNameAttribute);
        set.add(userEmailAttribute);
        set.add(userSearchAttribute);
        set.add(userGroupAttribute);
        set.add(userEleveRelationAttribute);
        set.add(userEleveTuteurAttribute);
        set.add(userTuteurEleveAttribute);
        set.add(userEleveEnseignement);
        set.add(userCodeMatiereEnseignement);
        set.addAll(otherUserAttributes);
        set.addAll(otherUserDisplayedAttributes);
        return set;
    }
}
