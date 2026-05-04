package fr.recia.mce.api.escomceapi.db.entities;

import java.util.Date;
import javax.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cerbere_password", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"idPersonne", "debut"})
})
@Getter
@Setter
public class CerberePassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idPersonne", nullable = false)
    private APersonne aPersonne;

    @Column(name = "password", nullable = false)
    private String password;

    @Temporal(TemporalType.DATE)
    @Column(name = "debut", nullable = false)
    private Date debut;

    @Temporal(TemporalType.DATE)
    @Column(name = "fin")
    private Date fin;

    public CerberePassword() {}

    public CerberePassword(APersonne aPersonne, String password, Date debut) {
        this.aPersonne = aPersonne;
        this.password = password;
        this.debut = debut;
    }
}