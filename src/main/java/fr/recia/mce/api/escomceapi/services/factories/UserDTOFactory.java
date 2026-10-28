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

import fr.recia.mce.api.escomceapi.db.services.DatabaseUserService;
import fr.recia.mce.api.escomceapi.ldap.services.LdapUserService;
import fr.recia.mce.api.escomceapi.services.relations.RelationEleveService;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumObjectClass;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.structure.StructureServiceImpl;
import fr.recia.mce.api.escomceapi.web.dto.InfoGeneralDTO;
import fr.recia.mce.api.escomceapi.web.dto.UserForDisplayDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@Getter
public class UserDTOFactory {

    @Autowired
    private FonctionRepository fonctionRepository;

    @Autowired
    private IClasseGroupeService classeGroupeService;

    @Autowired
    private RelationEleveService iRelationEleveService;

    @Autowired
    private StructureServiceImpl structureService;

    @Autowired
    private FonctionService fonctionService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private DatabaseUserService databaseUserService;

    @Autowired
    private LdapUserService ldapUserService;

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    /**
     * Retourne le DTO prêt pour affichage
     */
    public UserForDisplayDTO from(@NotNull String uid) {
        log.debug("Création d'un UserForDisplayDTO à partir de l'uid : {}", uid);
        LdapUserDto ldapUser = ldapUserService.getUserLdap(uid);
        DatabaseUserDto databaseUser = databaseUserService.getUserDatabase(uid);
        try {
            EnumPublic ep = personneService.evalPublic(databaseUser);
            log.debug("Profil public évalué avec succès pour l'utilisateur [uid={}] : {}", databaseUser.getUid(), ep.name());
        } catch (Exception e) {
            log.error("Échec de l'évaluation du profil public pour l'utilisateur [uid={}] - Raison : Erreur technique lors du calcul du profil | Détail : {}", databaseUser.getUid(), e.getMessage());
        }
        return from(ldapUser, databaseUser);
    }

    /**
     * Méthode privée qui construit le DTO
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
                apprentisList = iRelationEleveService.allApprentiEnRelation(ldapUser.getId());
                log.debug("Utilisateur détecté comme maître d'apprentissage [uid={}]: {} apprenti(s) trouvé(s)", databaseUser.getUid(), apprentisList != null ? apprentisList.size() : 0);
            }
            log.debug("objectClass [uid={}]: isMaitre={}", databaseUser.getUid(), isMaitre);
        }

        // isMaitre REMPLACE le profil : pas de parentEleve ni relationEleve
        List<RelationEleveContact> respEleves = isMaitre ? null : personneService.resolveRespEleves(ldapUser);
        List<RelationEleveContact> eleves = isMaitre ? null : personneService.resolveEleves(databaseUser);

        EnumPublic pub = databaseUser.getEnumPublic();
        boolean passEditable = false;
        boolean canEditEmail = false;
        boolean eduConnect = false;
        boolean passEtab = false;

        if (pub != null) {
            passEditable = personneService.computePassEditable(databaseUser, pub);
            eduConnect = personneService.computeEduConnect(databaseUser, pub);
            canEditEmail = personneService.computeCanEditEmail(pub, base, databaseUser);
            passEtab = personneService.computePassEtab(pub, databaseUser);
        }

        String resolvedEmail = personneService.resolveEmail(databaseUser, ldapUser);
        String resolvedEmailPersonnel = personneService.resolveEmailPersonnel(base);
        String etab = personneService.resolveEtablissementName(databaseUser);
        String userIdentifiant = databaseUser.getIdentifiant();
        List<String> userPublic = personneService.buildUserPublicLinks(eduConnect, passEtab);
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
            personneService.resolveAvatarUrl(base, ldapUser),
            base.getEtat(),
            passEditable,
            userPublic,
            showGeneralInfo(ldapUser, databaseUser),
            respEleves,
            eleves,
            apprentisList
        );
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
