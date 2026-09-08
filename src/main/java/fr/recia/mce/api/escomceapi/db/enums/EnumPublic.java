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
package fr.recia.mce.api.escomceapi.db.enums;

import lombok.AccessLevel;
import lombok.Getter;

import fr.recia.mce.api.escomceapi.db.dto.StructureDTO.DomSource;

/**
 * Profil "public" d'une personne.
 *
 * <p>
 * Chaque constante porte, dans cet ordre : les {@link #rules(Rule...) règles} qui permettent de la résoudre à
 * partir d'une personne, puis ses attributs ({@code connectOk}, {@code eleve}, {@code parent},
 * {@code educonnect}, {@code passEtab}). La résolution combine {@link EnumCategorie} (catégorie LDAP),
 * {@link DomSource} (source de la structure), le caractère local de l'authentification et le caractère collectivité.
 * </p>
 *
 * <p>
 * L'ordre de déclaration des constantes fait office de précédence dans {@link #resolve(EnumCategorie, DomSource,
 * boolean, boolean)} : une personne est attribuée au premier profil dont une règle correspond.
 * </p>
 *
 * <p>
 * Ajouter un profil se résume à ajouter une constante (règles + attributs), sans toucher au reste du code :
 * {@code UserDTOFactoryImpl.evalPublic} délègue simplement à {@code resolve}. Aucune règle de résolution n'est
 * ailleurs dans le code.
 * </p>
 *
 * <p>
 * NB : le {@code ntPass} (mot de passe Samba NT) n'est PAS un attribut de ce profil : il dépend en plus de la
 * source ({@code DomSource.GIP}) et de l'appartenance runtime à un groupe LDAP ({@code groupsWithNtPassword}).
 * Seul le critère de profil ({@code CVDL}) est exprimé ici, via {@link #isNtProfile()}.
 * </p>
 */
@Getter
public enum EnumPublic {

    /**
     * Les élèves CFR / CFA (apprentis).
     */
    APPRENANT(
            rules(rule(EnumCategorie.ELEVE, DomSource.CFA)),
            true, true, false, false, true),

    /**
     * Les élèves de l'éducation nationale connectés via EduConnect.
     */
    ELEVE_EDUC(
            rules(rule(EnumCategorie.ELEVE, DomSource.AC, false)),
            false, true, false, true, true),

    /**
     * Les élèves de l'enseignement agricole (non CFA).
     */
    ELEVE_AGRI(
            rules(rule(EnumCategorie.ELEVE, DomSource.LA)),
            false, true, false, false, true),

    /**
     * Les élèves (authentification locale).
     */
    ELEVE(
            rules(rule(EnumCategorie.ELEVE, DomSource.AC, true), rule(EnumCategorie.ELEVE)),
            true, true, false, false, true),

    /**
     * Les parents de l'éducation nationale connectés via EduConnect.
     */
    PARENT_EDUC(
            rules(
                rule(EnumCategorie.PARENT, DomSource.AC, false)),
            false, false, true, true, false),

    /**
     * Les parents de l'enseignement agricole.
     */
    PARENT_AGRI(
            rules(rule(EnumCategorie.PARENT, DomSource.LA)),
            false, false, true, false, false),

    /**
     * Les parents (authentification locale).
     */
    PARENT(
            rules(rule(EnumCategorie.PARENT, DomSource.AC, true), rule(EnumCategorie.PARENT)),
            true, false, true, false, false),

    /**
     * Personnel de l'éducation nationale (enseignants et non-enseignants), authentifié hors local (CAS).
     */
    EDUCATION(
            rules(
                    rule(EnumCategorie.PROF, DomSource.AC, false),
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.AC, false),
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.AC, false),
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.AC, false, false)),
            false, false, false, false, true),

    /**
     * Personnel enseignement agricole, authentifié par l'enseignement agricole (hors local).
     */
    AGRI(
            rules(rule(EnumCategorie.PROF, DomSource.LA, false), rule(EnumCategorie.NON_PROF_ETAB, DomSource.LA, false),
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.LA, false),
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.LA, false, false)),
            false, false, false, false, true),

    /**
     * Personnel de la région Centre-Val de Loire ! Attention, il y a aussi les personnes des collectivités sans
     * contrôle !
     */
    CVDL(
            rules(rule(EnumCategorie.NON_PROF_COL_LOCAL, false, true)),
            false, false, false, false, true),

    /**
     * Personnel ni éducation nationale ni agricole, authentifié localement.
     */
    PERSONNEL(
            rules(
                    // collectivité + source locale -> personnel (le non-local coll. va sur CVDL, déclaré avant)
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, true, true),
                    // collectivité non-collectivité, académie + local -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.AC, true, false),
                    // collectivité non-collectivité, agricole + local -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.LA, true, false),
                    // enseignant académie + local -> personnel
                    rule(EnumCategorie.PROF, DomSource.AC, true),
                    // enseignant agricole + local -> personnel
                    rule(EnumCategorie.PROF, DomSource.LA, true),
                    // non-enseignant établissement académie + local -> personnel
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.AC, true),
                    // non-enseignant établissement agricole + local -> personnel
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.LA, true),
                    // non-enseignant académique académie + local -> personnel
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.AC, true),
                    // non-enseignant académique agricole + local -> personnel
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.LA, true),
                    // catch-all : tout enseignant restant (autre source GIP/COLL/CFA ou sans structure) -> personnel
                    rule(EnumCategorie.PROF),
                    // catch-all : tout non-enseignant établissement restant -> personnel
                    rule(EnumCategorie.NON_PROF_ETAB),
                    // catch-all : toute collectivité restante -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL)),
            true, false, false, false, true),

    /**
     * Entreprises, tuteurs de stage…
     */
    EXTERIEUR(
            rules(rule(EnumCategorie.ENTREPRISE), rule(EnumCategorie.TUTEUR)),
            true, false, false, false, false),

    /**
     * Le reste : toute personne non reconnue par les profils précédents.
     */
    AUTRE(
            rules(rule(EnumCategorie.NON_PROF_ACAD)),
            true, false, false, false, false);

    /** Règles de résolution (catégorie × source × local × collectivité) ; l'ordre de la liste compte. */
    @Getter(AccessLevel.NONE)
    private final Rule[] rules;

    /** {@code true} = le profil se connecte avec un mot de passe local ; {@code false} = SSO externe (CAS). */
    private final boolean connectOk;

    /** {@code true} = le profil représente un élève (local, educonnect ou agricole). */
    private final boolean eleve;

    /**
     * {@code true} = le profil représente un parent (local, educonnect ou agricole).
     *
     * <p>
     * Actuellement inutilisée : la distinction entre parents est déjà portée par la valeur de l'enum
     * ({@code PARENT}/{@code PARENT_EDUC}/{@code PARENT_AGRI}) et par {@link #isConnectOk()} /
     * {@link #isEduconnect()}. Ménagée pour de futures règles « est parent » (périmètre, mots de passe, relations).
     * </p>
     */
    private final boolean parent;

    /** {@code true} = le profil se connecte via le portail EduConnect (il ne gère jamais son mot de passe ici). */
    private final boolean educonnect;

    /**
     * {@code true} = le profil peut changer son mot de passe établissement (périmètre GIP vérifié ailleurs).
     *
     * <p>
     * Ne suffit pas : il faut par ailleurs vérifier le périmètre (la structure est-elle gérée par le GIP).
     * </p>
     */
    private final boolean passEtab;

    /**
     * Construit un profil avec ses règles de résolution et ses attributs.
     *
     * @param rules les règles de résolution du profil
     * @param connectOk {@code true} si le profil se connecte avec un mot de passe local
     * @param eleve {@code true} si le profil représente un élève
     * @param parent {@code true} si le profil représente un parent
     * @param educonnect {@code true} si le profil se connecte via EduConnect
     * @param passEtab {@code true} si le profil peut changer son mot de passe établissement
     */
    EnumPublic(Rule[] rules, boolean connectOk, boolean eleve, boolean parent, boolean educonnect, boolean passEtab) {
        this.rules = rules;
        this.connectOk = connectOk;
        this.eleve = eleve;
        this.parent = parent;
        this.educonnect = educonnect;
        this.passEtab = passEtab;
    }

    /**
     * Résout le profil d'une personne à partir de ses caractéristiques.
     *
     * <p>
     * La précédence est l'ordre de déclaration des constantes : la première dont une règle correspond l'emporte.
     * Une catégorie inconnue ({@code null} ou sans règle) retombe sur {@code AUTRE} (comportement historique de
     * {@code evalPublic}).
     * </p>
     *
     * @param categorie la catégorie LDAP de la personne, éventuellement {@code null}
     * @param source la source de la structure, éventuellement {@code null}
     * @param local {@code true} si la source de la personne est locale ({@code SarapisUi…})
     * @param collectivite {@code true} si la source de la personne est une collectivité ({@code COLL-CVDL}/{@code COLL-CD28})
     * @return le profil attribué (jamais {@code null})
     */
    public static EnumPublic resolve(EnumCategorie categorie, DomSource source, boolean local, boolean collectivite) {
        if (categorie == null) {
            return AUTRE;
        }
        for (EnumPublic profil : values()) {
            for (Rule rule : profil.rules) {
                if (rule.matches(categorie, source, local, collectivite)) {
                    return profil;
                }
            }
        }
        return AUTRE;
    }

    /**
     * Critère de profil pour l'éligibilité {@code ntPass} : seul {@code CVDL} est un profil s'appuyant sur un mot
     * de passe NT.
     *
     * <p>
     * Rappel : l'éligibilité complète est {@code this == CVDL || DomSource.GIP.equals(source)} (voir
     * {@code UserDTOFactoryImpl.evalPublic} et {@code PasswordService.isNtPasswordProfile}) puis l'appartenance
     * runtime à un groupe LDAP.
     * </p>
     *
     * @return {@code true} pour {@code CVDL}
     */
    public boolean isNtProfile() {
        return this == CVDL;
    }

    /**
     * Regroupe les règles d'un profil. Le format d'une règle est décrit sur les méthodes {@code rule(...)}.
     *
     * <p>
     * L'ordre de la liste compte : {@link #resolve(EnumCategorie, DomSource, boolean, boolean)} teste les règles
     * dans l'ordre et s'arrête à la première qui correspond. Les règles spécifiques (source/local/collectivité
     * fixés) se déclarent donc avant les « catch-all » (règle de catégorie seule, en dernier).
     * </p>
     *
     * @param rules une à plusieurs règles de résolution, dans l'ordre de priorité (spécifiques d'abord,
     *              « catch-all » en dernier)
     * @return le tableau de règles du profil
     */
    private static Rule[] rules(Rule... rules) {
        return rules;
    }

    /**
     * Crée une règle qui ne contraint que la catégorie : elle matche la catégorie quelle que soit la source, le
     * caractère local ou la collectivité. C'est la règle dite « catch-all » de la catégorie (à placer en dernier
     * dans {@link #rules(Rule...)}).
     *
     * @param categorie la catégorie LDAP obligatoirement attendue chez la personne (jamais {@code null})
     * @return la règle, avec la catégorie fixée et les trois autres contraintes libres
     */
    private static Rule rule(EnumCategorie categorie) {
        return new Rule(categorie, null, null, null);
    }

    /**
     * Crée une règle contraignant la catégorie et la source de la structure ; le caractère local et la collectivité
     * sont quelconques.
     *
     * @param categorie la catégorie LDAP obligatoirement attendue chez la personne (jamais {@code null})
     * @param source la source de la structure obligatoirement attendue : {@code AC} (éducation nationale),
     *              {@code LA} (enseignement agricole), {@code CFA} (apprentis)…
     * @return la règle, avec la catégorie et la source fixées, local et collectivité libres
     */
    private static Rule rule(EnumCategorie categorie, DomSource source) {
        return new Rule(categorie, source, null, null);
    }

    /**
     * Crée une règle contraignant la catégorie, la source de la structure et le caractère local de
     * l'authentification ; la collectivité est quelconque.
     *
     * @param categorie la catégorie LDAP obligatoirement attendue chez la personne (jamais {@code null})
     * @param source la source de la structure obligatoirement attendue : {@code AC} (éducation nationale),
     *              {@code LA} (enseignement agricole)…
     * @param local {@code true} = seules les personnes authentifiées localement ({@code SarapisUi…}) matchent ;
     *              {@code false} = seules celles passant par un SSO externe (CAS) matchent
     * @return la règle, avec la catégorie, la source et le caractère local fixés, collectivité libre
     */
    private static Rule rule(EnumCategorie categorie, DomSource source, boolean local) {
        return new Rule(categorie, source, local, null);
    }

    /**
     * Crée une règle contraignant la catégorie, le caractère local de l'authentification et la collectivité ; la
     * source de la structure est quelconque.
     *
     * @param categorie la catégorie LDAP obligatoirement attendue chez la personne (jamais {@code null})
     * @param local {@code true} = seules les personnes authentifiées localement ({@code SarapisUi…}) matchent ;
     *              {@code false} = seules celles passant par un SSO externe (CAS) matchent
     * @param collectivite {@code true} = seules les personnes dont la structure est une collectivité
     *              ({@code COLL-CVDL}, {@code COLL-CD28}) matchent ; {@code false} = seules celles dont la
     *              structure n'en est pas une matchent
     * @return la règle, avec la catégorie, le caractère local et la collectivité fixés, source libre
     */
    @SuppressWarnings("SameParameterValue")
    private static Rule rule(EnumCategorie categorie, boolean local, boolean collectivite) {
        return new Rule(categorie, null, local, collectivite);
    }

    /**
     * Crée une règle contraignant la catégorie, la source de la structure, le caractère local de l'authentification
     * et la collectivité : c'est la forme complète de référence.
     *
     * <p>
     * Format d'une règle : {@code rule(CATEGORIE, [source], [local], [collectivité])}.
     * <ul>
     * <li>{@code CATEGORIE} (obligatoire) — la catégorie LDAP de la personne : {@code ELEVE} si c'est un élève,
     * {@code PARENT} si c'est un parent, {@code PROF}/{@code NON_PROF_...} si c'est un personnel,
     * {@code ENTREPRISE}/{@code TUTEUR} si c'est un externe.</li>
     * <li>{@code source} (optionnel) — contrainte sur la source de la structure ({@code AC}, {@code LA},
     * {@code CFA}…).</li>
     * <li>{@code local} (optionnel, {@code true}/{@code false}) — {@code true} = source locale
     * ({@code SarapisUi…}), {@code false} = SSO externe (CAS).</li>
     * <li>{@code collectivité} (optionnel, {@code true}/{@code false}) — {@code true} = source collectivité.</li>
     * </ul>
     * Un argument omis signifie « n'importe quelle valeur » (joker) : les surcharges plus courtes de
     * {@code rule(...)} ne font que laisser les contraintes finales libres.
     * </p>
     *
     * <p>
     * En pratique, on écrit la règle selon la population de la personne :
     * <ul>
     * <li>élève ({@code ELEVE}) — la source discrimine le profil : {@code CFA} → {@code APPRENANT},
     * {@code AC} non-local → {@code ELEVE_EDUC}, {@code LA} → {@code ELEVE_AGRI}, AC local ou autre → {@code ELEVE}.</li>
     * <li>parent ({@code PARENT}) — même logique : {@code AC} non-local → {@code PARENT_EDUC}, {@code LA} →
     * {@code PARENT_AGRI}, AC local ou autre → {@code PARENT}.</li>
     * <li>personnel ({@code PROF}, {@code NON_PROF_...}) — la source départage : {@code AC} non-local →
     * {@code EDUCATION}, {@code LA} non-local → {@code AGRI} ; la collectivité départage {@code CVDL}
     * (collectivité non-locale) de {@code PERSONNEL} (local).</li>
     * <li>externe ({@code ENTREPRISE}, {@code TUTEUR}) — → {@code EXTERIEUR}.</li>
     * <li>sinon — les non-reconnus tombent sur {@code AUTRE}.</li>
     * </ul>
     * </p>
     *
     * @param categorie la catégorie LDAP obligatoirement attendue chez la personne (jamais {@code null})
     * @param source la source de la structure obligatoirement attendue ({@code AC}, {@code LA}…)
     * @param local {@code true} = seules les personnes authentifiées localement ({@code SarapisUi…}) matchent ;
     *              {@code false} = seules celles passant par un SSO externe (CAS) matchent
     * @param collectivite {@code true} = seules les personnes dont la structure est une collectivité
     *              ({@code COLL-CVDL}, {@code COLL-CD28}) matchent ; {@code false} = seules celles dont la
     *              structure n'en est pas une matchent
     * @return la règle, avec les quatre contraintes fixées
     */
    @SuppressWarnings("SameParameterValue")
    private static Rule rule(EnumCategorie categorie, DomSource source, boolean local, boolean collectivite) {
        return new Rule(categorie, source, local, collectivite);
    }

    /**
     * Une règle de résolution : catégorie obligatoire, puis contraintes optionnelles sur la source, le caractère
     * local et la collectivité. Un champ {@code null} signifie « sans contrainte » (valeur quelconque).
     */
    private static final class Rule {
        /** Catégorie LDAP attendue (obligatoire). */
        private final EnumCategorie categorie;
        /** Source de la structure attendue, {@code null} = quelconque. */
        private final DomSource source;
        /** Caractère local attendu, {@code null} = quelconque. */
        private final Boolean local;
        /** Caractère collectivité attendu, {@code null} = quelconque. */
        private final Boolean collectivite;

        /**
         * Construit une règle.
         *
         * @param categorie la catégorie LDAP attendue
         * @param source la source attendue, {@code null} = quelconque
         * @param local le caractère local attendu, {@code null} = quelconque
         * @param collectivite le caractère collectivité attendu, {@code null} = quelconque
         */
        private Rule(EnumCategorie categorie, DomSource source, Boolean local, Boolean collectivite) {
            this.categorie = categorie;
            this.source = source;
            this.local = local;
            this.collectivite = collectivite;
        }

        /**
         * Indique si les caractéristiques d'une personne correspondent à cette règle.
         *
         * @param cat la catégorie LDAP de la personne
         * @param src la source de la structure, éventuellement {@code null}
         * @param isLocal le caractère local de la personne
         * @param isCollectivite le caractère collectivité de la personne
         * @return {@code true} si toutes les contraintes de la règle sont satisfaites
         */
        private boolean matches(EnumCategorie cat, DomSource src, boolean isLocal, boolean isCollectivite) {
            return categorie == cat
                    && (source == null || source == src)
                    && (local == null || local == isLocal)
                    && (collectivite == null || collectivite == isCollectivite);
        }
    }
}
