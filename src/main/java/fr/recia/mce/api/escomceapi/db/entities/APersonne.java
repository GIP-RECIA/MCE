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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "apersonne", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "source", "cle" }),
		@UniqueConstraint(columnNames = "uid") })
@Getter
@Setter
public class APersonne implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", unique = true, nullable = false)
	private Long id;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "structure_rattachement_fk")
	@JsonIgnore
	private AStructure aStructure;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "dateAcquittement", length = 19)
	private Date dateAcquittement;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "dateCreation")
	private Date dateCreation;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "dateModification", length = 19)
	private Date dateModification;

	@Temporal(TemporalType.DATE)
	@Column(name = "anneeScolaire", length = 10)
	private Date anneeScolaire;

	@Column(name = "categorie")
	private String categorie;

	@Column(name = "civilite")
	private String civilite;

	@Column(name = "cle")
	private String cle;

	@Column(name = "source")
	private String source;

	@Column(name = "cn")
	private String cn;

	@Temporal(TemporalType.DATE)
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
	private String password;

	@Column(name = "sn")
	private String sn;

	@Column(name = "titre")
	private String titre;

	@Column(name = "uid")
	private String uid;

	@Temporal(TemporalType.DATE)
	@Column(name = "validationCharte", length = 10)
	private Date validationCharte;

	@Column(name = "doForward")
	private boolean doForward;

	@Column(name = "sambaLMPassword")
	private String sambaLmpassword;

	@Column(name = "sambaNTPassword")
	private String sambaNtpassword;

	@Column(name = "photo")
	private String photo;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByAPersonneLogin")
	@JsonIgnore
	private Set<Login> loginsForApersonneLogin = new HashSet<Login>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByAPersonneAlias")
	@JsonIgnore
	private Set<Login> loginsForApersonneAlias = new HashSet<Login>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByIdParent")
	@JsonIgnore
	private Set<CerbereEnfant> cerbereEnfantsForIdParent = new HashSet<CerbereEnfant>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByIdEnfant")
	@JsonIgnore
	private Set<CerbereEnfant> cerbereEnfantsForIdEnfant = new HashSet<CerbereEnfant>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonne")
	private Set<CerbereConfirmation> cerbereConfirmations = new HashSet<CerbereConfirmation>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByAPersonneLogin")
	@JsonIgnore
	private Set<Login> loginsForApersonneOldAlias = new HashSet<Login>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByResponsableId")
	private Set<AStructure> astructuresForResponsableId = new HashSet<AStructure>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByContactId")
	private Set<AStructure> astructuresForContactId = new HashSet<AStructure>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByContactId")
	private Set<AStructure> astructuresForContactId_1 = new HashSet<AStructure>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "aPersonneByResponsableId")
	private Set<AStructure> astructuresForResponsableId_1 = new HashSet<AStructure>(0);

	public APersonne() {
	}

	public APersonne(boolean doForward) {
		this.doForward = doForward;
	}

	public APersonne(AStructure astructure, Date dateAcquittement,
			Date dateCreation, Date dateModification, Date anneeScolaire,
			String categorie, String civilite, String cle, String source,
			String cn, Date dateNaissance, String displayName, String email,
			String emailPersonnel, String etat, String givenName,
			String password, String sn, String titre, String uid,
			Date validationCharte, boolean doForward, String sambaLmpassword,
			String sambaNtpassword,
			String photo,
			Set<Login> loginsForApersonneLogin,
			Set<Login> loginsForApersonneAlias,
			Set<CerbereEnfant> cerbereEnfantsForIdParent,
			Set<CerbereEnfant> cerbereEnfantsForIdEnfant,
			Set<CerbereConfirmation> cerbereConfirmations,
			Set<Login> loginsForApersonneOldAlias,
			Set<AStructure> astructuresForResponsableId,
			Set<AStructure> astructuresForContactId,
			Set<AStructure> astructuresForContactId_1,
			Set<AStructure> astructuresForResponsableId_1) {
		this.aStructure = astructure;
		this.dateAcquittement = dateAcquittement;
		this.dateCreation = dateCreation;
		this.dateModification = dateModification;
		this.anneeScolaire = anneeScolaire;
		this.categorie = categorie;
		this.civilite = civilite;
		this.cle = cle;
		this.source = source;
		this.cn = cn;
		this.dateNaissance = dateNaissance;
		this.displayName = displayName;
		this.email = email;
		this.emailPersonnel = emailPersonnel;
		this.etat = etat;
		this.givenName = givenName;
		this.password = password;
		this.sn = sn;
		this.titre = titre;
		this.uid = uid;
		this.validationCharte = validationCharte;
		this.doForward = doForward;
		this.sambaLmpassword = sambaLmpassword;
		this.sambaNtpassword = sambaNtpassword;
		this.photo = photo;
		this.loginsForApersonneLogin = loginsForApersonneLogin;
		this.loginsForApersonneAlias = loginsForApersonneAlias;
		this.cerbereEnfantsForIdParent = cerbereEnfantsForIdParent;
		this.cerbereEnfantsForIdEnfant = cerbereEnfantsForIdEnfant;
		this.cerbereConfirmations = cerbereConfirmations;
		this.loginsForApersonneOldAlias = loginsForApersonneOldAlias;
		this.astructuresForResponsableId = astructuresForResponsableId;
		this.astructuresForContactId = astructuresForContactId;
		this.astructuresForContactId_1 = astructuresForContactId_1;
		this.astructuresForResponsableId_1 = astructuresForResponsableId_1;
	}

}
