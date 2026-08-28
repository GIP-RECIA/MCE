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
package fr.recia.mce.api.escomceapi.services.exception;

/**
 * Erreur métier levée lorsque l'utilisateur ne peut pas réinitialiser son mot de passe en autonomie (aucun email associé au compte, aucun mode
 * d'authentification local, etc.) et doit se rapprocher d'un administrateur de son établissement.
 */
public class ContactAdminException extends IllegalArgumentException {
    public ContactAdminException(String message) {
        super(message);
    }
}
