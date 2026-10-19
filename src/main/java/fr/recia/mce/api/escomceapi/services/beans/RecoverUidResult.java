package fr.recia.mce.api.escomceapi.services.beans;

import fr.recia.mce.api.escomceapi.web.dto.RecoverUidRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Informations de charte de la cible résolue par {@link #recoverUid(RecoverUidRequestDTO)}.
 * Ne contient aucun identifiant (uid) : transmissible au front sans risque d'énumération.
 */
@Getter
@Setter
@AllArgsConstructor
public class RecoverUidResult {
    private final boolean charteRequired;
    private final String charteUrl;
    private final String resetToken;

    public RecoverUidResult(boolean charteRequired, String charteUrl) {
        this(charteRequired, charteUrl, null);
    }

}
