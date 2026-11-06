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
     * personnel de l'education nationnal (prof et autre).
     */
    EDUCATION,
    /**
     * personnel enseignement agricole authentifié par l'enseignement agricole.
     */
    AGRI,
    /**
     * personnel de la region centre val de loire
     * ¡attention il y a aussi les personnes des collectivites sans controle!
     */
    CVDL,
    /**
     * personnel non education nationnal non agri.
     */
    PERSONNEL,
    /**
     * Les parents
     */
    PARENT,
    /**
     * Les parents educ nat
     */
    PARENT_EDUC,
    /**
     * les parents agri
     */
    PARENT_AGRI,
    /**
     * les eleves educ nat
     */
    ELEVE_EDUC,
    /**
     * les eleves agri non cfa
     */
    ELEVE_AGRI,
    /**
     * les eleves autre (localuser par exemple)
     */
    ELEVE,
    /**
     * les eleves cfa
     */
    APPRENANT,
    /**
     * Entreprise , tuteur ...
     */
    EXTERIEUR,
    /**
     * le reste
     */
    AUTRE;


    public boolean isAuthLocale() {
        return switch (this) {
            case EDUCATION, AGRI, CVDL, ELEVE_EDUC, PARENT_EDUC, ELEVE_AGRI, PARENT_AGRI -> false;
            default -> true;
        };
    }

    public boolean isEleve(){
        return switch (this) {
            case ELEVE_EDUC, ELEVE, ELEVE_AGRI, APPRENANT -> true;
            default -> false;
        };

    }

    public boolean isParent(){
        return switch (this) {
            case PARENT, PARENT_EDUC, PARENT_AGRI -> true;
            default -> false;
        };
    }

    public boolean isEduconnect(){
        return switch (this) {
            case PARENT_EDUC, ELEVE_EDUC -> true;
            default -> false;
        };
    }

    /**
     * Donne les populations pouvant changer leurs mots de passe établissement.
     * ne suffit pas a donner le liens il faut par ailleur verifier le périmetre
     * (sont'il géré par le gip)
     */
    public boolean isPassEtab() {
        switch (this) {
            case EDUCATION:
            case AGRI:
            case CVDL:
            case PERSONNEL:
            case ELEVE_EDUC:
            case APPRENANT:
            case ELEVE:
            case ELEVE_AGRI:
                return true;
            default:
                return false;
        }
    }


    /**
     * Résout le profil d'une personne à partir de ses caractéristiques
     */
    public static EnumPublic resolve(EnumCategorie categorie, DomSource source, boolean local, boolean isCollViaCas) {
        switch (categorie){
            case ELEVE:
                return switch (source) {
                    case CFA -> EnumPublic.APPRENANT;
                    case AC -> local ? EnumPublic.ELEVE : EnumPublic.ELEVE_EDUC;
                    case LA -> local ? EnumPublic.ELEVE : EnumPublic.ELEVE_AGRI;
                    default -> EnumPublic.ELEVE;
                };

            case PARENT:
                return switch (source) {
                    case AC -> local ? EnumPublic.PARENT : EnumPublic.PARENT_EDUC;
                    case LA -> local ? EnumPublic.PARENT : EnumPublic.PARENT_AGRI;
                    default -> EnumPublic.PARENT;
                };

            case PROF:
                return switch (source) {
                    case AC -> local ? EnumPublic.PERSONNEL : EnumPublic.EDUCATION;
                    case LA -> local ? EnumPublic.PERSONNEL : EnumPublic.AGRI;
                    default -> EnumPublic.PERSONNEL;
                };

            case ENTREPRISE, TUTEUR:
                return EnumPublic.EXTERIEUR;

            case NON_PROF_COL_LOCAL:
                if (isCollViaCas) {
                    return local ? EnumPublic.PERSONNEL : EnumPublic.CVDL;
                }
                return AUTRE;

            case NON_PROF_ETAB:
                return switch (source) {
                    case AC -> local ? EnumPublic.PERSONNEL : EnumPublic.EDUCATION;
                    case LA -> local ? EnumPublic.PERSONNEL : EnumPublic.AGRI;
                    default -> EnumPublic.PERSONNEL;
                };

            case NON_PROF_ACAD:
                return switch (source) {
                    case AC -> local ? EnumPublic.PERSONNEL : EnumPublic.EDUCATION;
                    case LA -> local ? EnumPublic.PERSONNEL : EnumPublic.AGRI;
                    default -> EnumPublic.AUTRE;
                };
        }

        return AUTRE;
    }

}
