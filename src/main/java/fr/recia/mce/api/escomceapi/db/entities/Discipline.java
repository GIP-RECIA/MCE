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

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "discipline", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
public class Discipline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "disciplinePoste")
    private String disciplinePoste;

    @Column(name = "source", length = 60)
    private String source;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "discipline")
    private Set<Fonction> fonctions = new HashSet<Fonction>(0);

    public Discipline() {
    }

    public Discipline(String code, String disciplinePoste, String source,
            Set<Fonction> fonctions) {
        this.code = code;
        this.disciplinePoste = disciplinePoste;
        this.source = source;
        this.fonctions = fonctions;
    }
}
