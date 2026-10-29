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
import fr.recia.mce.api.escomceapi.services.UserDto;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.relations.RelationEleveService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.repositories.CerbereConfirmationRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.PasswordService;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
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
    private ClasseGroupeService classeGroupeService;

    @Autowired
    private RelationEleveService relationEleveService;

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
    private MCEProperties mceProperties;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private CerbereConfirmationRepository cerbereConfirmationRepository;

    /**
     * Méthode qui construit le DTO à afficher par le front
     */
    public UserForDisplayDTO from(UserDto userDto) {
        final DatabaseUserDto databaseUser = userDto.getDatabaseUserDto();
        // Gestion des relations
        List<RelationEleveContact> apprentisList = userDto.getApprentisList();
        List<RelationEleveContact> respEleves = userDto.getRespEleves();
        List<RelationEleveContact> eleves = userDto.getEleves();
        // Autres attributs
        final boolean passEditable = personneService.canEditPassword(userDto);
        final boolean eduConnect = personneService.computeEduConnect(userDto);
        final boolean canEditEmail = personneService.computeCanEditEmail(userDto);
        final boolean passEtab = personneService.computePassEtab(userDto);
        final String resolvedEmail = databaseUser.getEmail();
        final String resolvedEmailPersonnel = databaseUser.getEmailPersonnel();
        final String etab = databaseUser.getStructureDisplayName();
        final String userIdentifiant = databaseUser.getUid();
        List<String> userPublic = personneService.buildUserPublicLinks(eduConnect, passEtab);
        return new UserForDisplayDTO(
            databaseUser.getId(),
            databaseUser.getUid(),
            databaseUser.getDisplayName(),
            databaseUser.getGivenName(),
            databaseUser.getSn(),
            databaseUser.getCivilite(),
            databaseUser.getCategorie(),
            canEditEmail,
            userIdentifiant,
            etab,
            resolvedEmail,
            resolvedEmailPersonnel,
            databaseUser.getDateNaissance(),
            databaseUser.getPhoto(),
            databaseUser.getEtat(),
            passEditable,
            userPublic,
            showGeneralInfo(userDto.getLdapUserDto(), databaseUser),
            respEleves,
            eleves,
            apprentisList
        );
    }

    private InfoGeneralDTO showGeneralInfo(LdapUserDto ldapUserDto, DatabaseUserDto databaseUserDto) {
        Long id = databaseUserDto.getId();
        log.debug("Récupération des informations générales pour l'ID utilisateur : {}", id);
        Collection<FonctionDTO> fonctions = fonctionService.getAllFonctionOfPersonne(id);
        List<FonctionDTO> listFonctions = new ArrayList<>(fonctions);
        log.debug("{} fonction(s) trouvée(s) pour l'ID utilisateur : {}", listFonctions.size(), id);
        ClasseGroupeDTO classes = ldapUserDto != null ? classeGroupeService.calculCG(ldapUserDto) : null;
        return new InfoGeneralDTO(listFonctions, classes);
    }

}
