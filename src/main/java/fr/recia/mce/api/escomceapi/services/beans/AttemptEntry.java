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

package fr.recia.mce.api.escomceapi.services.beans;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compteur de tentatives horodaté : le champ {@code lastTouchMs} est rafraîchi à chaque accès (bon ou mauvais code), ce qui permet à
 * {@link #purgeStaleAttemptEntries()} de supprimer les entrées des utilisateurs partis sans conclure.
 */
public class AttemptEntry {
    /**
     * Durée de conservation d'une entrée de compteur de tentatives : doit dépasser la durée de vie d'un code (expiryHours) pour ne pas purger un compteur
     * encore pertinent, tout en libérant la mémoire des comptes abandonnés en cours de route.
     */
    private static final long ATTEMPT_ENTRY_TTL_MS = 2 * 3_600_000L;

    public final AtomicInteger count = new AtomicInteger(0);
    volatile long lastTouchMs = System.currentTimeMillis();

    public boolean isStale(long nowMs) {
        return nowMs - lastTouchMs >= ATTEMPT_ENTRY_TTL_MS;
    }

    public void touch() {
        lastTouchMs = System.currentTimeMillis();
    }
}
