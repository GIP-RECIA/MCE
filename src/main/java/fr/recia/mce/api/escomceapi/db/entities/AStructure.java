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
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Entity
@Table(name = "astructure", uniqueConstraints = {
        @UniqueConstraint(columnNames = "siren"),
        @UniqueConstraint(columnNames = {"source", "cle"}),
        @UniqueConstraint(columnNames = "nom")})
@Data
public class AStructure implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    @JsonIgnore
    private APersonne aPersonneByContactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    @JsonIgnore
    private APersonne aPersonneByResponsableId;

    @Column(name = "dateAcquittement")
    private Date dateAcquittement;

    @Column(name = "dateCreation")
    private Date dateCreation;

    @Column(name = "dateModification", length = 19)
    private Date dateModification;

    @Column(name = "categorie")
    private String categorie;

    @Column(name = "cle")
    private String cle;

    @Column(name = "source")
    private String source;

    @Column(name = "etat")
    private String etat;

    @Column(name = "nom", unique = true)
    private String nom;

    @Column(name = "siren", unique = true)
    private String siren;

    @Column(name = "siteweb")
    private String siteWeb;

    @Column(name = "type_structure_fk")
    private Long typeStructureFk;

    @Column(name = "anneeScolaire", length = 10)
    private Date anneeScolaire;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aStructure")
    @JsonIgnore
    private Set<APersonne> aPersonnes = new HashSet<>(0);

}
