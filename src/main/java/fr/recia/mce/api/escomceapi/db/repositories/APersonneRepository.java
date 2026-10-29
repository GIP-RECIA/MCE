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

import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface APersonneRepository extends AbstractRepository<APersonne, Long> {

    @Query("SELECT a FROM APersonne a WHERE a.uid = :uid")
    APersonne findByUid(final String uid);

    // TODO : à améliorer
    @Query("SELECT a FROM APersonne a WHERE LOWER(a.uid) = LOWER(:login) " +
            "OR a IN (SELECT l.aPersonneByAPersonneLogin FROM Login l WHERE LOWER(l.nom) = LOWER(:login)) " +
            "OR a IN (SELECT l.aPersonneByAPersonneAlias FROM Login l WHERE LOWER(l.nom) = LOWER(:login))")
    APersonne findByLogin(@Param("login") final String login);

    @Query("SELECT new fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto(a, s, l, c) " +
            "FROM APersonne a " +
            "JOIN Login l ON a = l.aPersonneByAPersonneLogin " +
            "JOIN AStructure s ON s = a.aStructure " +
            "LEFT JOIN CerbereEnfant c ON c.aPersonneByIdParent=a " +
            "WHERE a.uid = :uid")
    DatabaseUserDto getPersonneByUid(final String uid);

    @Query("SELECT DISTINCT new fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto(a, s, l, ce) " +
            "FROM APersonne a, CerbereEnfant ce, AStructure s, Login l " +
            "WHERE ce.aPersonneByIdParent.id = :parent " +
            "AND ce.aPersonneByIdEnfant = a " +
            "AND a = l.aPersonneByAPersonneLogin " +
            "AND s = a.aStructure " +
            "AND (a.etat != 'Delete' OR a.dateModification < a.dateAcquittement)")
    Collection<DatabaseUserDto> findAllEnfantOf(@Param("parent") Long parent);

    /**
     * Fallback DB : Retourne les parents/tuteurs d'un élève (utilisé quand LDAP ne renvoie rien)
     */
    @Query("SELECT new fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact(" +
            "'CONTACT2ELEVE', " + // sens = du parent vers l'enfant
            "c.aPersonneByIdParent, " + // le PARENT
            "c.aPersonneByIdEnfant, " + // l'ENFANT
            "c.typeRelation, " +
            "c.lienParente, " +
            "true) " +
            "FROM CerbereEnfant c " +
            "WHERE c.aPersonneByIdEnfant.uid = :uidEleve")
    List<RelationEleveContact> findAllParentOfEleve(@Param("uidEleve") String uidEleve);

}
