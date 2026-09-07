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
package fr.recia.mce.api.escomceapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Garde anti-bruteforce en mémoire : compteurs de tentatives par personne (vérification d'email et
 * réinitialisation de mot de passe) et challenges « sans uid », purgés périodiquement.
 *
 * <p>Le compteur est <b>local à l'instance</b> : il ne simule pas un partage multi-instance.</p>
 */
@Service
@Slf4j
public class AttemptGuardService {

    /**
     * Durée de conservation d'une entrée de compteur de tentatives : doit dépasser la durée de vie d'un code (expiryHours) pour ne pas purger un compteur
     * encore pertinent, tout en libérant la mémoire des comptes abandonnés en cours de route.
     */
    private static final long ATTEMPT_ENTRY_TTL_MS = 2 * 3_600_000L;

    /**
     * Compteur de tentatives horodaté : le champ {@code lastTouchMs} est rafraîchi à chaque accès (bon ou mauvais code), ce qui permet à
     * {@link #purgeStaleAttemptEntries()} de supprimer les entrées des utilisateurs partis sans conclure.
     */
    static final class AttemptEntry {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long lastTouchMs = System.currentTimeMillis();

        boolean isStale(long nowMs) {
            return nowMs - lastTouchMs >= ATTEMPT_ENTRY_TTL_MS;
        }

        void touch() {
            lastTouchMs = System.currentTimeMillis();
        }
    }

    /**
     * Compteur de tentatives par personne pour la réinitialisation du mot de passe (uid connu).
     */
    private final ConcurrentHashMap<Long, AttemptEntry> resetAttempts = new ConcurrentHashMap<>();

    /**
     * Compteur utilisé lorsque l'utilisateur ne fournit pas d'UID : un mauvais code
     * ne permet pas encore d'identifier la personne concernée. Une consommation de ce
     * compteur peut donner une expérience délétère pour d'autres utilisateurs du même token.
     */
    private final ConcurrentHashMap<String, ResetChallenge> resetChallenges = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, AttemptEntry> verificationAttempts = new ConcurrentHashMap<>();

    static final class ResetChallenge {
        final String uid;
        final long expiresAtMs;

        ResetChallenge(String uid, long expiresAtMs) {
            this.uid = uid;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public AttemptEntry verificationAttempt(Long personId) {
        return verificationAttempts.computeIfAbsent(personId, k -> new AttemptEntry());
    }

    public void clearVerificationAttempt(Long personId) {
        verificationAttempts.remove(personId);
    }

    public AttemptEntry resetAttempt(Long personId) {
        return resetAttempts.computeIfAbsent(personId, k -> new AttemptEntry());
    }

    public void clearResetAttempt(Long personId) {
        resetAttempts.remove(personId);
    }

    public ResetChallenge resetChallenge(String token) {
        return resetChallenges.get(token);
    }

    public void putResetChallenge(String token, ResetChallenge challenge) {
        resetChallenges.put(token, challenge);
    }

    public void removeResetChallenge(String token) {
        resetChallenges.remove(token);
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void purgeStaleAttemptEntries() {
        long now = System.currentTimeMillis();
        int before = resetAttempts.size() + resetChallenges.size() + verificationAttempts.size();
        resetAttempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        resetChallenges.entrySet().removeIf(e -> e.getValue().expiresAtMs <= now);
        verificationAttempts.entrySet().removeIf(e -> e.getValue().isStale(now));
        int removed = before - resetAttempts.size() - resetChallenges.size() - verificationAttempts.size();
        if (removed > 0) {
            log.info("Purge des compteurs de tentatives expirés : {} entrée(s) supprimée(s)", removed);
        }
    }

}