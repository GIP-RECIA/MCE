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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class RelationEleveContact {

    public enum SensRel {
        /**
         * le contact est l'origine de la relation,
         * l'élèves est la relation.
         */
        CONTACT2ELEVE,
        /**
         * L'élèves est l'origine de la relation,
         * le contact est la relation.
         */
        ELEVE2CONTACT;
    }

    /**
     * La personne est'elle un élève (true) ou un contact (false).
     * Si la personne est un élève alors la relation est un contact
     * et vice versa.
     */
    private final boolean isEleve2Contact;
    private Long contact;
    private PersonneDTO eleve;

    private String uidRelation;

    private String displayNameRelation;

    private boolean autoriteParental;

    private String typeRelation;
    private boolean isSelected;

    public RelationEleveContact(final SensRel typeRel) {
        isEleve2Contact = (typeRel == SensRel.ELEVE2CONTACT);
    }
}
