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

package fr.recia.manager.ldap;

public final class ConstLDAP {

    /* Attributs */
    public static final String IS_MEMBER_OF = "isMemberOf";
    public static final String OBJECT_CLASS = "objectClass";
    public static final String UID = "uid";
    public static final String DISPLAY_NAME = "displayName";
    public static final String MAIL = "mail";
    public static final String PHOTO = "ESCOPersonPhoto";
    public static final String PROFILS = "ENTPersonProfils";
    public static final String ETAT_COMPTE = "ESCOPersonEtatCompte";
    public static final String ESCOUAI = "ESCOUAI";
    public static final String USER_PASSWORD = "userPassword";
    public static final String SAMBA_LM_PASSWORD = "sambaLMPassword";
    public static final String SAMBA_NT_PASSWORD = "sambaNTPassword";
    /* Spécifiques élève */
    public static final String ELEVE_RELATION = "ENTElevePersRelEleve";
    public static final String ELEVE_ENSEIGNEMENTS = "ENTEleveEnseignements";
    public static final String ELEVE_CLASSES = "ENTEleveClasses";
    public static final String ELEVE_GROUPES = "ENTEleveGroupes";
    /* Spécifiques enseignants */
    public static final String ENS_CLASSES_MATIERES = "ENTAuxEnsClassesMatieres";
    public static final String ENS_CLASSES_MATIERES_ETAB = "ESCOAuxEnsCodeMatiereEnseignEtab";
    public static final String ENS_GROUPES_MATIERES = "ENTAuxEnsGroupesMatieres";
    /* Spécifiques tuteurs */
    public static final String TUTEUR_ELVES = "ENTAuxTuteurStageEleves";
    /* Structures */
    public static final String STRUCT_DOMAINES = "ESCODomaines";
    public static final String STRUCT_NOM_COURANT = "ENTStructureNomCourant";
    public static final String STRUCT_JOINTURE = "ENTStructureJointure";
    public static final String STRUCT_TYPE = "ENTStructureTypeStruct";
    public static final String STRUCT_SIREN = "ENTStructureSIREN";
    public static final String STRUCT_UAI = "ENTStructureUAI";
    public static final String STRUCT_VILLE = "l";

    /* Branches */
    public static final String PEOPLE_BRANCH = "ou=people";
    public static final String STRUCTURE_BRANCH = "ou=structures";

    /* Valeurs */
    /* ObjectClasses */
    public static final String OC_TUTEUR_STAGE = "ENTAuxTuteurStage";
    /* Profils */
    public static final String PROFIL_ENS = "National_ENS";
    /* Source locale */
    public static final String SOURCE_LOCALE = "SarapisUi_";
    public static final String SOURCE_CVDL = "COLL-CVDL";
    public static final String SOURCE_CD28 = "COLL-CD28";
    public static final String SOURCE_RREUNION = "COLL-RREUNION";
    /* Password */
    public static final String ACTIVE_WITHOUT_PASSWORD = "{SSHA}Active" + "=".repeat(50);
    /* Type de structure */
    public static final String COLLECTIVITE_LOCALE = "COLLECTIVITE LOCALE";


}
