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
package fr.recia.mce.api.escomceapi.configuration.bean;

import lombok.Data;

/**
 * Propriétés de configuration pour la gestion des avatars.
 */
@Data
public class AvatarProperties {
    /**
     * Taille maximale autorisée pour un avatar en octets.
     */
    private long maxSize = 512 * 1024; // 512 KB par défaut

    /**
     * Types MIME autorisés.
     */
    private java.util.List<String> allowedTypes = java.util.Arrays.asList("image/jpeg", "image/png");

    /**
     * URL de base utilisée pour construire les liens publics d'accès aux avatars.
     */
    private String baseUrl;
    
    /**
     * Chemin physique sur le serveur où les fichiers images des avatars sont enregistrés.
     */
    private String storagePath;

    /**
     * Nom du fichier image de l'avatar (ex: avatar0.jpg).
     */
    private String filename = "avatar0.jpg";

    /**
     * Nom du fichier de backup/rotation (ex: avatar1.jpg).
     */
    private String filenameBackup = "avatar1.jpg";
}
