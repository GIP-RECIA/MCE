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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "afonction")
@Getter
@Setter
public class AFonction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personne_fk")
    @JsonIgnore
    private APersonne aPersonne;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateAcquittement", length = 19)
    private Date dateAcquittement;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateCreation", length = 19)
    private Date dateCreation;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateModification", length = 19)
    private Date dateModification;

    @Column(name = "categorie")
    private String categorie;

    @Column(name = "source")
    private String source;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateFin", length = 19)
    private Date dateFin;

    public AFonction() {

    }

    public AFonction(APersonne aPersonne, Date dateAcquittement, Date dateCreation, Date dateModification,
            String categorie, String source, Date dateFin) {
        this.aPersonne = aPersonne;
        this.dateAcquittement = dateAcquittement;
        this.dateCreation = dateCreation;
        this.dateModification = dateModification;
        this.categorie = categorie;
        this.source = source;
        this.dateFin = dateFin;
    }

}
