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
