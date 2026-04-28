package fr.recia.mce.api.escomceapi.services.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

public final class AuditLogger {

    private AuditLogger() {}

    /**
     * Log un message de niveau INFO avec action, statut, uid et raison.
     * Initialise le MDC avant le log puis le nettoie après.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     * @param reason la raison associée (optionnelle)
     */
    public static void info(Logger log, String action, String status, String uid, String reason) {
        setMdc(action, status, uid, reason);
        try {
            log.info("action={} status={} uid={} reason={}", action, status, uid(uid), reason(reason));
        } finally {
            clearMdc();
        }
    }


    /**
     * Log un message de niveau INFO sans raison.
     * Initialise le MDC avant le log puis le nettoie après.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     */
    public static void info(Logger log, String action, String status, String uid) {
        setMdc(action, status, uid, null);
        try {
            log.info("action={} status={} uid={}", action, status, uid(uid));
        } finally {
            clearMdc();
        }
    }

    /**
     * Log un message de niveau WARN avec une raison.
     * Version simplifiée sans champ extra.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     * @param reason la raison associée
     */
    public static void warn(Logger log, String action, String status, String uid, String reason) {
        warn(log, action, status, uid, reason, null);
    }

    /**
     * Log un message de niveau WARN avec une raison et un champ supplémentaire optionnel.
     * Initialise le MDC avant le log puis le nettoie après.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     * @param reason la raison associée
     * @param extra  information supplémentaire optionnelle (peut être null)
     */
    public static void warn(Logger log, String action, String status, String uid, String reason, String extra) {
        setMdc(action, status, uid, reason);
        try {
            if (extra != null) {
                log.warn("action={} status={} uid={} reason={} {}", action, status, uid(uid), reason(reason), extra);
            } else {
                log.warn("action={} status={} uid={} reason={}", action, status, uid(uid), reason(reason));
            }
        } finally {
            clearMdc();
        }
    }

    /**
     * Log un message de niveau ERROR avec une raison.
     * Initialise le MDC avant le log puis le nettoie après.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     * @param reason la raison associée
     */
    public static void error(Logger log, String action, String status, String uid, String reason) {
        setMdc(action, status, uid, reason);
        try {
            log.error("action={} status={} uid={} reason={}", action, status, uid(uid), reason(reason));
        } finally {
            clearMdc();
        }
    }
    public static void error(Logger log, String action, String status, String uid, String reason, Throwable t) {
        setMdc(action, status, uid, reason);
        try {
            log.error("action={} status={} uid={} reason={}", action, status, uid(uid), reason(reason), t);
        } finally {
            clearMdc();
        }
    }

    /**
     * Log un message de niveau ERROR avec une exception.
     * Initialise le MDC avant le log puis le nettoie après.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     * @param reason la raison associée
     * @param extra      l'exception à logger
     */
    public static void error(Logger log, String action, String status, String uid, String reason, String extra) {
        error(log, action, status, uid, reason, extra, null);
    }

    /**
     * Log un message de niveau ERROR avec un champ supplémentaire et une exception.
     * Méthode principale pour les logs d'erreur.
     * Initialise le MDC avant le log puis le nettoie après.
     *
     * @param log    le logger utilisé
     * @param action l'action métier effectuée
     * @param status le statut associé à l'action
     * @param uid    l'identifiant utilisateur (ou "unknown" si null)
     * @param reason la raison associée
     * @param extra  information supplémentaire optionnelle (peut être null)
     * @param t      l'exception à logger (peut être null)
     */
    public static void error(Logger log, String action, String status, String uid, String reason, String extra, Throwable t) {
        setMdc(action, status, uid, reason);
        try {
            if (extra != null) {
                log.error("action={} status={} uid={} reason={} {}", action, status, uid(uid), reason(reason), extra, t);
            } else {
                log.error("action={} status={} uid={} reason={}", action, status, uid(uid), reason(reason), t);
            }
        } finally {
            clearMdc();
        }
    }

    /**
     * Initialise le MDC avec les informations de contexte.
     *
     * @param action l'action métier (vide si null)
     * @param status le statut (vide si null)
     * @param uid    l'identifiant utilisateur (transformé via uid())
     * @param reason la raison (transformée via reason())
     */
    private static void setMdc(String action, String status, String uid, String reason) {
        MDC.put("action", action  != null ? action  : "");
        MDC.put("status", status  != null ? status  : "");
        MDC.put("uid",    uid(uid));
        MDC.put("reason", reason(reason));
    }



    /**
     * Nettoie les champs du MDC utilisés par cette classe.
     */
    private static void clearMdc() {
        MDC.remove("action");
        MDC.remove("status");
        MDC.remove("uid");
        MDC.remove("reason");
    }

    /**
     * Retourne l'uid ou "unknown" si null.
     *
     * @param uid identifiant utilisateur
     * @return uid non null
     */
    private static String uid(String uid)       { return uid    != null ? uid    : "unknown"; }

    /**
     * Retourne la raison ou une chaîne vide si null.
     *
     * @param reason raison brute
     * @return raison non null
     */
    private static String reason(String reason) { return reason != null ? reason : "";        }
}