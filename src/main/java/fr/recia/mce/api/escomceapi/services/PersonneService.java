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
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp;
import fr.recia.mce.api.escomceapi.services.exception.InvalidAvatarException;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Objects;

@Service
@Getter
@Slf4j
public class PersonneService {

    private static final String DB_CACHE_NAME = "personneDBCache";
    private static final String LDAP_CACHE_NAME = "personneLDAPCache";

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

    @Autowired
    private MailProperties mailProperties;

    public PersonneDTO getUserByUid(String uid) {
        Cache cache = cacheManager.getCache(DB_CACHE_NAME);
        if (cache != null) {
            PersonneDTO cached = cache.get(uid, PersonneDTO.class);
            if (cached != null) {
                log.debug("Cache hit pour 'personneDBCache' : Données DB chargées pour l'uid [{}]", uid);
                return cached;
            }
        }

        log.debug("Cache miss pour 'personneDBCache' : Récupération des données DB pour l'uid [{}]", uid);

        PersonneDTO personne = aPersonneRepository.getPersonneByUid(uid);

        if (personne != null) {
            loadLdapUser(personne, uid);
            if (cache != null) {
                cache.putIfAbsent(uid, personne);
            }
        }

        log.debug("Recherche base de données terminée pour l'uid [{}] : Résultat={}", uid, personne);
        return personne;
    }

    private IExternalUser getUserLdap(String uid) {
        IExternalUser userLdap = null;

        Cache cache = cacheManager.getCache(LDAP_CACHE_NAME);

        if (cache != null) {
            IExternalUser getUser = cache.get(uid, IExternalUser.class);
            if (!Objects.isNull(getUser)) {
                log.debug("Cache hit pour 'personneLDAPCache' : Données LDAP chargées pour l'uid [{}]", uid);
                return getUser;
            }
        }

        try {
            log.debug("Cache miss pour 'personneLDAPCache' : Récupération des données LDAP depuis l'annuaire pour l'uid [{}]", uid);
            userLdap = getExtDao().getUserByUid(uid);
            if (cache != null) {
                cache.putIfAbsent(uid, userLdap);
            }

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
     * @param uid
     *            L'UID de l'utilisateur.
     * @return Les octets de l'image, ou null si aucune image n'est trouvée.
     * @throws RuntimeException
     *             En cas d'erreur lors de la lecture du fichier.
     */
    public byte[] getAvatar(String uid) {
        String groupDir = uid.substring(0, 2);
        String userDir = uid.substring(2);
        Path path = Paths.get(mceProperties.getAvatar().getStoragePath(), groupDir, userDir, mceProperties.getAvatar().getFilename());
        if (!Files.exists(path)) {
            log.warn("Avatar non trouvé pour le hash [{}] à l'emplacement : {}", uid, path);
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
     * Met à jour l'avatar de l'utilisateur : effectue une rotation des fichiers (0/1), enregistre la nouvelle image, met à jour la base de données et
     * synchronise le LDAP.
     *
     * @param uid
     *            L'UID de l'utilisateur.
     * @param fileContent
     *            Le contenu binaire de la nouvelle image.
     * @throws PersonneNotFoundException
     *             Si l'utilisateur n'est pas trouvé en base.
     * @throws RuntimeException
     *             En cas d'erreur de stockage ou de synchronisation.
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
        String groupDir = hash.substring(0, 2);
        String userDir = hash.substring(2);
        Path storageDir = Paths.get(mceProperties.getAvatar().getStoragePath(), groupDir, userDir);

        if (!Files.exists(storageDir)) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                log.error("Impossible de créer le dossier de stockage : {}. Raison : {}", storageDir, e.getMessage());
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

        String filename = mceProperties.getAvatar().getFilename();
        String filenameBackup = mceProperties.getAvatar().getFilenameBackup();
        Path path0 = storageDir.resolve(filename);
        Path path1 = storageDir.resolve(filenameBackup);

        // Rotation cyclique
        try {
            if (Files.exists(path0)) {
                if (Files.exists(path1))
                    Files.delete(path1);
                Files.move(path0, path1);
            }
            Files.write(path0, fileContent);
        } catch (IOException e) {
            log.error("Erreur de gestion des fichiers avatar pour l'UID [{}]: {}", uid, e.getMessage());
            throw new RuntimeException("Erreur lors de l'enregistrement", e);
        }

        // Chemin relatif avec version globale
        String relativePath = mceProperties.getAvatar().getBaseUrl() + hash + "/" + filename + "?v=" + nextVersion;

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

    public List<String> getDistinctCategories() {
        return aPersonneRepository.findDistinctCategories();
    }

    public void validateEmailForUpdate(String uid, String newEmail) {        PersonneDTO personneDTO = aPersonneRepository.getPersonneByUid(uid);
        if (personneDTO == null) {
            log.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable en base de données", uid);
            throw new PersonneNotFoundException("Utilisateur introuvable en base : " + uid);
        }

        if (!canEditPersonalEmail(personneDTO)) {
            log.warn("Audit [UPDATE_EMAIL] : REFUSÉ pour l'utilisateur [{}] - Raison : Email personnel non modifiable pour ce profil", uid);
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre email personnel");
        }

        if (!newEmail.matches(mailProperties.getRegexValideAddr())) {
            throw new IllegalArgumentException("Le format de l'adresse email n'est pas valide");
        }

        String domain = newEmail.substring(newEmail.lastIndexOf('@') + 1);
        String[] excludedDomains = mailProperties.getRegexsDomainesExclus().split("\\s+");
        for (String excluded : excludedDomains) {
            if (domain.equalsIgnoreCase(excluded.trim())) {
                throw new IllegalArgumentException("Le domaine de l'adresse email est exclu");
            }
        }
    }

    @Transactional
    public void updateEmail(String uid, String newEmail) {
        log.info("Mise à jour de l'email pour l'utilisateur [uid={}] vers : {}", uid, newEmail);

        validateEmailForUpdate(uid, newEmail);

        // L'email est déjà enregistré dans cerbere_confirmation (via sendVerificationEmail)
        // Pas de mise à jour dans apersonne ni LDAP - lecture dynamique depuis cerbere_confirmation
        clearUserCaches(uid);

        log.info("Audit [UPDATE_EMAIL] : SUCCÈS pour l'utilisateur [{}]", uid);
    }

    private boolean canEditPersonalEmail(PersonneDTO personne) {
        APersonne base = personne.getAPersonneBase();
        if (base == null) {
            return false;
        }

        EnumPublic publicProfile = personne.getEnumPublic();
        if (publicProfile != null && publicProfile.isEleve()) {
            return true;
        }

        if (publicProfile == null) {
            EnumCategorie cat = EnumCategorie.fromString(base.getCategorie());
            if (cat == EnumCategorie.ELEVE) {
                return true;
            }
        }

        if (StringUtils.isNotBlank(base.getEmailPersonnel())) {
            return true;
        }

        return StringUtils.isBlank(personne.getMailFixe());
    }

    @Transactional
    public void signCharte(String uid) {
        log.info("[signCharte] DEBUT uid={}", uid);
        APersonne entity = aPersonneRepository.findByUid(uid);
        if (entity == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : " + uid);
        }
        entity.setValidationCharte(new Date());
        aPersonneRepository.save(entity);
        clearUserCaches(uid);
        log.info("[signCharte] FIN uid={}", uid);
    }

    /**
     * Active un compte initialement {@code Invalide} en posant l'état {@code Valide}. Seule à produire la transition d'activation dans l'API.
     */
    @Transactional
    public void valideCompte(String uid) {
        log.info("[valideCompte] DEBUT uid={}", uid);
        APersonne entity = aPersonneRepository.findByUid(uid);
        if (entity == null) {
            throw new IllegalArgumentException("Utilisateur introuvable : " + uid);
        }
        if (AccountState.DELETE.equals(entity.getEtat())) {
            throw new IllegalArgumentException("Ce compte a été supprimé et ne peut pas être activé : " + uid);
        }
        if (!AccountState.VALIDE.equals(entity.getEtat())) {
            entity.setEtat(AccountState.VALIDE);
            entity.setDateModification(new Date());
            aPersonneRepository.save(entity);
            clearUserCaches(uid);
            log.info("[valideCompte] FIN uid={} -> Valide", uid);
        } else {
            log.info("[valideCompte] FIN uid={} déjà Valide", uid);
        }
    }

    public void clearUserCaches(String uid) {
        log.info("[clearUserCaches] DEBUT uid={}", uid);
        Cache dbCache = cacheManager.getCache(DB_CACHE_NAME);
        if (dbCache != null) {
            dbCache.evict(uid);
        }
        Cache ldapCache = cacheManager.getCache(LDAP_CACHE_NAME);
        if (ldapCache != null) {
            ldapCache.evict(uid);
        }
        log.info("[clearUserCaches] FIN uid={}", uid);
    }

}
