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

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import fr.recia.mce.api.escomceapi.db.enums.ConfirmationType;
import lombok.Data;

@Entity
@Table(name = "cerbere_confirmation")
@Data
public class CerbereConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idPersonne", nullable = false)
    private APersonne aPersonne;

    @Column(name = "code")
    private String code;

    @Column(name = "mail")
    private String mail;

    @Column(name = "limite", nullable = false, length = 19)
    private Date limite;

    @Column(name = "confirmation", length = 19)
    private Date confirmation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idEditor")
    private APersonne editor;

    /**
     * Type dérivé du préfixe du code hashé (VERIFY: ou RESET:). Pas de colonne en base — le type est déterminé dynamiquement depuis {@code code}.
     */
    @Transient
    public ConfirmationType getType() {
        return ConfirmationType.fromCode(this.code);
    }
}
