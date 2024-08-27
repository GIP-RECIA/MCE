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

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;

@Repository
public interface APersonneRepository extends AbstractRepository<APersonne, Long> {

    @Query("SELECT new fr.recia.mce.api.escomceapi.db.dto.PersonneDTO(a, s, l) " +
            "FROM APersonne a " +
            "JOIN Login l ON a.id = l.aPersonneByAPersonneLogin " +
            "JOIN AStructure s ON s.id = a.aStructure " +
            "WHERE a.uid = :uid")
    PersonneDTO getPersonneByUid(final String uid);

    @Query("SELECT DISTINCT new fr.recia.mce.api.escomceapi.db.dto.PersonneDTO(a, ce, s, l) " +
            "from APersonne a, CerbereEnfant ce, AStructure s , Login l " +
            "where ce.aPersonneByIdParent.id = :parent " +
            "and ce.aPersonneByIdEnfant.id = a.id " +
            "and a.id = l.aPersonneByAPersonneLogin " +
            "and s.id = a.aStructure "
            + "and ( a.etat != 'Delete' or a.dateModification < a.dateAcquittement)")
    Collection<PersonneDTO> findAllEnfantOf(Long parent);
}
