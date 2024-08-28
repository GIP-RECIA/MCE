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
package fr.recia.mce.api.escomceapi.db.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fonction")
@Getter
@Setter
public class Fonction implements Serializable {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discipline_poste_fk")
    private Discipline discipline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filiere_fk")
    private TypeFonctionFiliere typeFonctionFiliere;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "astructure_fk")
    private AStructure aStructure;

    public Fonction() {
    }

    public Fonction(long id) {
        this.id = id;
    }

    public Fonction(long id, Discipline discipline,
            TypeFonctionFiliere typeFonctionFiliere, AStructure aStructure) {
        this.id = id;
        this.discipline = discipline;
        this.typeFonctionFiliere = typeFonctionFiliere;
        this.aStructure = aStructure;
    }
}
