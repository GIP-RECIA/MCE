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
package fr.recia.mce.api.escomceapi.db.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Structure implements Comparable<Structure> {

    private boolean open4Ent;
    private String nom;

    private String displayName;

    private String uai;
    private String skin;
    private String ville;
    private String siren;
    private String[] domaines;
    private String type;
    private String source;

    public void setNom(final String nom) {
        if (nom != null) {
            this.nom = nom.intern();
        }
    }

    public void setDisplayName(final String displayName) {
        if (displayName != null) {
            this.displayName = displayName.intern();
        }
    }

    public void setUai(final String uai) {
        if (uai != null) {
            this.uai = uai.intern();
        }
    }

    public void setSkin(final String skin) {
        if (skin != null) {
            this.skin = skin.intern();
        }
    }

    public void setSiren(final String siren) {
        if (siren != null) {
            this.siren = siren.intern();
        }
    }

    public void setDomaines(final String[] domaines) {
        if (domaines != null) {
            this.domaines = new String[domaines.length];
            for (int i = 0; i < domaines.length; i++) {
                this.domaines[i] = domaines[i].intern();
            }
        }
    }

    public void setType(final String type) {
        if (type != null) {
            this.type = type.intern();
        }
    }

    @Override
    public int compareTo(final @NonNull Structure arg0) {
        if (arg0 == this) {
            return 0;
        }
        return getSiren().compareTo(arg0.getSiren());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((siren == null) ? 0 : siren.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Structure other = (Structure) obj;
        if (siren == null) {
            return other.siren == null;
        } else return siren.equals(other.siren);
    }

}
