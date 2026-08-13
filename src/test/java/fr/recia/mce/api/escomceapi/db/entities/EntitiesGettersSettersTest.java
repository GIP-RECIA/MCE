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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EntitiesGettersSettersTest {

    @Test
    void aStructure() {
        AStructure s = new AStructure();
        APersonne contact = new APersonne();
        APersonne resp = new APersonne();
        Date d = new Date();
        Set<APersonne> personnes = new HashSet<>();
        personnes.add(new APersonne());

        s.setId(1L);
        s.setVersion(2L);
        s.setAPersonneByContactId(contact);
        s.setAPersonneByResponsableId(resp);
        s.setDateAcquittement(d);
        s.setDateCreation(d);
        s.setDateModification(d);
        s.setCategorie("cat");
        s.setCle("cle");
        s.setSource("src");
        s.setEtat("etat");
        s.setNom("nom");
        s.setSiren("123");
        s.setSiteWeb("web");
        s.setTypeStructureFk(5L);
        s.setAnneeScolaire(d);
        s.setAPersonnes(personnes);

        assertThat(s.getId()).isEqualTo(1L);
        assertThat(s.getVersion()).isEqualTo(2L);
        assertThat(s.getAPersonneByContactId()).isSameAs(contact);
        assertThat(s.getAPersonneByResponsableId()).isSameAs(resp);
        assertThat(s.getDateAcquittement()).isEqualTo(d);
        assertThat(s.getDateCreation()).isEqualTo(d);
        assertThat(s.getDateModification()).isEqualTo(d);
        assertThat(s.getCategorie()).isEqualTo("cat");
        assertThat(s.getCle()).isEqualTo("cle");
        assertThat(s.getSource()).isEqualTo("src");
        assertThat(s.getEtat()).isEqualTo("etat");
        assertThat(s.getNom()).isEqualTo("nom");
        assertThat(s.getSiren()).isEqualTo("123");
        assertThat(s.getSiteWeb()).isEqualTo("web");
        assertThat(s.getTypeStructureFk()).isEqualTo(5L);
        assertThat(s.getAnneeScolaire()).isEqualTo(d);
        assertThat(s.getAPersonnes()).isEqualTo(personnes);
    }

    @Test
    void aPersonne() {
        APersonne p = new APersonne();
        AStructure structure = new AStructure();
        Date d = new Date();

        p.setId(1L);
        p.setVersion(2L);
        p.setAStructure(structure);
        p.setDateAcquittement(d);
        p.setDateCreation(d);
        p.setDateModification(d);
        p.setAnneeScolaire(d);
        p.setCategorie("cat");
        p.setCivilite("M");
        p.setCle("cle");
        p.setSource("src");
        p.setCn("cn");
        p.setDateNaissance(d);
        p.setDisplayName("display");
        p.setEmail("mail@x");
        p.setEmailPersonnel("perso@x");
        p.setEtat("etat");
        p.setGivenName("given");
        p.setPassword("pwd");
        p.setSn("sn");
        p.setTitre("titre");
        p.setUid("uid");
        p.setValidationCharte(d);
        p.setDoForward(true);
        p.setSambaLmpassword("lm");
        p.setSambaNtpassword("nt");
        p.setPhoto("photo");

        Set<Login> logins = new HashSet<>();
        Set<CerbereEnfant> enfants = new HashSet<>();
        Set<CerbereConfirmation> confs = new HashSet<>();
        Set<CerberePassword> passwords = new HashSet<>();
        Set<AStructure> structs = new HashSet<>();
        p.setLoginsForApersonneLogin(logins);
        p.setLoginsForApersonneAlias(logins);
        p.setLoginsForApersonneOldAlias(logins);
        p.setCerbereEnfantsForIdParent(enfants);
        p.setCerbereEnfantsForIdEnfant(enfants);
        p.setCerbereConfirmations(confs);
        p.setCerberePasswords(passwords);
        p.setAstructuresForResponsableId(structs);
        p.setAstructuresForContactId(structs);
        p.setAstructuresForContactId_1(structs);
        p.setAstructuresForResponsableId_1(structs);

        assertThat(p.getId()).isEqualTo(1L);
        assertThat(p.getVersion()).isEqualTo(2L);
        assertThat(p.getAStructure()).isSameAs(structure);
        assertThat(p.getDateAcquittement()).isEqualTo(d);
        assertThat(p.getDateCreation()).isEqualTo(d);
        assertThat(p.getDateModification()).isEqualTo(d);
        assertThat(p.getAnneeScolaire()).isEqualTo(d);
        assertThat(p.getCategorie()).isEqualTo("cat");
        assertThat(p.getCivilite()).isEqualTo("M");
        assertThat(p.getCle()).isEqualTo("cle");
        assertThat(p.getSource()).isEqualTo("src");
        assertThat(p.getCn()).isEqualTo("cn");
        assertThat(p.getDateNaissance()).isEqualTo(d);
        assertThat(p.getDisplayName()).isEqualTo("display");
        assertThat(p.getEmail()).isEqualTo("mail@x");
        assertThat(p.getEmailPersonnel()).isEqualTo("perso@x");
        assertThat(p.getEtat()).isEqualTo("etat");
        assertThat(p.getGivenName()).isEqualTo("given");
        assertThat(p.getPassword()).isEqualTo("pwd");
        assertThat(p.getSn()).isEqualTo("sn");
        assertThat(p.getTitre()).isEqualTo("titre");
        assertThat(p.getUid()).isEqualTo("uid");
        assertThat(p.getValidationCharte()).isEqualTo(d);
        assertThat(p.isDoForward()).isTrue();
        assertThat(p.getSambaLmpassword()).isEqualTo("lm");
        assertThat(p.getSambaNtpassword()).isEqualTo("nt");
        assertThat(p.getPhoto()).isEqualTo("photo");
        assertThat(p.getLoginsForApersonneLogin()).isEqualTo(logins);
        assertThat(p.getLoginsForApersonneAlias()).isEqualTo(logins);
        assertThat(p.getLoginsForApersonneOldAlias()).isEqualTo(logins);
        assertThat(p.getCerbereEnfantsForIdParent()).isEqualTo(enfants);
        assertThat(p.getCerbereEnfantsForIdEnfant()).isEqualTo(enfants);
        assertThat(p.getCerbereConfirmations()).isEqualTo(confs);
        assertThat(p.getCerberePasswords()).isEqualTo(passwords);
        assertThat(p.getAstructuresForResponsableId()).isEqualTo(structs);
        assertThat(p.getAstructuresForContactId()).isEqualTo(structs);
        assertThat(p.getAstructuresForContactId_1()).isEqualTo(structs);
        assertThat(p.getAstructuresForResponsableId_1()).isEqualTo(structs);
    }

    @Test
    void typeFonctionFiliere() {
        TypeFonctionFiliere t = new TypeFonctionFiliere();
        Set<Fonction> fonctions = new HashSet<>();
        t.setId(1L);
        t.setCodeFiliere("code");
        t.setLibelleFiliere("lib");
        t.setSource("src");
        t.setFonctions(fonctions);

        assertThat(t.getId()).isEqualTo(1L);
        assertThat(t.getCodeFiliere()).isEqualTo("code");
        assertThat(t.getLibelleFiliere()).isEqualTo("lib");
        assertThat(t.getSource()).isEqualTo("src");
        assertThat(t.getFonctions()).isEqualTo(fonctions);
    }

    @Test
    void fonction() {
        Fonction f = new Fonction();
        Discipline disc = new Discipline();
        TypeFonctionFiliere tff = new TypeFonctionFiliere();
        AStructure structure = new AStructure();
        f.setId(1L);
        f.setDiscipline(disc);
        f.setTypeFonctionFiliere(tff);
        f.setAStructure(structure);

        assertThat(f.getId()).isEqualTo(1L);
        assertThat(f.getDiscipline()).isSameAs(disc);
        assertThat(f.getTypeFonctionFiliere()).isSameAs(tff);
        assertThat(f.getAStructure()).isSameAs(structure);
    }

    @Test
    void enseignement() {
        Enseignement e = new Enseignement();
        Date d = new Date();
        e.setId(1L);
        e.setMatiere("math");
        e.setSource("src");
        e.setCode("code");
        e.setAnneScolaire(d);

        assertThat(e.getId()).isEqualTo(1L);
        assertThat(e.getMatiere()).isEqualTo("math");
        assertThat(e.getSource()).isEqualTo("src");
        assertThat(e.getCode()).isEqualTo("code");
        assertThat(e.getAnneScolaire()).isEqualTo(d);
    }

    @Test
    void cerbereEnfant() {
        CerbereEnfant c = new CerbereEnfant();
        byte[] id = new byte[]{1, 2, 3};
        APersonne parent = new APersonne();
        APersonne enfant = new APersonne();
        c.setId(id);
        c.setAPersonneByIdParent(parent);
        c.setAPersonneByIdEnfant(enfant);
        c.setTypeRelation("type");
        c.setLienParente("lien");

        assertThat(c.getId()).isEqualTo(id);
        assertThat(c.getAPersonneByIdParent()).isSameAs(parent);
        assertThat(c.getAPersonneByIdEnfant()).isSameAs(enfant);
        assertThat(c.getTypeRelation()).isEqualTo("type");
        assertThat(c.getLienParente()).isEqualTo("lien");
    }

    @Test
    void discipline() {
        Discipline disc = new Discipline();
        Set<Fonction> fonctions = new HashSet<>();
        disc.setId(1L);
        disc.setCode("code");
        disc.setDisciplinePoste("poste");
        disc.setSource("src");
        disc.setFonctions(fonctions);

        assertThat(disc.getId()).isEqualTo(1L);
        assertThat(disc.getCode()).isEqualTo("code");
        assertThat(disc.getDisciplinePoste()).isEqualTo("poste");
        assertThat(disc.getSource()).isEqualTo("src");
        assertThat(disc.getFonctions()).isEqualTo(fonctions);
    }

    @Test
    void cerbereConfirmation() {
        CerbereConfirmation c = new CerbereConfirmation();
        APersonne p = new APersonne();
        APersonne editor = new APersonne();
        Date d = new Date();
        c.setId(1L);
        c.setAPersonne(p);
        c.setCode("code");
        c.setMail("mail");
        c.setLimite(d);
        c.setConfirmation(d);
        c.setEditor(editor);

        assertThat(c.getId()).isEqualTo(1L);
        assertThat(c.getAPersonne()).isSameAs(p);
        assertThat(c.getCode()).isEqualTo("code");
        assertThat(c.getMail()).isEqualTo("mail");
        assertThat(c.getLimite()).isEqualTo(d);
        assertThat(c.getConfirmation()).isEqualTo(d);
        assertThat(c.getEditor()).isSameAs(editor);
    }

    @Test
    void aFonction() {
        AFonction f = new AFonction();
        APersonne p = new APersonne();
        Date d = new Date();
        f.setId(1L);
        f.setVersion(2L);
        f.setAPersonne(p);
        f.setDateAcquittement(d);
        f.setDateCreation(d);
        f.setDateModification(d);
        f.setCategorie("cat");
        f.setSource("src");
        f.setDateFin(d);

        assertThat(f.getId()).isEqualTo(1L);
        assertThat(f.getVersion()).isEqualTo(2L);
        assertThat(f.getAPersonne()).isSameAs(p);
        assertThat(f.getDateAcquittement()).isEqualTo(d);
        assertThat(f.getDateCreation()).isEqualTo(d);
        assertThat(f.getDateModification()).isEqualTo(d);
        assertThat(f.getCategorie()).isEqualTo("cat");
        assertThat(f.getSource()).isEqualTo("src");
        assertThat(f.getDateFin()).isEqualTo(d);
    }

    @Test
    void cerberePassword() {
        CerberePassword c = new CerberePassword();
        APersonne p = new APersonne();
        Date d = new Date();
        c.setId(1L);
        c.setAPersonne(p);
        c.setPassword("pwd");
        c.setDebut(d);
        c.setFin(d);

        assertThat(c.getId()).isEqualTo(1L);
        assertThat(c.getAPersonne()).isSameAs(p);
        assertThat(c.getPassword()).isEqualTo("pwd");
        assertThat(c.getDebut()).isEqualTo(d);
        assertThat(c.getFin()).isEqualTo(d);
    }

    @Test
    void login() {
        Login l = new Login();
        APersonne a = new APersonne();
        APersonne b = new APersonne();
        APersonne c = new APersonne();
        Date d = new Date();
        l.setId(1L);
        l.setVersion(2L);
        l.setAPersonneByAPersonneOldAlias(a);
        l.setAPersonneByAPersonneLogin(b);
        l.setAPersonneByAPersonneAlias(c);
        l.setDateAcquittement(d);
        l.setDateCreation(d);
        l.setDateModification(d);
        l.setNom("nom");

        assertThat(l.getId()).isEqualTo(1L);
        assertThat(l.getVersion()).isEqualTo(2L);
        assertThat(l.getAPersonneByAPersonneOldAlias()).isSameAs(a);
        assertThat(l.getAPersonneByAPersonneLogin()).isSameAs(b);
        assertThat(l.getAPersonneByAPersonneAlias()).isSameAs(c);
        assertThat(l.getDateAcquittement()).isEqualTo(d);
        assertThat(l.getDateCreation()).isEqualTo(d);
        assertThat(l.getDateModification()).isEqualTo(d);
        assertThat(l.getNom()).isEqualTo("nom");
    }
}
