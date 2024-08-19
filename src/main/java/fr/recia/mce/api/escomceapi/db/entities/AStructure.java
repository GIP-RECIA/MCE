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

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "astructure", uniqueConstraints = {
        @UniqueConstraint(columnNames = "siren"),
        @UniqueConstraint(columnNames = { "source", "cle" }),
        @UniqueConstraint(columnNames = "nom") })
@Getter
@Setter
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
    private APersonne aPersonneByContactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private APersonne aPersonneByResponsableId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateAcquittement")
    private Date dateAcquittement;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateCreation")
    private Date dateCreation;

    @Temporal(TemporalType.TIMESTAMP)
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

    @Temporal(TemporalType.DATE)
    @Column(name = "anneeScolaire", length = 10)
    private Date anneeScolaire;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "aStructure")
    private Set<APersonne> aPersonnes = new HashSet<>(0);

    // @OneToMany(fetch = FetchType.LAZY, mappedBy = "astructure")
    // private Set<APersonne> aPersonnes_1 = new HashSet<>(0);

    public AStructure() {
    }

    public AStructure(final APersonne apersonneByContactId,
            final APersonne apersonneByResponsableId, final Date dateAcquittement,
            final Date dateCreation, final Date dateModification, final String categorie,
            final String cle, final String source, final String etat, final String modeleLogin,
            final String nom, final String siren, final String siteWeb, final Long typeStructureFk,
            final Date anneeScolaire, final Set<APersonne> apersonnes) {
        this.aPersonneByContactId = apersonneByContactId;
        this.aPersonneByResponsableId = apersonneByResponsableId;
        this.dateAcquittement = dateAcquittement;
        this.dateCreation = dateCreation;
        this.dateModification = dateModification;
        this.categorie = categorie;
        this.cle = cle;
        this.source = source;
        this.etat = etat;
        this.nom = nom;
        this.siren = siren;
        this.siteWeb = siteWeb;
        this.typeStructureFk = typeStructureFk;
        this.anneeScolaire = anneeScolaire;
        this.aPersonnes = apersonnes;

    }
}
