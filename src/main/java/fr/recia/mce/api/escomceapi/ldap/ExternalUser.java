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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExternalUser implements IExternalUser {

    private String id;

    private String displayName;

    private String email;

    private Map<String, List<String>> attributes = new HashMap<>();

    @Override
    public List<String> getAttribute(String attr) {
        if (attributes.containsKey(attr))
            return attributes.get(attr);
        else if (attributes.containsKey(attr.toLowerCase()))
            return attributes.get(attr.toLowerCase());
        return Collections.emptyList();
    }

    public String toString() {
        return "ExternalUser(id=" + this.id + ", displayName=" + this.displayName + ", email=" + this.email
                + ", attributes=" + this.attributes + ")";
    }

}
