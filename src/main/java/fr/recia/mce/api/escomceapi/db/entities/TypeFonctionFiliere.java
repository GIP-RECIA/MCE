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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {
        "codeFiliere", "source" }))
@Getter
@Setter
public class TypeFonctionFiliere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "codeFiliere", nullable = false, length = 10)
    private String codeFiliere;

    @Column(name = "libelleFiliere")
    private String libelleFiliere;

    @Column(name = "source", nullable = false, length = 60)
    private String source;

    @OneToMany(fetch = FetchType.LAZY)
    private Set<Fonction> fonctions = new HashSet<>(0);

    public TypeFonctionFiliere() {

    }

    public TypeFonctionFiliere(String codeFiliere, String source) {
        this.codeFiliere = codeFiliere;
        this.source = source;
    }

    public TypeFonctionFiliere(String codeFiliere, String libelleFiliere,
            String source, Set<Fonction> fonctions) {
        this.codeFiliere = codeFiliere;
        this.libelleFiliere = libelleFiliere;
        this.source = source;
        this.fonctions = fonctions;
    }

}
