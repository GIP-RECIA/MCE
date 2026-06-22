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

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import fr.recia.mce.api.escomceapi.services.exception.InvalidAvatarException;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

@Service
@Getter
@Slf4j
public class PersonneService {

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private LdapUserDaoImp userLdapDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private transient IExternalUserDao extDao;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

    public PersonneDTO getUserByUid(String uid) {
        Cache cache = cacheManager.getCache("personneDBCache");
        PersonneDTO cached = cache.get(uid, PersonneDTO.class);
        if (cached != null) {
            log.debug("Cache hit pour 'personneDBCache' : Données DB chargées pour l'uid [{}]", uid);
            return cached;
        }

        log.debug("Cache miss pour 'personneDBCache' : Récupération des données DB pour l'uid [{}]", uid);

        PersonneDTO personne = aPersonneRepository.getPersonneByUid(uid);

        if (personne != null) {
            loadLdapUser(personne, uid);
            cache.putIfAbsent(uid, personne);
        }

        log.debug("Recherche base de données terminée pour l'uid [{}] : Résultat={}", uid, personne);
        return personne;
    }

    @Cacheable(cacheNames = "personneLDAPCache", key = "#uid")
    private IExternalUser getUserLdap(String uid) {
        IExternalUser userLdap = null;

        Cache cache = cacheManager.getCache("personneLDAPCache");

        IExternalUser getUser = cache.get(uid, IExternalUser.class);
        if (!Objects.isNull(getUser)) {
            log.debug("Cache hit pour 'personneLDAPCache' : Données LDAP chargées pour l'uid [{}]", uid);
            return getUser;
        }

        try {
            log.debug("Cache miss pour 'personneLDAPCache' : Récupération des données LDAP depuis l'annuaire pour l'uid [{}]", uid);
            userLdap = getExtDao().getUserByUid(uid);
            cache.putIfAbsent(uid, userLdap);

        } catch (Exception e) {
            log.error("Échec du chargement des données LDAP pour l'uid [{}] - Détail : {}", uid, e.getMessage());

        }
        return userLdap;

    }

    private void loadLdapUser(PersonneDTO personne, String uid) {
        IExternalUser u = getUserLdap(uid);
        personne.setExtUser(u);
    }

    public PersonneDTO retrievePersonnebyUid(String uid) {
        return this.getUserByUid(uid);
    }

    public IExternalUser retrievePersonLdap(String uid) {
        log.debug("retrievePersonLdap : {}", uid);
        return getUserLdap(uid);

    }

    /**
     * Récupère le contenu binaire de l'avatar d'un utilisateur depuis le stockage local.
     *
     * @param uid L'UID de l'utilisateur.
     * @return Les octets de l'image, ou null si aucune image n'est trouvée.
     * @throws RuntimeException En cas d'erreur lors de la lecture du fichier.
     */
    public byte[] getAvatar(String uid) {
        // Chemin physique : storagePath / uid / avatar0.jpg
        Path path = Paths.get(mceProperties.getAvatar().getStoragePath(), uid, "avatar0.jpg");
        if (!Files.exists(path)) {
            log.warn("Avatar non trouvé pour l'UID [{}] à l'emplacement : {}", uid, path);
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (java.io.IOException e) {
            log.error("Erreur lors de la lecture de l'avatar pour l'UID [{}] : {}", uid, e.getMessage());
            throw new RuntimeException("Erreur lors de la lecture de l'image", e);
        }
    }

    private String getHashFromUid(String uid) {
        if (uid == null || uid.length() < 2) {
            return uid;
        }
        
        char[] tab = uid.toCharArray();
        int res = 0;
        for (char c : tab) {
            res = (res * 100) + c;
        }
        return String.format("%x", res);
    }

    /**
     * Met à jour l'avatar de l'utilisateur : effectue une rotation des fichiers (0/1),
     * enregistre la nouvelle image, met à jour la base de données et synchronise le LDAP.
     *
     * @param uid         L'UID de l'utilisateur.
     * @param fileContent Le contenu binaire de la nouvelle image.
     * @throws PersonneNotFoundException Si l'utilisateur n'est pas trouvé en base.
     * @throws RuntimeException          En cas d'erreur de stockage ou de synchronisation.
     */
    @Transactional
    public void updateAvatar(String uid, byte[] fileContent) {
        log.info("Mise à jour de la photo pour l'utilisateur [uid={}]", uid);

        // 0. Validation de sécurité
        if (fileContent.length > mceProperties.getAvatar().getMaxSize()) {
            throw new InvalidAvatarException("Taille de l'avatar trop élevée (max " + mceProperties.getAvatar().getMaxSize() / 1024 + " Ko)");
        }

        try (ByteArrayInputStream is = new ByteArrayInputStream(fileContent)) {
            ImageInputStream iis = ImageIO.createImageInputStream(is);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidAvatarException("Format d'image non valide");
            }
            
            ImageReader reader = readers.next();
            String format = reader.getFormatName().toLowerCase();
            boolean formatAllowed = false;
            for (String allowed : mceProperties.getAvatar().getAllowedTypes()) {
                if (allowed.contains(format)) {
                    formatAllowed = true;
                    break;
                }
            }
            
            if (!formatAllowed) {
                throw new InvalidAvatarException("Type d'image non autorisé : " + format);
            }
        } catch (IOException e) {
            throw new InvalidAvatarException("Erreur lors de la lecture de l'image");
        }

        APersonne entity = aPersonneRepository.findByUid(uid);
        if (entity == null) {
            throw new PersonneNotFoundException("Utilisateur introuvable en base : " + uid);
        }

        // 1. Sauvegarde du fichier
        String hash = getHashFromUid(uid);
        Path storageDir = Paths.get(mceProperties.getAvatar().getStoragePath(), hash);

        if (!Files.exists(storageDir)) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                log.error("Impossible de créer le dossier de stockage : {}. Raison : {}", storageDir, e.getMessage(), e);
                throw new RuntimeException("Impossible de créer le dossier de stockage", e);
            }
        }

        // Lecture version actuelle dans la BDD
        int nextVersion = 1;
        String currentPhoto = entity.getPhoto();
        if (currentPhoto != null && currentPhoto.contains("v=")) {
            try {
                nextVersion = Integer.parseInt(currentPhoto.substring(currentPhoto.lastIndexOf("v=") + 2)) + 1;
            } catch (Exception e) {
                log.warn("Impossible de lire la version actuelle, on réinitialise à 1");
            }
        }

        Path path0 = storageDir.resolve("avatar0.jpg");
        Path path1 = storageDir.resolve("avatar1.jpg");

        // Rotation cyclique
        try {
            if (Files.exists(path0)) {
                if (Files.exists(path1)) Files.delete(path1);
                Files.move(path0, path1);
            }
            Files.write(path0, fileContent);
        } catch (IOException e) {
            log.error("Erreur de gestion des fichiers avatar pour l'UID [{}]: {}", uid, e.getMessage());
            throw new RuntimeException("Erreur lors de l'enregistrement", e);
        }

        // Chemin relatif avec version globale
        String relativePath = mceProperties.getAvatar().getBaseUrl() + hash + "/avatar0.jpg?v=" + nextVersion;

        // 2. Mise à jour de l'entité
        entity.setPhoto(relativePath);
        entity.setDateModification(new Date());
        aPersonneRepository.saveAndFlush(entity);

        // 3. Mise à jour LDAP 
        try {
            getExtDao().updateAvatarLDAP(uid, relativePath);
        } catch (Exception e) {
            log.warn("Impossible de mettre à jour LDAP pour l'UID [{}]: {}", uid, e.getMessage());
        }

        // 4. Invalidation du cache
        clearUserCaches(uid);
    }

    @Transactional
    public void updateEmail(String uid, String newEmail) {
        log.info("Mise à jour de l'email pour l'utilisateur [uid={}] vers : {}", uid, newEmail);

        // 1. Update in Database
        PersonneDTO personneDTO = aPersonneRepository.getPersonneByUid(uid);
        if (personneDTO == null) {
            specialLog.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable en base de données", uid);
            throw new PersonneNotFoundException("Utilisateur introuvable en base : " + uid);
        }

        if (!canEditPersonalEmail(personneDTO)) {
            specialLog.warn("Audit [UPDATE_EMAIL] : REFUSÉ pour l'utilisateur [{}] - Raison : Email personnel non modifiable pour ce profil", uid);
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre email personnel");
        }

        APersonne entity = personneDTO.getApersonne();

//        adresse email interne a l ent
//        entity.setEmail(newEmail);

        // addresse email externe a l ent
        entity.setEmailPersonnel(newEmail);
        entity.setDateModification(new Date());

        aPersonneRepository.saveAndFlush(entity);
        log.debug("Email mis à jour en base de données pour l'uid : {}", uid);

        // 2. Update in LDAP
        try {
            getExtDao().updateEmail(uid, newEmail);
            log.debug("Email mis à jour dans l'annuaire LDAP pour l'uid : {}", uid);
        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Échec de la mise à jour LDAP | Détail : {}", uid, e.getMessage());
            throw new RuntimeException("Erreur lors de la mise à jour de l'email dans l'annuaire", e);
        }

        // 3. Clear Caches
        clearUserCaches(uid);

        specialLog.info("Audit [UPDATE_EMAIL] : SUCCÈS pour l'utilisateur [{}]", uid);
    }

    private boolean canEditPersonalEmail(PersonneDTO personne) {
        EnumPublic publicProfile = personne.getEnumPublic();
        APersonne base = personne.getAPersonneBase();

        if (base == null) return false;

        if (publicProfile == null) {
            EnumCategorie cat = EnumCategorie.fromString(base.getCategorie());
            return cat == EnumCategorie.ELEVE
                    || StringUtils.isNotBlank(base.getEmailPersonnel())
                    || StringUtils.isBlank(base.getEmail());
        }

        return publicProfile.isEleve()
                || StringUtils.isNotBlank(base.getEmailPersonnel())
                || StringUtils.isBlank(base.getEmail());
    }

    private void clearUserCaches(String uid) {
        Cache dbCache = cacheManager.getCache("personneDBCache");
        if (dbCache != null) {
            dbCache.evict(uid);
        }
        Cache ldapCache = cacheManager.getCache("personneLDAPCache");
        if (ldapCache != null) {
            ldapCache.evict(uid);
        }
    }

}
