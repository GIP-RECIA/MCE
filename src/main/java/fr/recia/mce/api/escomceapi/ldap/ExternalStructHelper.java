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
public class ExternalStructHelper {

    private String structIdAttribute;

    private String structNameAttribute;

    private String structDisplayNameAttribute;

    private String structTypeAttribute;

    private String structDomainesAttribute;

    private String structJointureAttribute;

    private String structUaiAttribute;

    private String structVilleAttribute;

    private String structDNSubPath;

    public Set<String> getAttributes() {

        Set<String> set = new HashSet<>();
        set.add(structIdAttribute);
        set.add(structNameAttribute);
        set.add(structDisplayNameAttribute);
        set.add(structTypeAttribute);
        set.add(structDomainesAttribute);
        set.add(structJointureAttribute);
        set.add(structUaiAttribute);
        set.add(structVilleAttribute);

        return set;
    }

}
