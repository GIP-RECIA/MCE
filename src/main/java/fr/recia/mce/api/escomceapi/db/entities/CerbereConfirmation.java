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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cerbere_confirmation")
@Getter
@Setter
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

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "limite", nullable = false, length = 19)
    private Date limite;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "confirmation", length = 19)
    private Date confirmation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idEditor", nullable = true)
    private APersonne editor;

    public CerbereConfirmation() {
    }

    public CerbereConfirmation(APersonne apersonne, Date limite, APersonne editor) {
        this.aPersonne = apersonne;
        this.limite = limite;
        this.editor = editor;
    }

    public CerbereConfirmation(APersonne apersonne, String code, String mail,
            Date limite, Date confirmation, APersonne editor) {
        this.aPersonne = apersonne;
        this.code = code;
        this.mail = mail;
        this.limite = limite;
        this.confirmation = confirmation;
        this.editor = editor;
    }
}
