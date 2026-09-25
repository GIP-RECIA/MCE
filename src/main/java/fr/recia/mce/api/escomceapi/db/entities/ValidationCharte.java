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

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * Validation de la charte d'un utilisateur pour un service donné.
 * <p>
 * Table préexistante {@code validationcharteservice} : une ligne par couple
 * (personne, service). Une personne peut appartenir à plusieurs services (chacun
 * avec sa propre charte) ; la charte de chaque service est validée indépendamment.
 * <p>
 * Identifiant métier = {@code (aPersonneId, serviceId)}. {@code charterVersionDate}
 * est la date de version de la charte du service au moment de la signature et
 * {@code validatedAt} la date/heure de validation effective. La colonne
 * {@code apersonne.validationCharte} n'est plus utilisée comme source de vérité.
 */
@Entity
@Table(name = "validationcharteservice", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"APersonne_id", "serviceId"})})
@IdClass(ValidationCharteId.class)
@Getter
@Setter
public class ValidationCharte implements Serializable {

    /** Identifiant de la personne {@code apersonne} signataire. */
    @Id
    @Column(name = "APersonne_id", nullable = false)
    private Long aPersonneId;

    /** Service (domaine) de la charte signée (source de la personne, ex. COLL-37) ; « default » si la personne n'a pas de source. */
    @Id
    @Column(name = "serviceId", nullable = false, length = 60)
    private String serviceId;

    /** Date de version de la charte du service signée. */
    @Temporal(TemporalType.DATE)
    @Column(name = "charterVersionDate", nullable = false)
    private Date charterVersionDate;

    /** Date/heure de validation (signature effective). */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "validatedAt", nullable = false)
    private Date validatedAt;
}