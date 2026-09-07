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
package fr.recia.mce.api.escomceapi.services;

/**
 * États de compte en base (colonne {@code etat} de {@code aPersonne}).
 *
 * <p>Source de vérité unique des libellés stockés (chaînes non typées en base) ;
 * à utiliser pour toute comparaison d'état à travers les services.</p>
 */
public final class AccountState {

    public static final String VALIDE = "Valide";
    public static final String INVALIDE = "Invalide";
    public static final String DELETE = "Delete";

    private AccountState() {
    }
}
