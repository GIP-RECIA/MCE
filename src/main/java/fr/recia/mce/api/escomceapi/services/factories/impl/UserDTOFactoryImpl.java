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
package fr.recia.mce.api.escomceapi.services.factories.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;

import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO.DomSource;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.factories.EnumOnglet;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import fr.recia.mce.api.escomceapi.web.dto.InfoGeneralDTO;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@Getter
public class UserDTOFactoryImpl implements IUserDTOFactory {

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

    private IExternalUser externalUser;
    private PersonneDTO personneDTO;

    private final ServiceProperties serviceProperties;

    @Autowired
    private SoffitHolder soffitHolder;

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

    public UserDTOFactoryImpl(MCEProperties mceProperties) {
        this.serviceProperties = mceProperties.getService();
        this.mceProperties = mceProperties;
        String regex = this.serviceProperties.getCustomParams().getRegexGroupsWithSshaPass();
        if (regex != null) {
            this.groupsWithSSHAPassword = Pattern.compile(regex);
        }
    }

    @Override
    public APersonne from(@NotNull UserDTO dtObject) {
        log.debug("Conversion DTO vers modèle pour {}", dtObject);
        if (dtObject != null) {
            Optional<APersonne> optionalAPersonne = daoPersonne.findById(dtObject.getId());
            return optionalAPersonne.orElse(null);
        }
        return null;
    }

    @Override
    public UserDTO from(IExternalUser extModel, boolean withInternal) {
        log.debug("Conversion modèle externe vers DTO pour {}", extModel);

        PersonneDTO model = null;
        if (extModel != null && withInternal) {
            // Optional<APersonne> optionalAPersonne =
            // daoPersonne.findById(extModel.getId());
            personneDTO = personneService.retrievePersonnebyUid(extModel.getId());
            personneDTO.setMailFromLdap(extModel.getEmail());

            // TO DO : evalPublic
            model = personneDTO;

            try {
                EnumPublic ep = evalPublic(model);
                log.debug("Profil public évalué avec succès pour l'utilisateur [uid={}] : {}", model.getUid(), ep.name());
            } catch (Exception e) {
                log.error("Échec de l'évaluation du profil public pour l'utilisateur [uid={}] - Raison : Erreur technique lors du calcul du profil | Détail : {}", model.getUid(), e.getMessage());
            }
        }
        return from(model, extModel);
    }

    private EnumPublic evalPublic(final PersonneDTO personne) {

        EnumPublic res = null;

        DomSource ds = null;
        StructureDTO structure = personne.getStructureDto();
        boolean isLocalUser = false;
        boolean isRegion = false;
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
            isRegion = source.endsWith("COLL-CVDL");
        }

        EnumCategorie enumCat = EnumCategorie.fromString(personne.getAPersonneBase().getCategorie());

        switch (enumCat) {
            case ELEVE:
                if (ds != null) {
                    switch (ds) {
                        case CFA:
                            res = EnumPublic.APPRENANT;
                            break;
                        case AC:
                            res = isLocalUser ? EnumPublic.ELEVE : EnumPublic.ELEVE_EDUC;
                            break;
                        case GIP:
                        case LA:
                        case COLL:
                        default:
                            res = EnumPublic.ELEVE;
                    }
                } else {
                    res = EnumPublic.ELEVE;
                }
                break;

            case PARENT:
                if (ds != null) {
                    if (ds == DomSource.AC) {
                        res = isLocalUser ? EnumPublic.PARENT : EnumPublic.PARENT_EDUC;
                    } else {
                        res = EnumPublic.PARENT;
                    }
                } else {
                    res = EnumPublic.PARENT;
                }
                break;

            case PROF:
                if (ds != null) {
                    switch (ds) {
                        case AC:
                            res = isLocalUser ? EnumPublic.PERSONNEL : EnumPublic.EDUCATION;
                            break;
                        case LA:
                            res = isLocalUser ? EnumPublic.PERSONNEL : EnumPublic.AGRI;
                            break;
                        case CFA:
                        case GIP:
                        case COLL:
                        default:
                            res = EnumPublic.PERSONNEL;
                    }
                } else {
                    res = EnumPublic.PERSONNEL;
                }
                break;

            case ENTREPRISE:
            case TUTEUR:
                res = EnumPublic.EXTERIEUR;
                break;

            case NON_PROF_COL_LOCAL:
                if (isRegion) {
                    res = EnumPublic.CVDL;
                    break;
                }
            case NON_PROF_ETAB:
                if (ds != null) {
                    switch (ds) {
                        case AC:
                            res = isLocalUser ? EnumPublic.PERSONNEL : EnumPublic.EDUCATION;
                            break;
                        case LA:
                            res = isLocalUser ? EnumPublic.PERSONNEL : EnumPublic.AGRI;
                            break;
                        case CFA:
                        case GIP:
                        case COLL:
                        default:
                            res = EnumPublic.PERSONNEL;
                    }
                } else {
                    res = EnumPublic.PERSONNEL;
                }
                break;

            case NON_PROF_ACAD:
                if (ds != null) {
                    switch (ds) {
                        case AC:
                            res = isLocalUser ? EnumPublic.PERSONNEL : EnumPublic.EDUCATION;
                            break;
                        case LA:
                            res = isLocalUser ? EnumPublic.PERSONNEL : EnumPublic.AGRI;
                            break;
                        // $CASES-OMITTED$
                        default:
                            res = EnumPublic.AUTRE;
                    }
                } else {
                    res = EnumPublic.AUTRE;
                }
                break;
            case AUTRE:
                res = EnumPublic.AUTRE;
                break;
        }

        personne.setEnumPublic(res);

        if (groupsWithSSHAPassword != null && DomSource.GIP.equals(ds)) {
            IExternalUser extUser = personne.getExtUser();
            if (extUser != null) {
                List<String> attrs = extUser.getAttribute("isMemberOf");
                if (attrs != null) {
                    personne.setSSHAPass(attrs.stream().anyMatch(s -> groupsWithSSHAPassword.matcher(s).matches()));
                }
            }
        }

        return res;
    }

    @Override
    public UserDTO from(PersonneDTO model, IExternalUser extModel) {

        List<RelationEleveContact> respEleves;
        List<RelationEleveContact> eleves;
        boolean passEditable = false;
        boolean canEditEmail = false;
        boolean eduConnect = false;
        boolean passEtab = false;

        structureService.getAllStructures();

        if (model != null && extModel != null) {
            APersonne base = model.getAPersonneBase();
            if (base == null) {
                log.error("Données de base absentes pour l'utilisateur [uid={}]", model.getUid());
                return null;
            }

            Collection<RelationEleveContact> respCol = iRelationEleveService.allRelationEleves(extModel);
            if (respCol != null) {
                respEleves = new ArrayList<>(respCol);
            } else {
                respEleves = null;
            }

            Collection<RelationEleveContact> elevesCol = iRelationEleveService
                    .allEleveEnRelation(base.getId());

            eleves = new ArrayList<>(elevesCol);

            EnumPublic pub = model.getEnumPublic();
            if (pub != null) {
                // Logique pour le mot de passe
                if (model.getMailFixe() == null || pub != EnumPublic.EDUCATION
                        || !model.getMailFixe().matches("[^@]+@ac-orleans-tours.fr")) {

                    passEditable = pub.isConnectOk();
                    eduConnect = pub.isEduconnect();
                }

                // Logique pour l'email (Tableau de règles)
                if (pub.isEleve()) {
                    canEditEmail = true; // Élèves : toujours autorisé
                } else if (base.getEmailPersonnel() != null && !base.getEmailPersonnel().isEmpty()) {
                    canEditEmail = true; // Utilisateurs ayant déjà saisi un email perso
                } else if (model.getMailFixe() == null || model.getMailFixe().isEmpty()) {
                    canEditEmail = true; // Utilisateurs sans email fixe
                }

                if (pub.isPassEtab()) {
                    passEtab = structureService.isReseauRecia(model);

                }

            }

            // Détermination du mail à afficher (mailFixe ou confirmé)
            String resolvedEmail = base.getEmail();
            String mailFixe = model.getMailFixe();
            boolean mailFixeConfiance = false;

            if (mailFixe != null && !mailFixe.isEmpty()) {
                String domain = mailFixe.substring(mailFixe.lastIndexOf('@') + 1);
                String[] trustedDomains = mailProperties.getDomainesConfiance().split("\\s+");
                for (String trusted : trustedDomains) {
                    if (domain.equalsIgnoreCase(trusted.trim())) {
                        mailFixeConfiance = true;
                        break;
                    }
                }
                if (pub == EnumPublic.PERSONNEL) {
                    mailFixeConfiance = true;
                }
                if (!mailFixeConfiance) {
                    List<CerbereConfirmation> confirmed = cerbereConfirmationRepository.findConfirmedByPersonId(base.getId());
                    if (!confirmed.isEmpty()) {
                        resolvedEmail = confirmed.get(0).getMail();
                    }
                }
            }

            String userIdentifiant = passEditable ? model.getIdentifiant() : null;
            List<String> userPublic = new ArrayList<>();

            if (eduConnect) {
                userPublic.add(this.serviceProperties.getCustomParams().getLienEdu());
                if (passEtab) {
                    userPublic.add(this.serviceProperties.getCustomParams().getLienPassEtab());
                }
            } else if (passEtab) {
                userPublic.add(this.serviceProperties.getCustomParams().getLienPassEtab());
            }

            String avatarUrl = null;
            if (base.getPhoto() != null) {
                // Construction dynamique : base-url + uid + /avatar0.jpg
                avatarUrl = mceProperties.getAvatar().getBaseUrl() 
                            + model.getUid() + "/avatar0.jpg";
                log.debug("URL de l'avatar générée pour l'UID [{}]: {}", model.getUid(), avatarUrl);
            }

            UserDTO user = new UserDTO(base.getId(), model.getUid(), model.getDisplayName(),
                    base.getGivenName(),
                    base.getSn(),
                    base.getCivilite(),
                    base.getCategorie(),
                    canEditEmail,
                    userIdentifiant,
                    model.getStructureDto().getDisplayName(),
                    resolvedEmail,
                    base.getEmailPersonnel(),
                    model.getNaissance(), model.getAvatarUrl(), base.getEtat(),
                    passEditable, userPublic,
                    listMenuTab(base.getCategorie()), showGeneralInfo(), respEleves, eleves, null);
            
            user.setAvatarUrl(avatarUrl);
            return user;

        }

        return null;
    }

    @Override
    public UserDTO from(@NotNull PersonneDTO model) {

        log.debug("Conversion modèle vers DTO pour {}", model);
        externalUser = personneService.retrievePersonLdap(model.getUid());
        return from(model, externalUser);
    }

    @Override
    public UserDTO from(@NotNull String uid) {

        log.debug("Conversion de l'uid vers DTO pour {}", uid);
        externalUser = personneService.retrievePersonLdap(uid);

        return from(externalUser, true);
    }

    private List<String> listMenuTab(String code) {
        List<String> menu = new ArrayList<>();

        EnumCategorie enumCat = EnumCategorie.fromString(code);

        switch (enumCat) {
            case PROF:
            case NON_PROF_ACAD:
            case NON_PROF_ETAB:
                menu.add(EnumOnglet.GENERALE.name());
                menu.add(EnumOnglet.SERVICE.name());
                break;
            case ELEVE:
                menu.add(EnumOnglet.GENERALE.name());
                menu.add(EnumOnglet.SERVICE.name());
                menu.add(EnumOnglet.PARENT_ELEVE.name());
                break;
            case PARENT:
                menu.add(EnumOnglet.SERVICE.name());
                menu.add(EnumOnglet.RELATION_ELEVE.name());
                break;
            case TUTEUR:
                menu.add(EnumOnglet.SERVICE.name());
                menu.add(EnumOnglet.APPRENTIS.name());
                break;
            default:
                menu.add(EnumOnglet.SERVICE.name());
                break;
        }

        return menu;
    }

    @Override
    public InfoGeneralDTO showGeneralInfo() {

        if (personneDTO == null) {
            log.warn("Tentative d'affichage des informations générales mais le contexte PersonneDTO global est nul.");
            return null;
        }

        APersonne base = personneDTO.getAPersonneBase();
        if (base == null) {
            log.warn("Données de base absentes pour les informations générales.");
            return null;
        }

        InfoGeneralDTO infoGeneral = null;

        List<FonctionDTO> listFonctions;

        Long id = base.getId();
        log.debug("Récupération des informations générales pour l'ID utilisateur : {}", id);

        Collection<FonctionDTO> fonctions = fonctionService.getAllFonctionOfPersonne(id);

        listFonctions = new ArrayList<>(fonctions);
        log.debug("{} fonction(s) trouvée(s) pour l'ID utilisateur : {}", listFonctions.size(), id);

        IExternalUser extUser = personneDTO.getExtUser();
        ClasseGroupeDTO classes = extUser != null ? classeGroupeService.calculCG(extUser) : null;

        infoGeneral = new InfoGeneralDTO(listFonctions, classes);

        return infoGeneral;
    }

    private boolean isSubInvalid() {

        final boolean isNotOk = soffitHolder.getSub() == null || soffitHolder.getSub().startsWith("guest");
        if (isNotOk)
            log.info("Requête refusée : l'utilisateur est un invité ou n'a pas de réclamation 'sub' (sub : {})", soffitHolder.getSub());

        return isNotOk;
    }

    @Override
    public UserDTO getCurrentUser() {

        if (isSubInvalid())
            return null;
        final UserDTO user = from(soffitHolder.getSub());

        if (user == null)
            log.warn("Utilisateur authentifié non trouvé dans le système pour le sub Soffit : {}", soffitHolder.getSub());

        return user;
    }

    @Override
    public void changePassword(String uid, PasswordChangeRequestDTO req) {

        if (isSubInvalid()) {
            throw new SecurityException("No authorization");
        }

        PersonneDTO user = personneService.retrievePersonnebyUid(uid);
        if (user == null) {
            throw new PersonneNotFoundException("Utilisateur introuvable : " + uid);
        }

        passwordService.changePassword(user, req);

        personneService.clearUserCaches(uid);
    }

}