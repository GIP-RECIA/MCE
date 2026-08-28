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

import lombok.Getter;

@Getter
public enum EnumCategorie {

    PARENT("Personne_relation_eleve"), ELEVE("Eleve"), PROF("Enseignant"), NON_PROF_ACAD("Non_enseignant_service_academique"), NON_PROF_ETAB(
            "Non_enseignant_etablissement"), NON_PROF_COL_LOCAL(
                    "Non_enseignant_collectivite_locale"), ENTREPRISE("Responsable_Entreprise"), TUTEUR("Tuteur_stage");

    private String dbname;

    private EnumCategorie(String dbname) {
        this.dbname = dbname.intern();
    }

    public static EnumCategorie fromString(String dbname) {
        if (dbname == null)
            return null;
        String val = dbname.intern();
        for (EnumCategorie e : EnumCategorie.values()) {
            if (e.dbname.equals(val))
                return e;
        }
        return null;
    }

    /**
     * Résout un profil reçu du front vers son {@link EnumCategorie}, qu'il soit fourni sous forme de nom d'enum ({@code NON_PROF_ACAD}) ou de valeur en base (
     * {@code Non_enseignant_collectivite_locale}).
     *
     * @param input
     *            profil envoyé par le front (nom d'enum ou dbname), insensible à la casse
     * @return l'enum correspondant, ou {@code null} si le profil n'est pas reconnu
     */
    public static EnumCategorie fromProfile(String input) {
        if (input == null || input.isBlank())
            return null;
        String val = input.intern();
        for (EnumCategorie e : EnumCategorie.values()) {
            if (e.name().equalsIgnoreCase(val) || e.dbname.equalsIgnoreCase(val))
                return e;
        }
        return null;
    }

}
