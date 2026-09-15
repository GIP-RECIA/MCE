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
package fr.recia.mce.api.escomceapi.db.repositories;

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.entities.AFonction;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.db.entities.Discipline;
import fr.recia.mce.api.escomceapi.db.entities.Fonction;
import fr.recia.mce.api.escomceapi.db.entities.TypeFonctionFiliere;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
@DisplayName("Tests - FonctionRepository (flag active / dateFinSource)")
class FonctionRepositoryTest {

    @MockBean
    private LdapTemplate ldapTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private FonctionRepository repository;

    @Autowired
    private TestEntityManager em;

    private APersonne personne;
    private AStructure structure;
    private Discipline discipline;
    private TypeFonctionFiliere type;

    private void baseline() {
        APersonne p = new APersonne();
        p.setUid("fct-" + System.nanoTime());
        p.setSource("test");
        p.setCle("cle-" + System.nanoTime());
        p.setVersion(0L);
        em.persistAndFlush(p);

        structure = new AStructure();
        structure.setSource("test");
        structure.setCle("cle-" + System.nanoTime());
        structure.setNom("etablissement-" + System.nanoTime());
        structure.setSiren("siren-" + System.nanoTime());
        structure.setEtat("actif");
        structure.setVersion(0L);
        em.persistAndFlush(structure);

        discipline = new Discipline();
        discipline.setCode("code-" + System.nanoTime());
        discipline.setDisciplinePoste("maths");
        discipline.setSource("test");
        em.persistAndFlush(discipline);

        type = new TypeFonctionFiliere();
        type.setCodeFiliere("F-" + System.nanoTime() % 100000);
        type.setLibelleFiliere("professeur");
        type.setSource("test");
        em.persistAndFlush(type);

        personne = p;
    }

    private AFonction newAFonction() {
        AFonction a = new AFonction();
        a.setAPersonne(personne);
        a.setCategorie("ENS");
        a.setSource("test");
        a.setVersion(0L);
        em.persistAndFlush(a);

        Fonction f = new Fonction(a.getId(), discipline, type, structure);
        em.persistAndFlush(f);
        em.flush();
        return a;
    }

    private FonctionDTO getActiveFor(AFonction a) {
        Collection<FonctionDTO> result = repository.findAllFonction(personne.getId());
        for (FonctionDTO dto : result) {
            if (dto.getIdFonction().equals(a.getId())) {
                return dto;
            }
        }
        return null;
    }

    private Date future(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTime();
    }

    @Test
    @DisplayName("dateFinSource passée → active=false (non affiché)")
    void dateFinSourcePast_isInactive() {
        baseline();
        AFonction a = newAFonction();
        a.setDateFinSource(future(-30));
        em.persistAndFlush(a);

        assertThat(getActiveFor(a)).isNotNull();
        assertThat(getActiveFor(a).isActive()).isFalse();
    }

    @Test
    @DisplayName("dateFinSource future → active=true (affiché)")
    void dateFinSourceFuture_isActive() {
        baseline();
        AFonction a = newAFonction();
        a.setDateFinSource(future(30));
        em.persistAndFlush(a);

        assertThat(getActiveFor(a)).isNotNull();
        assertThat(getActiveFor(a).isActive()).isTrue();
    }

    @Test
    @DisplayName("dateFinSource null → active=true (fonction sans bornes)")
    void dateFinSourceNull_isActive() {
        baseline();
        AFonction a = newAFonction();

        assertThat(getActiveFor(a)).isNotNull();
        assertThat(getActiveFor(a).isActive()).isTrue();
    }

    @Test
    @DisplayName("règle existante conservée : dateFin passé → active=false")
    void dateFinPast_isInactive() {
        baseline();
        AFonction a = newAFonction();
        a.setDateFin(future(-2));
        em.persistAndFlush(a);

        assertThat(getActiveFor(a)).isNotNull();
        assertThat(getActiveFor(a).isActive()).isFalse();
    }

    @Test
    @DisplayName("règle existante conservée : dateDebut futur → active=false")
    void dateDebutFuture_isInactive() {
        baseline();
        AFonction a = newAFonction();
        a.setDateDebut(future(30));
        em.persistAndFlush(a);

        assertThat(getActiveFor(a)).isNotNull();
        assertThat(getActiveFor(a).isActive()).isFalse();
    }

    @Test
    @DisplayName("plusieurs fonctions : le flag active est calculé par fonction")
    void mixedFonctions_havePerFunctionActiveFlag() {
        baseline();
        AFonction a1 = newAFonction();
        a1.setDateFinSource(future(30));
        em.persistAndFlush(a1);

        AFonction a2 = newAFonction();
        a2.setDateFinSource(future(-10));
        em.persistAndFlush(a2);

        AFonction a3 = newAFonction();
        em.persistAndFlush(a3);

        Collection<FonctionDTO> result = repository.findAllFonction(personne.getId());

        assertThat(result)
                .extracting(FonctionDTO::getIdFonction, FonctionDTO::isActive)
                .contains(
                        tuple(a1.getId(), true),
                        tuple(a2.getId(), false),
                        tuple(a3.getId(), true));
    }
}