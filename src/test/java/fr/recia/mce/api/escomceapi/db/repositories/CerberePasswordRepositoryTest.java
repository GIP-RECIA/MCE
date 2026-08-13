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

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerberePassword;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
@DisplayName("Tests - CerberePasswordRepository (native query)")
class CerberePasswordRepositoryTest {

    @MockBean
    private LdapTemplate ldapTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private CerberePasswordRepository repository;

    @Autowired
    private TestEntityManager em;

    private APersonne createPersonne() {
        APersonne p = new APersonne();
        p.setUid("test-" + System.nanoTime());
        p.setSource("test");
        p.setCle("cle-" + System.nanoTime());
        p.setVersion(0L);
        return p;
    }

    @Test
    @DisplayName("findByAPersonne : ordre DESC par debut")
    void findByAPersonne_ordersByDebutDesc() {
        APersonne p = createPersonne();
        em.persistAndFlush(p);

        Calendar cal = Calendar.getInstance();

        CerberePassword old = new CerberePassword();
        old.setAPersonne(p);
        old.setPassword("{ARGON2}oldHash");
        cal.add(Calendar.DAY_OF_YEAR, -2);
        old.setDebut(cal.getTime());
        em.persistAndFlush(old);

        CerberePassword recent = new CerberePassword();
        recent.setAPersonne(p);
        recent.setPassword("{ARGON2}recentHash");
        cal.add(Calendar.DAY_OF_YEAR, 1);
        recent.setDebut(cal.getTime());
        em.persistAndFlush(recent);

        List<CerberePassword> result = repository.findByAPersonne(p);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPassword()).isEqualTo("{ARGON2}recentHash");
        assertThat(result.get(1).getPassword()).isEqualTo("{ARGON2}oldHash");
    }

    @Test
    @DisplayName("findByAPersonne : retourne liste vide si aucun mot de passe")
    void findByAPersonne_returnsEmptyWhenNone() {
        APersonne p = createPersonne();
        em.persistAndFlush(p);

        List<CerberePassword> result = repository.findByAPersonne(p);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updatePasswordForToday : met à jour l'entrée du jour")
    void updatePasswordForToday_updatesTodayEntry() {
        APersonne p = createPersonne();
        em.persistAndFlush(p);

        CerberePassword cp = new CerberePassword();
        cp.setAPersonne(p);
        cp.setPassword("{ARGON2}oldHash");
        cp.setDebut(new Date());
        em.persistAndFlush(cp);

        repository.updatePasswordForToday(p.getId(), "{ARGON2}updatedHash");

        em.clear();

        List<CerberePassword> result = repository.findByAPersonne(p);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPassword()).isEqualTo("{ARGON2}updatedHash");
    }

    @Test
    @DisplayName("updatePasswordForToday : ne modifie pas les entrées des autres jours")
    void updatePasswordForToday_doesNotUpdateOtherDays() {
        APersonne p = createPersonne();
        em.persistAndFlush(p);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);

        CerberePassword yesterday = new CerberePassword();
        yesterday.setAPersonne(p);
        yesterday.setPassword("{ARGON2}yesterdayHash");
        yesterday.setDebut(cal.getTime());
        em.persistAndFlush(yesterday);

        CerberePassword today = new CerberePassword();
        today.setAPersonne(p);
        today.setPassword("{ARGON2}todayHash");
        today.setDebut(new Date());
        em.persistAndFlush(today);

        repository.updatePasswordForToday(p.getId(), "{ARGON2}newHash");

        em.flush();
        em.clear();

        List<CerberePassword> result = repository.findByAPersonne(p);
        assertThat(result).hasSize(2);

        // Today's entry (first due to ORDER BY debut DESC) should be updated
        assertThat(result.get(0).getPassword()).isEqualTo("{ARGON2}newHash");
        // Yesterday's entry should be unchanged
        assertThat(result.get(1).getPassword()).isEqualTo("{ARGON2}yesterdayHash");
    }

}
