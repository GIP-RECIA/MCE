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
package fr.recia.manager.db.enums;

import lombok.Getter;

/**
 * Profil "public" d'une personne.
 */
@Getter
public enum EnumPublic {

    /**
     * Les élèves CFR / CFA (apprentis).
     */
    APPRENANT(
            rules(rule(EnumCategorie.ELEVE, DomSource.CFA, null, null)),
            true, true, false, false, true),

    /**
     * Les élèves de l'éducation nationale connectés via EduConnect.
     */
    ELEVE_EDUC(
            rules(rule(EnumCategorie.ELEVE, DomSource.AC, false, null)),
            false, true, false, true, true),

    /**
     * Les élèves de l'enseignement agricole (non CFA).
     */
    ELEVE_AGRI(
            rules(rule(EnumCategorie.ELEVE, DomSource.LA, null, null)),
            false, true, false, false, true),

    /**
     * Les élèves (authentification locale).
     */
    ELEVE(
            rules(
                    rule(EnumCategorie.ELEVE, DomSource.AC, true, null),
                    rule(EnumCategorie.ELEVE, null, null, null)),
            true, true, false, false, true),

    /**
     * Les parents de l'éducation nationale connectés via EduConnect.
     */
    PARENT_EDUC(
            rules(
                rule(EnumCategorie.PARENT, DomSource.AC, false, null)),
            false, false, true, true, false),

    /**
     * Les parents de l'enseignement agricole.
     */
    PARENT_AGRI(
            rules(rule(EnumCategorie.PARENT, DomSource.LA, null, null)),
            false, false, true, false, false),

    /**
     * Les parents (authentification locale).
     */
    PARENT(
            rules(
                    rule(EnumCategorie.PARENT, DomSource.AC, true, null),
                    rule(EnumCategorie.PARENT,null, null, null)),
            true, false, true, false, false),

    /**
     * Personnel de l'éducation nationale (enseignants et non-enseignants), authentifié hors local (CAS).
     */
    EDUCATION(
            rules(
                    rule(EnumCategorie.PROF, DomSource.AC, false, null),
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.AC, false, null),
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.AC, false, null),
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.AC, false, false)),
            false, false, false, false, true),

    /**
     * Personnel enseignement agricole, authentifié par l'enseignement agricole (hors local).
     */
    AGRI(
            rules(
                    rule(EnumCategorie.PROF, DomSource.LA, false, null),
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.LA, false, null),
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.LA, false, null),
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.LA, false, false)),
            false, false, false, false, true),

    /**
     * Personnel de la région Centre-Val de Loire.
     */
    // TODO : ces règles ne sont pas suffisantes pour identifier les personnels RCVL
    CVDL(
            rules(rule(EnumCategorie.NON_PROF_COL_LOCAL, null, false, true)),
            false, false, false, false, true),

    /**
     * Personnel ni éducation nationale ni agricole, authentifié localement.
     */
    PERSONNEL(
            rules(
                    // collectivité + source locale -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, null, true, true),
                    // collectivité non-collectivité, académie + local -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.AC, true, false),
                    // collectivité non-collectivité, agricole + local -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL, DomSource.LA, true, false),
                    // enseignant académie + local -> personnel
                    rule(EnumCategorie.PROF, DomSource.AC, true, null),
                    // enseignant agricole + local -> personnel
                    rule(EnumCategorie.PROF, DomSource.LA, true, null),
                    // non-enseignant établissement académie + local -> personnel
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.AC, true, null),
                    // non-enseignant établissement agricole + local -> personnel
                    rule(EnumCategorie.NON_PROF_ETAB, DomSource.LA, true, null),
                    // non-enseignant académique académie + local -> personnel
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.AC, true, null),
                    // non-enseignant académique agricole + local -> personnel
                    rule(EnumCategorie.NON_PROF_ACAD, DomSource.LA, true, null),
                    // catch-all : tout enseignant restant (autre source GIP/COLL/CFA ou sans structure) -> personnel
                    rule(EnumCategorie.PROF,null, null, null),
                    // catch-all : tout non-enseignant établissement restant -> personnel
                    rule(EnumCategorie.NON_PROF_ETAB,null, null, null),
                    // catch-all : toute collectivité restante -> personnel
                    rule(EnumCategorie.NON_PROF_COL_LOCAL,null, null, null)),
            true, false, false, false, true),

    /**
     * Entreprises, tuteurs de stage…
     */
    EXTERIEUR(
            rules(
                    rule(EnumCategorie.ENTREPRISE,null, null, null),
                    rule(EnumCategorie.TUTEUR,null, null, null)),
            true, false, false, false, false),

    /**
     * Le reste : toute personne non reconnue par les profils précédents.
     */
    AUTRE(
            rules(rule(EnumCategorie.NON_PROF_ACAD,null, null, null)),
            true, false, false, false, false);

    @Getter
    private final Rule[] rules;
    private final boolean authLocale;
    private final boolean eleve;
    private final boolean parent;
    private final boolean educonnect;
    private final boolean passEtab;
    EnumPublic(Rule[] rules, boolean authLocale, boolean eleve, boolean parent, boolean educonnect, boolean passEtab) {
        this.rules = rules;
        this.authLocale = authLocale;
        this.eleve = eleve;
        this.parent = parent;
        this.educonnect = educonnect;
        this.passEtab = passEtab;
    }

    /**
     * Regroupe les règles d'un profil.
     */
    private static Rule[] rules(Rule... rules) {
        return rules;
    }

    /**
     * Crée une règle contraignant la catégorie, la source de la structure, le caractère local de l'authentification et la collectivité
     */
    private static Rule rule(EnumCategorie categorie, DomSource source, Boolean local, Boolean collectivite) {
        return new Rule(categorie, source, local, collectivite);
    }

    /**
     * Résout le profil d'une personne à partir de ses caractéristiques
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

}
