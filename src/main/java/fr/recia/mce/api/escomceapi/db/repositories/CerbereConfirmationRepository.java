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

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;

@Repository
public interface CerbereConfirmationRepository extends AbstractRepository<CerbereConfirmation, Long> {

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NOT NULL ORDER BY cc.confirmation DESC")
    List<CerbereConfirmation> findConfirmedByPersonId(@Param("personId") Long personId);

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.code = :code AND cc.confirmation IS NULL")
    Optional<CerbereConfirmation> findPendingByPersonIdAndCode(@Param("personId") Long personId, @Param("code") String code);

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NULL")
    List<CerbereConfirmation> findPendingByPersonId(@Param("personId") Long personId);

    @Modifying
    @Query("UPDATE CerbereConfirmation cc SET cc.confirmation = CURRENT_TIMESTAMP WHERE cc.id = :id")
    void markConfirmed(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NULL")
    void deletePendingByPersonId(@Param("personId") Long personId);

}
