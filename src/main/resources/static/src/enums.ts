/**
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

export interface CategorieProfil {
  name: string;
  dbName: string;
}

export const CATEGORIES_PROFIL: CategorieProfil[] = [
  { name: 'ELEVE', dbName: 'Eleve' },
  { name: 'PARENT', dbName: 'Personne_relation_eleve' },
  { name: 'PROF', dbName: 'Enseignant' },
  { name: 'NON_PROF_ACAD', dbName: 'Non_enseignant_service_academique' },
  { name: 'NON_PROF_ETAB', dbName: 'Non_enseignant_etablissement' },
  { name: 'NON_PROF_COL_LOCAL', dbName: 'Non_enseignant_collectivite_locale' },
  { name: 'ENTREPRISE', dbName: 'Responsable_Entreprise' },
  { name: 'TUTEUR', dbName: 'Tuteur_stage' }
];

export const TYPES_ETABLISSEMENT = [
  'LYCEE', 'COLLEGE', 'CFA', 'ETABLISSEMENT', 'INSPECTION'
] as const;

export function categorieProfilName(value: string): string | undefined {
  const entry = CATEGORIES_PROFIL.find((c) => c.name === value || c.dbName === value);
  return entry?.name;
}
