package fr.recia.mce.api.escomceapi.services.beans;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Regroupe le code en clair (envoyé par email), sa version hashée persistée et sa date d'expiration.
 */
@AllArgsConstructor
@Getter
@Setter
public class VerificationCodeBundle {
    private final String code;
    private final String hashedCode;
    private final Date limite;
}
