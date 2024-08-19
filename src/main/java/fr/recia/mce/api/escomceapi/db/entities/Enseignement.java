package fr.recia.mce.api.escomceapi.db.entities;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "enseignement")
@Getter
@Setter
public class Enseignement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "matiere", length = 128)
    private String matiere;

    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "code", length = 20)
    private String code;

    @Temporal(TemporalType.DATE)
    @Column(name = "anneeScolaire", length = 10)
    private Date anneScolaire;

    public Enseignement() {

    }

    public Enseignement(final String matiere, final String source) {
        this.matiere = matiere;
        this.source = source;
    }

    public Enseignement(final String matiere, final String source, final String code) {
        this.matiere = matiere;
        this.source = source;
        this.code = code;
    }
}
