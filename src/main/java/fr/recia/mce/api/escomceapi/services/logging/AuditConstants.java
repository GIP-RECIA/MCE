package fr.recia.mce.api.escomceapi.services.logging;

public final class AuditConstants {

    private AuditConstants() {}

    // Actions
    public static final String ACTION_CHANGE_PASSWORD  = "CHANGE_PASSWORD";
    public static final String ACTION_VERIFY_PASSWORD  = "VERIFY_PASSWORD";
    public static final String ACTION_GENERATE_PASSWORD = "GENERATE_PASSWORD";
    public static final String ACTION_REQUIRES_SSHA    = "REQUIRES_SSHA";
    public static final String ACTION_REQUIRES_SAMBA   = "REQUIRES_SAMBA";
    public static final String ACTION_PARSE            = "PARSE";
    public static final String ACTION_UPDATE_DB        = "UPDATE_DB";
    public static final String ACTION_UPDATE_LDAP      = "UPDATE_LDAP";
    public static final String ACTION_GET_USER_BY_ID      = "ACTION_GET_USER_BY_ID";
    public static final String ACTION_UPDATE_PASSWORD      = "ACTION_UPDATE_PASSWORD";


    // Statuts
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_DENIED = "DENIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ABORTED = "ABORTED";

    // Raisons
    public static final String REASON_PERSON_NULL             = "PERSON_NULL";
    public static final String REASON_WEAK_PASSWORD           = "WEAK_PASSWORD";
    public static final String REASON_INVALID_REQUEST         = "INVALID_REQUEST";
    public static final String REASON_VERIFY_PASSWORD_ERROR   = "VERIFY_PASSWORD_EXCEPTION";
    public static final String REASON_WRONG_OLD_PASSWORD      = "WRONG_OLD_PASSWORD";
    public static final String REASON_BUSINESS_ERROR          = "BUSINESS_ERROR";
    public static final String REASON_TECHNICAL_ERROR         = "TECHNICAL_ERROR";
    public static final String REASON_INVALID_INPUT           = "INVALID_INPUT";
    public static final String REASON_NO_PASSWORD_STORED      = "NO_PASSWORD_STORED";
    public static final String REASON_ACCOUNT_ACTIVE_NO_PASSWORD          = "ACCOUNT_ACTIVE_NO_PASSWORD";
    public static final String REASON_PLAIN_NOT_ALLOWED       = "PLAIN_PASSWORD_NOT_ALLOWED";
    public static final String REASON_PLAIN_MISMATCH          = "PLAIN_PASSWORD_MISMATCH";
    public static final String REASON_CORRUPTED_HASH          = "CORRUPTED_HASH";
    public static final String REASON_PASSWORD_MISMATCH       = "PASSWORD_MISMATCH";
    public static final String REASON_NO_REGEX                = "NO_REGEX_CONFIGURED";
    public static final String REASON_EXT_USER_NULL           = "EXT_USER_NULL";
    public static final String REASON_NO_LDAP_GROUPS          = "NO_LDAP_GROUPS";
    public static final String REASON_INVALID_REGEX           = "INVALID_REGEX";
    public static final String REASON_USER_NOT_FOUND          = "USER_NOT_FOUND_IN_DB";
    public static final String REASON_LDAP_UPDATE_FAILED      = "LDAP_UPDATE_FAILED";
    public static final String REASON_EMPTY_LDAP_HASH         = "EMPTY_LDAP_HASH";
    public static final String REASON_HASH_REGEX_MISMATCH     = "HASH_REGEX_MISMATCH";
    public static final String REASON_SSHA_PAYLOAD_TOO_SHORT  = "SSHA_PAYLOAD_TOO_SHORT";
    public static final String REASON_UNKNOWN_ALGO            = "UNKNOWN_ALGO";
    public static final String REASON_LM_HASH_FAILED = "LM_HASH_FAILED";
    public static final String REASON_NT_HASH_FAILED = "NT_HASH_FAILED";
    public static final String REASON_MD4_DIGEST_FAILED = "MD4_DIGEST_FAILED";
}
