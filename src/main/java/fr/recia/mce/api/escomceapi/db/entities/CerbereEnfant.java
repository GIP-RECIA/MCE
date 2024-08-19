package fr.recia.mce.api.escomceapi.db.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cerbere_enfant")
@Getter
@Setter
public class CerbereEnfant implements Serializable {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private byte[] id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idParent")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private APersonne aPersonneByIdParent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idEnfant")
    private APersonne aPersonneByIdEnfant;

    @Column(name = "typeRelation")
    private String typeRelation;

    @Column(name = "lienParente")
    private String lienParente;

    public CerbereEnfant() {
    }

    public CerbereEnfant(final byte[] id) {
        this.id = id;
    }

    public CerbereEnfant(final byte[] id, final APersonne aPersonneByIdParent,
            final APersonne aPersonneByIdEnfant, final String typeRelation, final String lienParente) {
        this.id = id;
        this.aPersonneByIdParent = aPersonneByIdParent;
        this.aPersonneByIdEnfant = aPersonneByIdEnfant;
        this.typeRelation = typeRelation;
        this.lienParente = lienParente;
    }

}
