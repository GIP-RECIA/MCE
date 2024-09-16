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
package fr.recia.mce.api.escomceapi.services.classegroupe;

import fr.recia.mce.api.escomceapi.services.beans.ClasseGroupe;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionEleve;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionProf;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClasseGroupeDTO {

    private Map<String, ClasseGroupe> sectionCG;

    private SubSectionEleve sectionEleve;
    private SubSectionProf sectionProf;

}
