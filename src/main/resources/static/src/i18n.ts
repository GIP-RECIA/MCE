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

import { categorieProfilName } from './enums';

const LABELS_PROFIL: Record<string, string> = {
  ELEVE: 'Eleve',
  PARENT: 'Parent',
  PROF: 'Enseignant',
  NON_PROF_ACAD: 'Personnel non enseignant éducation nationale.',
  NON_PROF_ETAB: 'Personnel non enseignant, établissement',
  NON_PROF_COL_LOCAL: 'Personnel non Enseignant, collectivité locale',
  ENTREPRISE: 'Entreprise',
  TUTEUR: 'Tuteur'
};

const LABELS_TYPE_ETABLISSEMENT: Record<string, string> = {
  LYCEE: 'Lycée',
  COLLEGE: 'Collège',
  CFA: 'CFA',
  ETABLISSEMENT: "Etablissement régional d'enseignement adapté",
  INSPECTION: 'Inspection Académique'
};

export function profilLabel(value: string): string {
  const name = categorieProfilName(value) ?? value;
  return LABELS_PROFIL[name] ?? value;
}

export function typeEtablissementLabel(value: string): string {
  return LABELS_TYPE_ETABLISSEMENT[value.toUpperCase()] ?? value;
}
