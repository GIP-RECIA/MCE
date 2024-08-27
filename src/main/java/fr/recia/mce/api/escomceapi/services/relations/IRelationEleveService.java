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
package fr.recia.mce.api.escomceapi.services.relations;

import java.util.Collection;
import java.util.List;

import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;

public interface IRelationEleveService {

    /**
     * Donne tous les parents en relations avec un élèves.
     * Donne aussi les maitres de stage de l'élèves.
     * Si la personne donnée n'est pas un élève renvoie null ou vide.
     * 
     * @param eleve
     * @return La collection des relations de l'eleve.
     */
    public Collection<RelationEleveContact> allRelationEleves(String eleve);

    /**
     * Donne tous les élèves en relation avec un parent.
     * Si la personne donnée n'est pas un parent renvoie null ou vide.
     * 
     * @param parent
     * @return La collection des relations du parent
     */
    public Collection<RelationEleveContact> allEleveEnRelation(Long parent);

    /**
     * Donne tous les apprentis d'un maitre d'apprentissage.
     * Si la personne n'est pas un maitre d'apprentissage ou n'a pas d'apprenti
     * renvoie null ou vide.
     * 
     * @param maitre
     * @return
     */
    List<RelationEleveContact> allApprentiEnRelation(String maitre);
}
