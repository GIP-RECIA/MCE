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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerberePassword;

@Repository
public interface CerberePasswordRepository extends AbstractRepository<CerberePassword, Long> {

    @Query("FROM CerberePassword cp WHERE cp.aPersonne = :aPersonne ORDER BY cp.debut DESC")
    List<CerberePassword> findByAPersonne(@Param("aPersonne") APersonne aPersonne);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cerbere_password SET password = :password WHERE idPersonne = :idPersonne AND debut = CURRENT_DATE", nativeQuery = true)
    void updatePasswordForToday(@Param("idPersonne") Long idPersonne, @Param("password") String password);
}
