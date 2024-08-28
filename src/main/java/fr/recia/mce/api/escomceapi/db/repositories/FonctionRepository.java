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
package fr.recia.mce.api.escomceapi.db.repositories;

import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.entities.Fonction;

@Repository
public interface FonctionRepository extends AbstractRepository<Fonction, Long> {

    @Query("SELECT DISTINCT new fr.recia.mce.api.escomceapi.db.dto.FonctionDTO(a.id, t.libelleFiliere, d.disciplinePoste, s.siren, d.code, t.codeFiliere, case when a.dateFin is null then true else false end as active) "
            +
            "from AFonction a, Fonction f, AStructure s , Discipline d, TypeFonctionFiliere t " +
            "where a.aPersonne.id = :personne " +
            "and f.id = a.id " +
            "and f.discipline = d.id " +
            "and f.typeFonctionFiliere = t.id "
            + "and f.aStructure = s.id")
    Collection<FonctionDTO> findAllFonction(final Long personne);
}
