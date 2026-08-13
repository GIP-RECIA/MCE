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
package fr.recia.mce.api.escomceapi.services.beans;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@EqualsAndHashCode
public class RelationEleveContact {

    public enum SensRel {
        CONTACT2ELEVE, // Parent - Élève
        ELEVE2CONTACT; // Élève - Parent
    }

    private final boolean isEleve2Contact;

    private Long contact;
    private PersonneDTO eleve;
    private String uidRelation;
    private String displayNameRelation;
    private boolean autoriteParental;
    private String typeRelation;
    private String lienParente;
    private boolean isSelected;

    /**
     * Constructeur existant utilisé dans le code
     */
    public RelationEleveContact(final SensRel typeRel) {
        this.isEleve2Contact = (typeRel == SensRel.ELEVE2CONTACT);
    }

    /**
     * Constructeur utilisé par la requête JPQL parent = le parent/tuteur enfant = l'élève
     */
    public RelationEleveContact(
            String sensStr,
            APersonne parent,
            APersonne enfant,
            String typeRelation,
            String lienParente,
            boolean autoriteParental) {

        this.isEleve2Contact = false;

        this.contact = (parent != null) ? parent.getId() : null;
        this.typeRelation = typeRelation;
        this.lienParente = lienParente;
        this.autoriteParental = autoriteParental;

        // On met les infos du PARENT dans uidRelation et displayNameRelation
        if (parent != null) {
            this.uidRelation = parent.getUid();
            this.displayNameRelation = parent.getDisplayName() != null
                    ? parent.getDisplayName()
                    : (parent.getSn() + " " + parent.getGivenName()).trim();
        }

        // On garde l'enfant dans le champ eleve pour cohérence interne
        if (enfant != null) {
            this.eleve = new PersonneDTO(enfant);
        }

        log.debug("RelationEleveContact créée via DB → ParentUid={} | ParentNom={} | EnfantUid={} | Type={} | Lien={}",
                this.uidRelation,
                this.displayNameRelation,
                (enfant != null ? enfant.getUid() : "null"),
                this.typeRelation,
                this.lienParente);
    }
}
