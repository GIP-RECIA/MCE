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

import lombok.Getter;

/**
 * Levée quand un code de vérification / de réinitialisation vient déjà d'être envoyé (anti-double-clic) :
 * le client doit attendre la fin du cooldown avant de pouvoir renvoyer un code.
 */
@Getter
public class ResendCooldownActiveException extends ApiException {

    private static final long serialVersionUID = 1L;

    /** Temps restant avant de pouvoir renvoyer un code, en millisecondes (strictement positif). */
    private final long remainingMs;

    public ResendCooldownActiveException(String message, long remainingMs) {
        super(message);
        this.remainingMs = remainingMs;
    }

    /**
     * Temps restant arrondi au second supérieur : à afficher au client ("réessayez dans X s").
     */
    public long getRetryAfterSeconds() {
        return Math.max(1L, (remainingMs + 999L) / 1000L);
    }
}