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
import fr.recia.mce.api.escomceapi.configuration.cache.CacheNames;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.enums.DomSource;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.EnumObjectClass;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.repositories.ValidationCharteRepository;
import fr.recia.mce.api.escomceapi.db.services.DatabaseUserService;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImpl;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.exception.InvalidAvatarException;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.relations.RelationEleveService;
import fr.recia.mce.api.escomceapi.services.structure.StructureService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

@Service
@Getter
@Slf4j
public class PersonneService {

    // TODO : à mettre en conf
    private static final String AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT = "[^@]+@ac-orleans-tours.fr";

    private final MCEProperties mceProperties;
    private final APersonneRepository aPersonneRepository;
    private final ValidationCharteRepository validationCharteRepository;
    private final LdapUserDaoImpl userLdapDao;
    private final CacheManager cacheManager;
    private final LdapUserDaoImpl extDao;
    private final MailProperties mailProperties;
    private final StructureService structureService;
    private final RelationEleveService relationEleveService;
    private final CerbereConfirmationRepository cerbereConfirmationRepository;
    private final DatabaseUserService databaseUserService;
    private Pattern groupsWithSSHAPassword;
    private Pattern groupsWithNtPassword;

    public PersonneService(MCEProperties mceProperties, MailProperties mailProperties, LdapUserDaoImpl extDao, LdapUserDaoImpl userLdapDao,
                           ValidationCharteRepository validationCharteRepository, APersonneRepository aPersonneRepository, CacheManager cacheManager,
                           StructureService structureService, RelationEleveService relationEleveService, CerbereConfirmationRepository cerbereConfirmationRepository, DatabaseUserService databaseUserService) {
        this.mceProperties = mceProperties;
        this.mailProperties = mailProperties;
        this.extDao = extDao;
        this.userLdapDao = userLdapDao;
        this.validationCharteRepository = validationCharteRepository;
        this.aPersonneRepository = aPersonneRepository;
        this.cacheManager = cacheManager;
        this.structureService = structureService;
        this.relationEleveService = relationEleveService;
        this.cerbereConfirmationRepository = cerbereConfirmationRepository;
        this.databaseUserService = databaseUserService;
        String regex = mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass();
        if (regex != null) {
            this.groupsWithSSHAPassword = Pattern.compile(regex);
        }
        String ntRegex = mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt();
        if (ntRegex != null) {
            this.groupsWithNtPassword = Pattern.compile(ntRegex);
        }
    }

    public UserDto getUser(String uid){
        // Récupération dans le cache si possible
        Cache cache = cacheManager.getCache(CacheNames.PERSONNE_CACHE);
        if (cache != null) {
            UserDto cached = cache.get(uid, UserDto.class);
            if (cached != null) {
                log.debug("Cache hit pour 'personneCache' : Données chargées pour l'uid [{}]", uid);
                return cached;
            }
        }

        // Sinon on requête BDD et LDAP et on met dans le cache
        log.debug("Cache miss pour 'personneCache' : Récupération des données pour l'uid [{}]", uid);
        LdapUserDto ldapUser = userLdapDao.getUserLdap(uid);
        DatabaseUserDto databaseUser = databaseUserService.getUserDatabase(uid);
        boolean isLocalUser = false;
        boolean isCollectivite = false;
        String source = databaseUser.getSource();
        // TODO
        DomSource ds = DomSource.AC;
        if (source != null) {
            isLocalUser = source.startsWith("SarapisUi");
            isCollectivite = source.endsWith("COLL-CVDL");
        }
        EnumCategorie enumCat = EnumCategorie.fromString(databaseUser.getCategorie());
        EnumPublic ep = EnumPublic.resolve(enumCat, ds, isLocalUser, isCollectivite);
        List<RelationEleveContact> apprentisList = getApprentis(ldapUser, databaseUser);
        // Donne les parents d'un élève
        List<RelationEleveContact> respEleves = relationEleveService.allRelationEleves(uid);
        // Donne les élèves d'un parent
        List<RelationEleveContact> eleves = relationEleveService.allEleveEnRelation(databaseUser.getId());
        UserDto personne = new UserDto(databaseUser, ldapUser, databaseUser.getUid(), ep, apprentisList, respEleves, eleves);
        if (cache != null) {
            cache.putIfAbsent(uid, personne);
        }

        log.debug("Récupération du user : {}", uid);
        return personne;
    }

    private List<RelationEleveContact> getApprentis(LdapUserDto ldapUser, DatabaseUserDto databaseUser){
        boolean isMaitre = false;
        List<RelationEleveContact> apprentisList = new ArrayList<>();
        List<String> objectClasses = ldapUser.getAttribute("objectClass");
        isMaitre = EnumObjectClass.containsMaitre(objectClasses);
        if (isMaitre) {
            apprentisList = relationEleveService.allApprentiEnRelation(ldapUser, databaseUser);
            log.debug("Utilisateur détecté comme maître d'apprentissage [uid={}]: {} apprenti(s) trouvé(s)", ldapUser.getId(), apprentisList != null ? apprentisList.size() : 0);
        }
        log.debug("objectClass [uid={}]: isMaitre={}", ldapUser.getId(), isMaitre);
        return apprentisList;
    }

    public boolean canEditPassword(UserDto userDto) {
        if (userDto.getEnumPublic().isEduconnect()) {
            return false;
        }
        String acMailPattern = mailProperties.getAcMailPattern();
        if (acMailPattern == null){
            acMailPattern = AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT;
        }
        if (userDto.getEnumPublic() == EnumPublic.EDUCATION && userDto.getDatabaseUserDto().getEmail() != null && userDto.getDatabaseUserDto().getEmail().matches(acMailPattern)) {
            return userDto.getDatabaseUserDto().isNtPass();
        }
        return userDto.getEnumPublic().isConnectOk() || userDto.getDatabaseUserDto().isNtPass();
    }

    public boolean isEduconnect(UserDto userDto) {
        String acMailPattern = mailProperties.getAcMailPattern();
        if (acMailPattern == null){
            acMailPattern = AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT;
        }
        if (userDto.getDatabaseUserDto().getEmail() == null || userDto.getEnumPublic() != EnumPublic.EDUCATION || !userDto.getDatabaseUserDto().getEmail().matches(acMailPattern)) {
            return userDto.getEnumPublic().isEduconnect();
        }
        return false;
    }

    public boolean canEditEmail(UserDto userDto) {
        if (userDto.getEnumPublic().isEleve()){
            return true;
        }
        if (userDto.getDatabaseUserDto().getEmailPersonnel() != null && !userDto.getDatabaseUserDto().getEmailPersonnel().isEmpty()){
            return true;
        }
        return userDto.getDatabaseUserDto().getEmail() == null || userDto.getDatabaseUserDto().getEmail().isEmpty();
    }

    public boolean hasSambaPass(UserDto userDto) {
        return userDto.getEnumPublic().isPassEtab() && structureService.isReseauRecia(userDto.getDatabaseUserDto());
    }

    public List<String> buildUserPublicLinks(boolean eduConnect, boolean passEtab) {
        List<String> links = new ArrayList<>();
        if (eduConnect) {
            links.add(mceProperties.getService().getCustomParams().getLienEdu());
            if (passEtab) {
                links.add(mceProperties.getService().getCustomParams().getLienPassEtab());
            }
        } else if (passEtab) {
            links.add(mceProperties.getService().getCustomParams().getLienPassEtab());
        }
        return links;
    }

    /**
     * Produit un hash opaque (hexadécimal) à partir de l'uid réel.
     */
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


    /**
     * Lève une exeption si l'utilisateur ne peut pas changer son mail
     */
    public void validateEmailForUpdate(UserDto userDto, String newEmail) {
        if (!canEditEmail(userDto)) {
            log.warn("Audit [UPDATE_EMAIL] : REFUSÉ pour l'utilisateur [{}] - Raison : Email personnel non modifiable pour ce profil", userDto.getUid());
            throw new AccessDeniedException("Vous ne pouvez pas modifier votre email personnel");
        }
        if (!newEmail.matches(mailProperties.getRegexValideAddr())) {
            throw new IllegalArgumentException("Le format de l'adresse email n'est pas valide");
        }
        final String domain = newEmail.substring(newEmail.lastIndexOf('@') + 1);
        String[] excludedDomains = mailProperties.getRegexsDomainesExclus().split("\\s+");
        for (String excluded : excludedDomains) {
            if (domain.equalsIgnoreCase(excluded.trim())) {
                throw new IllegalArgumentException("Le domaine de l'adresse email est exclu");
            }
        }
    }

    // TODO : constantes autour des caches
    public void clearUserCaches(String uid) {
        log.info("[clearUserCaches] DEBUT uid={}", uid);
        Cache dbCache = cacheManager.getCache(CacheNames.DATABASE_PERSONNE_CACHE);
        if (dbCache != null) {
            dbCache.evict(uid);
        }
        Cache ldapCache = cacheManager.getCache(CacheNames.LDAP_PERSONNE_CACHE);
        if (ldapCache != null) {
            ldapCache.evict(uid);
        }
        Cache userCache = cacheManager.getCache(CacheNames.PERSONNE_CACHE);
        if (userCache != null) {
            userCache.evict(uid);
        }
        log.info("[clearUserCaches] FIN uid={}", uid);
    }

}
