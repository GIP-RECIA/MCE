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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Entity
@Table(name = "apersonne", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"source", "cle"}),
        @UniqueConstraint(columnNames = "uid")})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
public class APersonne implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    @JsonIgnore
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @JsonIgnore
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_rattachement_fk")
    @JsonIgnore
    private AStructure aStructure;

    @Column(name = "dateAcquittement", length = 19)
    private Date dateAcquittement;

    @Column(name = "dateCreation")
    private Date dateCreation;

    @Column(name = "dateModification", length = 19)
    private Date dateModification;

    @Column(name = "dateSourceModification")
    private Date dateSourceModification;

    @PreUpdate
    private void onUpdate() {
        this.dateSourceModification = new Date();
    }

    @Column(name = "anneeScolaire", length = 10)
    private Date anneeScolaire;

    @Column(name = "categorie")
    private String categorie;

    @Column(name = "civilite")
    private String civilite;

    @Column(name = "cle")
    @JsonIgnore
    private String cle;

    @Column(name = "source")
    private String source;

    @Column(name = "cn")
    private String cn;

    @Column(name = "dateNaissance", length = 10)
    private Date dateNaissance;

    @Column(name = "displayName")
    private String displayName;

    @Column(name = "email")
    private String email;

    @Column(name = "emailPersonnel")
    private String emailPersonnel;

    @Column(name = "etat")
    private String etat;

    @Column(name = "givenName")
    private String givenName;

    @Column(name = "password")
    @JsonIgnore
    private String password;

    @Column(name = "sn")
    private String sn;

    @Column(name = "titre")
    private String titre;

    @Column(name = "uid")
    @JsonIgnore
    private String uid;

    @Column(name = "validationCharte")
    private Date validationCharte;

    @Column(name = "doForward")
    private boolean doForward;

    @Column(name = "sambaLMPassword")
    @JsonIgnore
    private String sambaLmpassword;

    @Column(name = "sambaNTPassword")
    @JsonIgnore
    private String sambaNtpassword;

    @Column(name = "photo")
    private String photo;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByAPersonneLogin")
    @JsonIgnore
    private Set<Login> loginsForApersonneLogin = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByAPersonneAlias")
    @JsonIgnore
    private Set<Login> loginsForApersonneAlias = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByIdParent")
    @JsonIgnore
    private Set<CerbereEnfant> cerbereEnfantsForIdParent = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByIdEnfant")
    @JsonIgnore
    private Set<CerbereEnfant> cerbereEnfantsForIdEnfant = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonne")
    @JsonIgnore
    private Set<CerbereConfirmation> cerbereConfirmations = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByAPersonneLogin")
    @JsonIgnore
    private Set<Login> loginsForApersonneOldAlias = new HashSet<>(0);

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonne")
    private Set<CerberePassword> cerberePasswords = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByResponsableId")
    @JsonIgnore
    private Set<AStructure> astructuresForResponsableId = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByContactId")
    @JsonIgnore
    private Set<AStructure> astructuresForContactId = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByContactId")
    @JsonIgnore
    private Set<AStructure> astructuresForContactId_1 = new HashSet<>(0);

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByResponsableId")
    @JsonIgnore
    private Set<AStructure> astructuresForResponsableId_1 = new HashSet<>(0);


}
