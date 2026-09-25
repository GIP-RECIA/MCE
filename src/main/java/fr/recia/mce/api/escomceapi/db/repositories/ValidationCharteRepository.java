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

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fr.recia.mce.api.escomceapi.db.entities.ValidationCharte;
import fr.recia.mce.api.escomceapi.db.entities.ValidationCharteId;

@Repository
public interface ValidationCharteRepository extends AbstractRepository<ValidationCharte, ValidationCharteId> {

    /**
     * Validation de la charte d'une personne pour un service donné, ou {@code null} si absente.
     */
    @Query("SELECT v FROM ValidationCharte v WHERE v.aPersonneId = :aPersonneId AND v.serviceId = :serviceId")
    ValidationCharte findByApersonneIdAndServiceId(@Param("aPersonneId") Long aPersonneId,
            @Param("serviceId") String serviceId);

    /**
     * Toutes les validations de charte d'une personne (une ligne par service), ordonnées par service.
     */
    @Query("SELECT v FROM ValidationCharte v WHERE v.aPersonneId = :aPersonneId ORDER BY v.serviceId")
    List<ValidationCharte> findAllByAPersonneId(@Param("aPersonneId") Long aPersonneId);

}