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
     * personnel de la region centre val de loir!
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
     * les eleves educ nat
     */
    ELEVE_EDUC,
    /**
     * les eleves agri non cfa
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

    /**
     * Public donnant droit de se connecter.
     * les autres doivent passer par cas.
     */
    public boolean isConnectOk() {
        switch (this) {
            case EDUCATION:
            case AGRI:
            case CVDL:
            case ELEVE_EDUC:
            case PARENT_EDUC:
                return false;
            // $CASES-OMITTED$
            default:
                return true;
        }
    }

    public boolean isEleve() {
        switch (this) {
            case ELEVE_EDUC:
            case ELEVE:
            case APPRENANT:
                return true;
            default:
                return false;
        }

    }

    public boolean isParent() {
        switch (this) {
            case PARENT:
            case PARENT_EDUC:
                return true;
            default:
                return false;
        }
    }

    public boolean isEduconnect() {
        switch (this) {
            case PARENT_EDUC:
            case ELEVE_EDUC:
                return true;
            default:
                return false;
        }
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
                return true;
            default:
                return false;
        }
    }
}
