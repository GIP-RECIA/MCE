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

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.enums.ConfirmationType;

@Repository
public interface CerbereConfirmationRepository extends AbstractRepository<CerbereConfirmation, Long> {

    // ── Confirmées ──

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NOT NULL ORDER BY cc.confirmation DESC")
    List<CerbereConfirmation> findConfirmedByPersonId(@Param("personId") Long personId);

    // ── Pending par type ──

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NULL AND cc.code LIKE :likePattern")
    List<CerbereConfirmation> findPendingByPersonIdAndType(@Param("personId") Long personId, @Param("likePattern") String likePattern);

    default List<CerbereConfirmation> findPendingEmailVerificationByPersonId(Long personId) {
        return findPendingByPersonIdAndType(personId, ConfirmationType.EMAIL_VERIFICATION.getLikePattern());
    }

    default List<CerbereConfirmation> findPendingPasswordResetByPersonId(Long personId) {
        return findPendingByPersonIdAndType(personId, ConfirmationType.PASSWORD_RESET.getLikePattern());
    }

    // ── Pending par type + code ──

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.code = :code AND cc.confirmation IS NULL AND cc.code LIKE :likePattern")
    Optional<CerbereConfirmation> findPendingByPersonIdAndCodeAndType(@Param("personId") Long personId, @Param("code") String code, @Param("likePattern") String likePattern);

    default Optional<CerbereConfirmation> findPendingEmailVerificationByPersonIdAndCode(Long personId, String code) {
        return findPendingByPersonIdAndCodeAndType(personId, code, ConfirmationType.EMAIL_VERIFICATION.getLikePattern());
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.code = :code AND cc.confirmation IS NULL AND cc.code LIKE :likePattern")
    Optional<CerbereConfirmation> findPendingByPersonIdAndCodeAndTypeWithLock(@Param("personId") Long personId, @Param("code") String code, @Param("likePattern") String likePattern);

    default Optional<CerbereConfirmation> findPendingPasswordResetByPersonIdAndCodeWithLock(Long personId, String code) {
        return findPendingByPersonIdAndCodeAndTypeWithLock(personId, code, ConfirmationType.PASSWORD_RESET.getLikePattern());
    }

    // ── Dernières confirmations par type ──

    @Query("FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.code LIKE :likePattern ORDER BY cc.id DESC")
    List<CerbereConfirmation> findLatestByPersonIdAndType(@Param("personId") Long personId, @Param("likePattern") String likePattern);

    default List<CerbereConfirmation> findLatestPasswordResetByPersonId(Long personId) {
        return findLatestByPersonIdAndType(personId, ConfirmationType.PASSWORD_RESET.getLikePattern());
    }

    // ── Suppression ──

    @Modifying
    @Query("DELETE FROM CerbereConfirmation cc WHERE cc.aPersonne.id = :personId AND cc.confirmation IS NULL AND cc.code LIKE :likePattern")
    void deletePendingByPersonIdAndType(@Param("personId") Long personId, @Param("likePattern") String likePattern);

    default void deletePendingEmailVerificationByPersonId(Long personId) {
        deletePendingByPersonIdAndType(personId, ConfirmationType.EMAIL_VERIFICATION.getLikePattern());
    }

    default void deletePendingPasswordResetByPersonId(Long personId) {
        deletePendingByPersonIdAndType(personId, ConfirmationType.PASSWORD_RESET.getLikePattern());
    }

}
