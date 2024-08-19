package fr.recia.mce.api.escomceapi.db.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import lombok.Getter;
import lombok.Setter;

@Entity
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
