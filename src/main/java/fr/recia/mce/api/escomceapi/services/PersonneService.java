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
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.entities.ValidationCharte;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
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
import fr.recia.mce.api.escomceapi.services.structure.StructureServiceImpl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static fr.recia.mce.api.escomceapi.db.services.DatabaseUserService.DB_CACHE_NAME;
import static fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImpl.LDAP_CACHE_NAME;

@Service
@Getter
@Slf4j
public class PersonneService {

    // TODO : à mettre en conf
    private static final String AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT = "[^@]+@ac-orleans-tours.fr";

    private final MCEProperties mceProperties;
    private final CharteService charteService;
    private final APersonneRepository aPersonneRepository;
    private final ValidationCharteRepository validationCharteRepository;
    private final LdapUserDaoImpl userLdapDao;
    private final CacheManager cacheManager;
    private final LdapUserDaoImpl extDao;
    private final MailProperties mailProperties;
    private final StructureServiceImpl structureService;
    private final RelationEleveService relationEleveService;
    private final CerbereConfirmationRepository cerbereConfirmationRepository;
    private final DatabaseUserService databaseUserService;
    private Pattern groupsWithSSHAPassword;
    private Pattern groupsWithNtPassword;

    public PersonneService(MCEProperties mceProperties, MailProperties mailProperties, LdapUserDaoImpl extDao, LdapUserDaoImpl userLdapDao,
                           ValidationCharteRepository validationCharteRepository, APersonneRepository aPersonneRepository, CharteService charteService,
                           CacheManager cacheManager, StructureServiceImpl structureService, RelationEleveService relationEleveService,
                           CerbereConfirmationRepository cerbereConfirmationRepository, DatabaseUserService databaseUserService) {
        this.mceProperties = mceProperties;
        this.mailProperties = mailProperties;
        this.extDao = extDao;
        this.userLdapDao = userLdapDao;
        this.validationCharteRepository = validationCharteRepository;
        this.aPersonneRepository = aPersonneRepository;
        this.charteService = charteService;
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
        Cache cache = cacheManager.getCache("personneCache");
        if (cache != null) {
            UserDto cached = cache.get(uid, UserDto.class);
            if (cached != null) {
                log.debug("Cache hit pour 'personneCache' : Données chargées pour l'uid [{}]", uid);
                return cached;
            }
        }

        // Sinon on requête la BDD et on met dans le cache
        log.debug("Cache miss pour 'personneCache' : Récupération des données pour l'uid [{}]", uid);
        LdapUserDto ldapUser = userLdapDao.getUserLdap(uid);
        DatabaseUserDto databaseUser = databaseUserService.getUserDatabase(uid);
        StructureDTO.DomSource ds = null;
        StructureDTO structure = databaseUser.getStructureDto();
        boolean isLocalUser = false;
        boolean isCollectivite = false;
        String source = databaseUser.getSource();
        if (structure != null) {
            try {
                ds = structure.getDomSource();
            } catch (Exception e) {
                log.error("Échec de la récupération de la source du domaine pour la structure de l'utilisateur [uid={}] - Détail : {}", databaseUser.getUid(), e.getMessage());
            }
        }
        if (source != null) {
            isLocalUser = source.startsWith("SarapisUi");
            isCollectivite = source.endsWith("COLL-CVDL");
        }
        EnumCategorie enumCat = EnumCategorie.fromString(databaseUser.getAPersonneBase().getCategorie());
        EnumPublic ep = EnumPublic.resolve(enumCat, ds, isLocalUser, isCollectivite);
        UserDto personne = new UserDto(databaseUser, ldapUser, ep);
        if (cache != null) {
            cache.putIfAbsent(uid, personne);
        }

        log.debug("Récupération du user : {}", uid);
        return personne;
    }

    public List<RelationEleveContact> resolveRespEleves(LdapUserDto ldapUser) {
        Collection<RelationEleveContact> col = relationEleveService.allRelationEleves(ldapUser.getId());
        return col != null ? new ArrayList<>(col) : null;
    }

    public List<RelationEleveContact> resolveEleves(DatabaseUserDto databaseUser) {
        return new ArrayList<>(relationEleveService.allEleveEnRelation(databaseUser.getApersonne().getId()));
    }

    public boolean canEditPassword(UserDto userDto) {
        if (userDto.getEnumPublic().isEduconnect()) {
            return false;
        }
        String acMailPattern = mailProperties.getAcMailPattern();
        if (acMailPattern == null){
            acMailPattern = AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT;
        }
        if (userDto.getEnumPublic() == EnumPublic.EDUCATION && userDto.getDatabaseUserDto().getMailFixe() != null && userDto.getDatabaseUserDto().getMailFixe().matches(acMailPattern)) {
            return userDto.getDatabaseUserDto().isNtPass();
        }
        return userDto.getEnumPublic().isConnectOk() || userDto.getDatabaseUserDto().isNtPass();
    }

    public boolean computeEduConnect(UserDto userDto) {
        String acMailPattern = mailProperties.getAcMailPattern();
        if (acMailPattern == null){
            acMailPattern = AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT;
        }
        if (userDto.getDatabaseUserDto().getMailFixe() == null || userDto.getEnumPublic() != EnumPublic.EDUCATION || !userDto.getDatabaseUserDto().getMailFixe().matches(acMailPattern)) {
            return userDto.getEnumPublic().isEduconnect();
        }
        return false;
    }

    public boolean computeCanEditEmail(UserDto userDto) {
        if (userDto.getEnumPublic().isEleve()){
            return true;
        }
        if (userDto.getDatabaseUserDto().getAPersonneBase().getEmailPersonnel() != null && !userDto.getDatabaseUserDto().getAPersonneBase().getEmailPersonnel().isEmpty()){
            return true;
        }
        return userDto.getDatabaseUserDto().getMailFixe() == null || userDto.getDatabaseUserDto().getMailFixe().isEmpty();
    }

    public boolean computePassEtab(UserDto userDto) {
        return userDto.getEnumPublic().isPassEtab() && structureService.isReseauRecia(userDto.getDatabaseUserDto());
    }

    public String resolveEmail(DatabaseUserDto model, LdapUserDto extModel) {
        if (StringUtils.isBlank(model.getMailFromLdap()) && extModel != null) {
            model.setMailFromLdap(extModel.getEmail());
        }
        return model.getMailFixe();
    }

    public String resolveEmailPersonnel(APersonne base) {
        List<CerbereConfirmation> confirmed = cerbereConfirmationRepository.findConfirmedByPersonId(base.getId());
        if (!confirmed.isEmpty()) {
            return confirmed.get(0).getMail();
        }
        return base.getEmailPersonnel();
    }

    public String resolveEtablissementName(DatabaseUserDto model) {
        if (model.getStructureDto() == null)
            return null;
        try {
            return model.getStructureDto().getDisplayName();
        } catch (Exception e) {
            log.warn("Impossible de récupérer le nom de l'établissement pour l'utilisateur [uid={}] - Détail : {}",
                model.getUid(), e.getMessage());
            return null;
        }
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

    public String resolveAvatarUrl(APersonne base, LdapUserDto extModel) {
        if (base.getPhoto() != null) {
            log.debug("URL de l'avatar pour l'UID [{}]: {}", base.getUid(), base.getPhoto());
            return base.getPhoto();
        }
        if (extModel != null) {
            String avatarAttribute = mceProperties.getLdap().getUserBranch().getAvatarAttribute();
            List<String> urls = extModel.getAttribute(avatarAttribute);
            if (urls != null && !urls.isEmpty()) {
                log.debug("URL de l'avatar (fallback LDAP) pour l'UID [{}]: {}", base.getUid(), urls.get(0));
                return urls.get(0);
            }
        }
        return null;
    }


    /**
     * Reflète sur le DTO l'état de la validation de charte (date + flag) lu depuis la table dédiée
     * {@code validationcharteservice}, pour le service courant de la personne. N'écrit plus la colonne
     * {@code apersonne.validationCharte}.
     */
    private void populateCharteValidationDTO(DatabaseUserDto personne, String uid) {
        try {
            APersonne base = personne.getApersonne();
            if (base == null || base.getId() == null) {
                return;
            }
            String serviceId = charteService.resolveService(base);
            if (serviceId == null) {
                return;
            }
            ValidationCharte validation = validationCharteRepository
                .findByApersonneIdAndServiceId(base.getId(), serviceId);
            if (validation != null) {
                personne.setDateValidCharte(validation.getValidatedAt());
                personne.setCharteValide(true);
            }
        } catch (Exception e) {
            log.warn("Impossible de refléter la validation de charte sur le DTO pour l'uid [{}] : {}", uid, e.getMessage());
        }
    }

    /**
     * Produit un hash opaque (hexadécimal) à partir de l'uid réel.
     *
     * <p>Fonction déterministe et sans sens inverse pratique (l'uid n'est pas reconstructible depuis le
     * hash). Ce n'est <b>pas</b> un hash cryptographique : l'espace de sortie est borné par la largeur
     * d'un {@code int}. Elle sert à :</p>
     * <ul>
     *   <li>dériver l'emplacement de stockage des avatars ({@code groupDir = hash[0:2]} /
     *       {@code userDir = hash[2:]}), de sorte que le disque ne référence pas l'uid réel en clair ;</li>
     *   <li>fournir le token d'accès opaque (non énumérable) de l'URL publique de l'avatar
     *       {@code GET /{uid}/avatar} — voir le modèle de menace documenté dans
     *       {@code PersonneRestController#getAvatar}.</li>
     * </ul>
     *
     * @param uid
     *            L'uid réel de l'utilisateur.
     * @return Le hash hexadécimal (longueur ≥ 2 si {@code uid.length() ≥ 2}).
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
     * Vérifie que l'utilisateur a bien le droit de changer son mail
     */
    public void validateEmailForUpdate(String uid, String newEmail) {
        DatabaseUserDto databaseUserDto = aPersonneRepository.getPersonneByUid(uid);
        if (databaseUserDto == null) {
            log.error("Audit [UPDATE_EMAIL] : ÉCHEC pour l'utilisateur [{}] - Raison : Utilisateur introuvable en base de données", uid);
            throw new PersonneNotFoundException("Utilisateur introuvable en base : " + uid);
        }

        if (!canEditPersonalEmail(databaseUserDto)) {
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

    public void assertUpdateEmail(String uid, String newEmail) {
        log.info("Mise à jour de l'email pour l'utilisateur [uid={}] vers : {}", uid, newEmail);
        validateEmailForUpdate(uid, newEmail);
        clearUserCaches(uid);
        log.info("Audit [UPDATE_EMAIL] : SUCCÈS pour l'utilisateur [{}]", uid);
    }

    private boolean canEditPersonalEmail(DatabaseUserDto personne) {
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
        // Charte par service : la validation est tracée dans la table dédiée `validationcharteservice`
        // (une ligne par couple (personne, service)), et non plus dans la colonne `apersonne.validationCharte`.
        String serviceId = charteService.resolveService(entity);
        ValidationCharte validation = validationCharteRepository.findByApersonneIdAndServiceId(entity.getId(), serviceId);
        if (validation == null) {
            validation = new ValidationCharte();
            validation.setAPersonneId(entity.getId());
            validation.setServiceId(serviceId);
        }
        validation.setCharterVersionDate(charteService.getCharteVersionDateFor(entity));
        validation.setValidatedAt(new Date());
        validationCharteRepository.saveAndFlush(validation);
        syncValidationsCharteLdap(uid, entity.getId());
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
        if (AccountState.same(entity.getEtat(), AccountState.DELETE)) {
            throw new IllegalArgumentException("Ce compte a été supprimé et ne peut pas être activé : " + uid);
        }
        if (!AccountState.same(entity.getEtat(), AccountState.VALIDE)) {
            entity.setEtat(AccountState.VALIDE);
            entity.setDateModification(new Date());
            aPersonneRepository.save(entity);
            syncEtatCompteLdap(uid, AccountState.VALIDE);
            clearUserCaches(uid);
            log.info("[valideCompte] FIN uid={} -> Valide", uid);
        } else {
            log.info("[valideCompte] FIN uid={} déjà Valide", uid);
        }
    }

    /**
     * Active un compte {@code Invalide} SANS mot de passe local (profils EduConnect/SSO : ELEVE_EDUC, PARENT_EDUC,
     * EDUCATION, AGRI, CVDL...) en posant l'état {@code Valide} et le marqueur {@code {SSHA}Active} en base,
     * puis en annuaire LDAP (hash = marqueur, etatCompte = VALIDE).
     * Équivalent legacy {@code DomainServiceImpl.setEtatValidWithoutPassword} (Cerbère).
     * La base reste la source de vérité : un échec LDAP est journalisé en warning, sans bloquer l'activation.
     */
    @Transactional
    public void setEtatValidWithoutPassword(String uid) {
        log.info("[setEtatValidWithoutPassword] DEBUT uid={}", uid);
        APersonne entity = aPersonneRepository.findByUid(uid);
        if (entity == null) {
            throw new PersonneNotFoundException("Utilisateur introuvable : " + uid);
        }
        if (AccountState.same(entity.getEtat(), AccountState.DELETE)) {
            throw new IllegalArgumentException("Ce compte a été supprimé et ne peut pas être activé : " + uid);
        }

        entity.setPassword(PasswordService.ACTIVE);
        entity.setSambaLmpassword(null);
        entity.setSambaNtpassword(null);
        entity.setEtat(AccountState.VALIDE);
        entity.setDateModification(new Date());
        aPersonneRepository.save(entity);
        try {
            extDao.modifEtatLdapPassword(uid, PasswordService.ACTIVE, AccountState.VALIDE, null, null);
        } catch (Exception e) {
            log.warn("[SET_ETAT_VALID_WITHOUT_PASSWORD] Échec de la mise à jour LDAP pour uid={} : {}. La base prime, aucune action bloquante.",
                    uid, e.getMessage());
        }
        clearUserCaches(uid);
        log.info("[setEtatValidWithoutPassword] FIN uid={} -> Valide (marqueur actif)", uid);
    }

    /**
     * Synchronise l'état du compte dans l'annuaire LDAP (attribut {@code ESCOPersonEtatCompte}) avec l'état en base.
     * La mise à jour LDAP n'est pas bloquante : en cas d'échec technique, un warning est journalisé mais la base,
     * source de vérité, reste inchangée.
     *
     * @param uid
     *            identifiant de l'utilisateur
     * @param etat
     *            nouvel état à écrire dans l'annuaire ({@link AccountState})
     */
    public void syncEtatCompteLdap(String uid, String etat) {
        try {
            extDao.updateEtatCompte(uid, etat);
        } catch (Exception e) {
            log.warn("[ETAT_COMPTE_LDAP] Échec de la mise à jour de l'état LDAP ({}) pour uid={} : {}. La base prime, aucune action bloquante.",
                    etat, uid, e.getMessage());
        }
    }

    private void syncValidationsCharteLdap(String uid, Long personId) {
        try {
            List<ValidationCharte> validations = validationCharteRepository.findAllByAPersonneId(personId);
            List<String> values = formatValidationsCharte(validations);
            log.info("[VALIDATION_CHARTE_LDAP] Synchronisation uid={} : {} valeur(s) {}", uid, values.size(), values);
            extDao.updateValidationsCharteService(uid, values);
        } catch (Exception e) {
            log.warn("[VALIDATION_CHARTE_LDAP] Échec de la mise à jour des validations de charte LDAP pour uid={} : {}. La base prime, aucune action bloquante.",
                    uid, e.getMessage(), e);
        }
    }

    private List<String> formatValidationsCharte(List<ValidationCharte> validations) {
        if (validations == null || validations.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>(validations.size());
        for (ValidationCharte validation : validations) {
            if (validation == null || validation.getServiceId() == null
                    || validation.getCharterVersionDate() == null || validation.getValidatedAt() == null) {
                log.warn("[VALIDATION_CHARTE_LDAP] Validation incomplète ignorée : {}", validation);
                continue;
            }
            values.add(validation.getServiceId() + ";"
                    + formatVersionDate(validation.getCharterVersionDate()) + ";"
                    + formatDate(validation.getValidatedAt(), "yyyyMMddHHmmss'Z'"));
        }
        return values;
    }

    /**
     * Restitue la version de charte signée au format {@code yyyyMMdd}.
     * <p>C'est un <strong>jour calendaire</strong> (lu dans le CSV des chartes, stocké en colonne
     * {@code DATE}) et non un instant : il est donc rendu dans le fuseau qui l'a construit, sans
     * conversion UTC. Forcer UTC le décalait d'un jour vers l'arrière — minuit
     * Europe/Paris = veille 23:00 (hiver) ou 22:00 (été) UTC — et l'annuaire affichait
     * {@code 20240101} pour une version signée le {@code 2024-01-02} en base. Seul
     * {@code validatedAt}, qui est un instant, est rendu en UTC par {@link #formatDate}.</p>
     */
    private String formatVersionDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        formatter.setLenient(false);
        return formatter.format(date);
    }

    private String formatDate(Date date, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        formatter.setLenient(false);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }

    // TODO : constantes autour des caches
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
        Cache userCache = cacheManager.getCache("personeCache");
        if (userCache != null) {
            userCache.evict(uid);
        }
        log.info("[clearUserCaches] FIN uid={}", uid);
    }

}
