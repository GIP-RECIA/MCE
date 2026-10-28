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
package fr.recia.mce.api.escomceapi.services.factories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO.DomSource;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.EnumObjectClass;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.InfoGeneralDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserForDisplayDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@Getter
public class UserDTOFactory {

    // TODO : à mettre en conf
    private static final String AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT = "[^@]+@ac-orleans-tours.fr";

    @Autowired
    private transient APersonneRepository daoPersonne;

    @Autowired
    private transient FonctionRepository fonctionRepository;

    @Autowired
    private transient IExternalUserDao extDao;

    @Autowired
    private IClasseGroupeService classeGroupeService;

    @Autowired
    private IRelationEleveService iRelationEleveService;

    @Autowired
    private ExternalUserHelper extUserHelper;

    @Autowired
    private IStructureService structureService;

    @Autowired
    private FonctionService fonctionService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    private Pattern groupsWithSSHAPassword;
    private Pattern groupsWithNtPassword;

    public UserDTOFactory(MCEProperties mceProperties) {
        this.mceProperties = mceProperties;
        String regex = mceProperties.getService().getCustomParams().getRegexGroupsWithSshaPass();
        if (regex != null) {
            this.groupsWithSSHAPassword = Pattern.compile(regex);
        }
        String ntRegex = mceProperties.getService().getCustomParams().getRegexGroupsWithSambaNt();
        if (ntRegex != null) {
            this.groupsWithNtPassword = Pattern.compile(ntRegex);
        }
    }

    public UserForDisplayDTO from(@NotNull String uid) {
        log.debug("Création d'un UserForDisplayDTO à partir de l'uid : {}", uid);
        LdapUserDto ldapUser = personneService.getUserLdap(uid);
        DatabaseUserDto databaseUser = personneService.getUserDatabase(uid);
        try {
            EnumPublic ep = evalPublic(databaseUser);
            log.debug("Profil public évalué avec succès pour l'utilisateur [uid={}] : {}", databaseUser.getUid(), ep.name());
        } catch (Exception e) {
            log.error("Échec de l'évaluation du profil public pour l'utilisateur [uid={}] - Raison : Erreur technique lors du calcul du profil | Détail : {}", databaseUser.getUid(), e.getMessage());
        }
        return from(ldapUser, databaseUser);
    }

    /**
     * Retourne le DTO prêt pour affichage
     */
    private UserForDisplayDTO from(LdapUserDto ldapUser, DatabaseUserDto databaseUser) {
        APersonne base = databaseUser.getAPersonneBase();
        if (base == null) {
            log.error("Données de base absentes pour l'utilisateur [uid={}]", databaseUser.getUid());
            return null;
        }

        // Détection objectClass AVANT résolution des relations
        boolean isMaitre = false;
        List<RelationEleveContact> apprentisList = null;
        if (ldapUser != null) {
            List<String> objectClasses = ldapUser.getAttribute("objectClass");
            isMaitre = EnumObjectClass.containsMaitre(objectClasses);
            if (isMaitre) {
                apprentisList = iRelationEleveService.allApprentiEnRelation(databaseUser.getUid());
                log.debug("Utilisateur détecté comme maître d'apprentissage [uid={}]: {} apprenti(s) trouvé(s)", databaseUser.getUid(), apprentisList != null ? apprentisList.size() : 0);
            }
            log.debug("objectClass [uid={}]: isMaitre={}", databaseUser.getUid(), isMaitre);
        }

        // isMaitre REMPLACE le profil : pas de parentEleve ni relationEleve
        List<RelationEleveContact> respEleves = isMaitre ? null : resolveRespEleves(ldapUser);
        List<RelationEleveContact> eleves = isMaitre ? null : resolveEleves(base);

        EnumPublic pub = databaseUser.getEnumPublic();
        boolean passEditable = false;
        boolean canEditEmail = false;
        boolean eduConnect = false;
        boolean passEtab = false;

        if (pub != null) {
            passEditable = computePassEditable(databaseUser, pub);
            eduConnect = computeEduConnect(databaseUser, pub);
            canEditEmail = computeCanEditEmail(pub, base, databaseUser);
            passEtab = computePassEtab(pub, databaseUser);
        }

        String resolvedEmail = resolveEmail(databaseUser, ldapUser);
        String resolvedEmailPersonnel = resolveEmailPersonnel(base);
        String etab = resolveEtablissementName(databaseUser);
        String userIdentifiant = databaseUser.getIdentifiant();
        List<String> userPublic = buildUserPublicLinks(eduConnect, passEtab);
        return new UserForDisplayDTO(
            base.getId(),
            databaseUser.getUid(),
            databaseUser.getDisplayName(),
            base.getGivenName(),
            base.getSn(),
            base.getCivilite(),
            base.getCategorie(),
            canEditEmail,
            userIdentifiant,
            etab,
            resolvedEmail,
            resolvedEmailPersonnel,
            databaseUser.getNaissance(),
            resolveAvatarUrl(base, ldapUser),
            base.getEtat(),
            passEditable,
            userPublic,
            showGeneralInfo(ldapUser, databaseUser),
            respEleves,
            eleves,
            apprentisList
        );
    }

    // TODO : sortir de la classe ça n'a rien a faire dans une factory
    public EnumPublic evalPublic(final DatabaseUserDto personne) {

        // Sortir de la méthode
        LdapUserDto ldapUser = personneService.getUserLdap(personne.getUid());

        DomSource ds = null;
        StructureDTO structure = personne.getStructureDto();
        boolean isLocalUser = false;
        boolean isCollectivite = false;
        String source = personne.getSource();

        if (structure != null) {
            try {
                ds = structure.getDomSource();
            } catch (Exception e) {
                log.error("Échec de la récupération de la source du domaine pour la structure de l'utilisateur [uid={}] - Détail : {}", personne.getUid(), e.getMessage());
            }
        }

        if (source != null) {
            isLocalUser = source.startsWith("SarapisUi");
            isCollectivite = source.endsWith("COLL-CVDL");
        }

        EnumCategorie enumCat = EnumCategorie.fromString(personne.getAPersonneBase().getCategorie());
        EnumPublic res = EnumPublic.resolve(enumCat, ds, isLocalUser, isCollectivite);
        personne.setEnumPublic(res);

        if (groupsWithSSHAPassword != null && DomSource.GIP.equals(ds)) {
            if (ldapUser != null) {
                List<String> attrs = ldapUser.getAttribute("isMemberOf");
                if (attrs != null) {
                    personne.setSSHAPass(attrs.stream().anyMatch(s -> groupsWithSSHAPassword.matcher(s).matches()));
                }
            }
        }

        if (groupsWithNtPassword != null && (res != null && res.isNtProfile() || DomSource.GIP.equals(ds))) {
            if (ldapUser != null) {
                List<String> attrs = ldapUser.getAttribute("isMemberOf");
                if (attrs != null) {
                    personne.setNtPass(attrs.stream().anyMatch(s -> groupsWithNtPassword.matcher(s).matches()));
                }
            }
        }

        return res;
    }

    private List<RelationEleveContact> resolveRespEleves(LdapUserDto extModel) {
        Collection<RelationEleveContact> col = iRelationEleveService.allRelationEleves(extModel);
        return col != null ? new ArrayList<>(col) : null;
    }

    private List<RelationEleveContact> resolveEleves(APersonne base) {
        return new ArrayList<>(iRelationEleveService.allEleveEnRelation(base.getId()));
    }

    public boolean isPasswordEditable(DatabaseUserDto model) {
        if (model == null) {
            return false;
        }
        return computePassEditable(model, model.getEnumPublic());
    }

    public boolean canResetPassword(DatabaseUserDto databaseUserDto) {
        if (databaseUserDto == null) {
            return false;
        }
        // Toujours évaluer le profil pour s'assurer que source est correcte (isEduconnect dépend de source)
        EnumPublic pub = evalPublic(databaseUserDto);
        databaseUserDto.setEnumPublic(pub);
        if (pub == null) {
            return false;
        }
        if (pub.isEduconnect()) {
            return false;
        }
        return isPasswordEditable(databaseUserDto);
    }

    private boolean computePassEditable(DatabaseUserDto model, EnumPublic pub) {
        if (pub == null) {
            return false;
        }
        if (pub.isEduconnect()) {
            return false;
        }
        String acMailPattern = mailProperties.getAcMailPattern();
        if (acMailPattern == null){
            acMailPattern = AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT;
        }
        if (pub == EnumPublic.EDUCATION && model.getMailFixe() != null && model.getMailFixe().matches(acMailPattern)) {
            return model.isNtPass();
        }
        return pub.isConnectOk() || model.isNtPass();
    }

    private boolean computeEduConnect(DatabaseUserDto model, EnumPublic pub) {
        if (pub == null) {
            return false;
        }
        String acMailPattern = mailProperties.getAcMailPattern();
        if (acMailPattern == null){
            acMailPattern = AC_ORLEANS_TOURS_MAIL_PATTERN_DEFAULT;
        }
        if (model.getMailFixe() == null || pub != EnumPublic.EDUCATION || !model.getMailFixe().matches(acMailPattern)) {
            return pub.isEduconnect();
        }
        return false;
    }

    private boolean computeCanEditEmail(EnumPublic pub, APersonne base, DatabaseUserDto model) {
        if (pub.isEleve()){
            return true;
        }
        if (base.getEmailPersonnel() != null && !base.getEmailPersonnel().isEmpty()){
            return true;
        }
        return model.getMailFixe() == null || model.getMailFixe().isEmpty();
    }

    /**
     * Indique si la personne peut renseigner/modifier un mail personnel.
     * <p>
     * Même règle que le « changement d'adresse email ». Pour un non-élève, {@code model.getMailFixe()}
     * correspond à l'email principal en base ({@code base.getEmail()}), le fallback LDAP n'existant que pour les élèves
     * (déjà traités par {@code pub.isEleve()}).
     */
    public boolean canEditEmail(EnumPublic pub, APersonne base) {
        if (pub == null) {
            return false;
        }
        return computeCanEditEmail(pub, base, new DatabaseUserDto(base));
    }

    private boolean computePassEtab(EnumPublic pub, DatabaseUserDto model) {
        return pub.isPassEtab() && structureService.isReseauRecia(model);
    }

    private String resolveEmail(DatabaseUserDto model, LdapUserDto extModel) {
        if (StringUtils.isBlank(model.getMailFromLdap()) && extModel != null) {
            model.setMailFromLdap(extModel.getEmail());
        }
        return model.getMailFixe();
    }

    private String resolveEmailPersonnel(APersonne base) {
        List<CerbereConfirmation> confirmed = cerbereConfirmationRepository.findConfirmedByPersonId(base.getId());
        if (!confirmed.isEmpty()) {
            return confirmed.get(0).getMail();
        }
        return base.getEmailPersonnel();
    }

    private String resolveEtablissementName(DatabaseUserDto model) {
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

    private List<String> buildUserPublicLinks(boolean eduConnect, boolean passEtab) {
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

    private String resolveAvatarUrl(APersonne base, LdapUserDto extModel) {
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

    private InfoGeneralDTO showGeneralInfo(LdapUserDto ldapUserDto, DatabaseUserDto databaseUserDto) {
        APersonne base = databaseUserDto.getAPersonneBase();
        List<FonctionDTO> listFonctions;
        Long id = base.getId();
        log.debug("Récupération des informations générales pour l'ID utilisateur : {}", id);
        Collection<FonctionDTO> fonctions = fonctionService.getAllFonctionOfPersonne(id);
        listFonctions = new ArrayList<>(fonctions);
        log.debug("{} fonction(s) trouvée(s) pour l'ID utilisateur : {}", listFonctions.size(), id);
        ClasseGroupeDTO classes = ldapUserDto != null ? classeGroupeService.calculCG(ldapUserDto) : null;
        return new InfoGeneralDTO(listFonctions, classes);
    }

}
