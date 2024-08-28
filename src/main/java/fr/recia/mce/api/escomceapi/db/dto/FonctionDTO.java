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
package fr.recia.mce.api.escomceapi.db.dto;

import fr.recia.mce.api.escomceapi.db.beans.Structure;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FonctionDTO implements Comparable<FonctionDTO> {
    private String fonction;
    private String discipline;
    private Structure structure;
    private boolean active = true;

    private String codeF;
    private String codeD;
    private Long idFonction;
    private String siren;

    private String source;

    // public String getStructureName() {
    // String name = structure.getDisplayName();
    // if (StringUtils.isBlank(name)) {
    // name = structure.getNom();
    // }
    // return name.intern();
    // }

    @Override
    public int compareTo(FonctionDTO arg0) {
        if (this == arg0)
            return 0;
        int res = this.structure.compareTo(arg0.structure);
        if (res == 0) {
            res = this.fonction.compareTo(arg0.fonction);
            if (res == 0) {
                res = this.discipline.compareTo(arg0.discipline);
            }
        }
        return res;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((discipline == null) ? 0 : discipline.hashCode());
        result = prime * result
                + ((fonction == null) ? 0 : fonction.hashCode());
        result = prime * result
                + ((structure == null) ? 0 : structure.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FonctionDTO other = (FonctionDTO) obj;
        if (discipline == null) {
            if (other.discipline != null)
                return false;
        } else if (!discipline.equals(other.discipline))
            return false;
        if (fonction == null) {
            if (other.fonction != null)
                return false;
        } else if (!fonction.equals(other.fonction))
            return false;
        if (structure == null) {
            if (other.structure != null)
                return false;
        } else if (!structure.equals(other.structure))
            return false;
        return true;
    }

    // public FonctionDTO(final AFonction af, final Fonction f, final
    // Discipline d, final TypeFonctionFiliere t,
    // final String siren) {
    // this.aPersonne = af.getAPersonne();
    // this.idFonction = f.getId();
    // this.discipline = d.getId();
    // this.filiere = t.getId();
    // this.siren = siren;

    // }

    public FonctionDTO(String disciplinePoste, String fonction, String source, String structure) {
        this.discipline = disciplinePoste;
        this.fonction = fonction;
        this.source = source;
        this.siren = structure;
    }

    public FonctionDTO(Long idFonction, String fonction, String disciplinePoste, String siren, String codeD,
            String codeF, boolean active) {
        this.idFonction = idFonction;
        this.discipline = disciplinePoste;
        this.fonction = fonction;
        this.siren = siren;
        this.codeD = codeD;
        this.codeF = codeF;
        this.active = active;
    }

}
