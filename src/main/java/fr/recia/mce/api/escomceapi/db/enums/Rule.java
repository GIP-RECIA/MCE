package fr.recia.mce.api.escomceapi.db.enums;

import lombok.Data;

@Data
public class Rule {
    /** Catégorie LDAP attendue (obligatoire). */
    private final EnumCategorie categorie;
    /** Source de la structure attendue, null = quelconque. */
    private final DomSource source;
    /** Caractère local attendu, null = quelconque. */
    private final Boolean local;
    /** Caractère collectivité attendu, null = quelconque. */
    private final Boolean collectivite;

    /**
     * Indique si les caractéristiques d'une personne correspondent à cette règle.
     */
    public boolean matches(EnumCategorie cat, DomSource src, boolean isLocal, boolean isCollectivite) {
        return categorie == cat && (source == null || source == src) && (local == null || local == isLocal) && (collectivite == null || collectivite == isCollectivite);
    }
}
