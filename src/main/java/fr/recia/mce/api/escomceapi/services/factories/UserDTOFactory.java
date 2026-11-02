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
import java.util.List;

import fr.recia.mce.api.escomceapi.services.UserDto;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.FonctionService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
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
    private ClasseGroupeService classeGroupeService;

    @Autowired
    private FonctionService fonctionService;

    @Autowired
    private MCEProperties mceProperties;

    /**
     * Méthode qui construit le DTO à afficher par le front
     */
    public UserForDisplayDTO from(UserDto userDto) {
        final DatabaseUserDto databaseUser = userDto.getDatabaseUserDto();
        final List<RelationEleveContact> apprentisList = userDto.getApprentisList();
        final List<RelationEleveContact> respEleves = userDto.getRespEleves();
        final List<RelationEleveContact> eleves = userDto.getEleves();
        final boolean eduConnect = userDto.isEduconnect();
        final boolean passEtab = userDto.isHasPasswordEtab();
        return new UserForDisplayDTO(
            databaseUser.getId(),
            databaseUser.getUid(),
            databaseUser.getDisplayName(),
            databaseUser.getGivenName(),
            databaseUser.getSn(),
            databaseUser.getCivilite(),
            databaseUser.getCategorie(),
            userDto.isCanEditMailPerso(),
            databaseUser.getUid(),
            databaseUser.getStructureDisplayName(),
            databaseUser.getEmail(),
            databaseUser.getEmailPersonnel(),
            databaseUser.getDateNaissance(),
            databaseUser.getPhoto(),
            databaseUser.getEtat(),
            userDto.isCanEditPassword(),
            buildUserPublicLinks(eduConnect, passEtab),
            showGeneralInfo(userDto.getLdapUserDto(), databaseUser),
            respEleves,
            eleves,
            apprentisList
        );
    }

    /**
     * Construit la liste des liens externes
     * - lien vers educonnect
     * - lien vers changement de mot de passe étab
     */
    private List<String> buildUserPublicLinks(boolean eduConnect, boolean passEtab) {
        List<String> links = new ArrayList<>();
        if (eduConnect) {
            links.add(mceProperties.getService().getCustomParams().getLienEdu());
        }
        if (passEtab) {
            links.add(mceProperties.getService().getCustomParams().getLienPassEtab());
        }
        return links;
    }

    /**
     * Construit un DTO avec des infos supplémentaires pour l'utilisateur (fonctions, classes, etc..)
     */
    private InfoGeneralDTO showGeneralInfo(LdapUserDto ldapUserDto, DatabaseUserDto databaseUserDto) {
        final Long id = databaseUserDto.getId();
        log.debug("Récupération des informations générales pour l'ID utilisateur : {}", id);
        final List<FonctionDTO> listFonctions = new ArrayList<>(fonctionService.getAllFonctionOfPersonne(id));
        log.debug("{} fonction(s) trouvée(s) pour l'ID utilisateur : {}", listFonctions.size(), id);
        final ClasseGroupeDTO classes = ldapUserDto != null ? classeGroupeService.calculCG(ldapUserDto) : null;
        return new InfoGeneralDTO(listFonctions, classes);
    }

}
