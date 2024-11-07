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
import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.factories.EnumOnglet;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.InfoGeneralDTO;
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

    private IExternalUser externalUser;
    private PersonneDTO personneDTO;

    @Autowired
    private SoffitHolder soffitHolder;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public APersonne from(@NotNull UserDTO dtObject) {
        log.debug("DTO to model of {}", dtObject);
        if (dtObject != null) {
            Optional<APersonne> optionalAPersonne = daoPersonne.findById(dtObject.getId());
            return optionalAPersonne.orElse(null);
        }
        return null;
    }

    @Override
    public UserDTO from(IExternalUser extModel, boolean withInternal) {
        log.debug("External to DTO of {}", extModel);

        PersonneDTO model = null;
        if (extModel != null && withInternal) {
            // Optional<APersonne> optionalAPersonne =
            // daoPersonne.findById(extModel.getId());
            personneDTO = getUserByUid(extModel.getId());
            personneDTO.setMailFromLdap(extModel.getEmail());
            model = personneDTO;
        }
        return from(model, extModel);
    }

    @Override
    public UserDTO from(PersonneDTO model, IExternalUser extModel) {
        if (model != null && extModel != null) {
            return new UserDTO(model.getAPersonneBase().getId(), model.getUid(), model.getDisplayName(),
                    model.getIdentifiant(),
                    model.getStructureDto().getDisplayName(),
                    model.getMailFixe(), model.getNaissance(), model.getAvatarUrl(), model.getAPersonneBase().getEtat(),
                    listMenuTab(model.getAPersonneBase().getCategorie()));

        }

        return null;
    }

    @Override
    public UserDTO from(@NotNull PersonneDTO model) {

        log.debug("Model to DTO of {}", model);
        externalUser = getUserLdap(model.getUid());
        return from(model, externalUser);
    }

    @Override
    public UserDTO from(@NotNull String uid) {

        log.debug("from uid to DTO of {}", uid);
        externalUser = getUserLdap(uid);

        return from(externalUser, true);
    }

    private List<String> listMenuTab(String code) {
        List<String> menu = new ArrayList<>();

        EnumCategorie enumCat = EnumCategorie.fromString(code);

        switch (enumCat) {
            case PROF:
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

    @Cacheable(cacheNames = "personneDBCache", key = "#uid")
    private PersonneDTO getUserByUid(String uid) {
        log.info("uid: {}", uid);
        PersonneDTO personne = null;

        Cache cache = cacheManager.getCache("personneDBCache");
        PersonneDTO getPersonne = cache.get(uid, PersonneDTO.class);
        if (!Objects.isNull(getPersonne)) {
            log.info("Loading personneDB cache...");
            return getPersonne;
        }

        try {
            log.info("Calcul personDB");
            personne = daoPersonne.getPersonneByUid(uid);
            cache.putIfAbsent(uid, personne);

            if (personne != null) {
                loadLdapUser(personne, uid);
            }

        } catch (Exception e) {
            log.error("error : {}", e);
        }

        log.info("getPersonne : {}", personne);
        return personne;
    }

    @Cacheable(cacheNames = "personneLDAPCache", key = "#uid")
    private IExternalUser getUserLdap(String uid) {
        IExternalUser userLdap = null;

        Cache cache = cacheManager.getCache("personneLDAPCache");

        IExternalUser getUser = cache.get(uid, IExternalUser.class);
        if (!Objects.isNull(getUser)) {
            log.info("Loading personneLDAP cache...");
            return getUser;
        }

        try {
            log.info("Calcul personLDAP");
            userLdap = getExtDao().getUserByUid(uid);
            cache.putIfAbsent(uid, userLdap);

        } catch (Exception e) {
            log.error("error : {}", e);

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

    @Override
    public InfoGeneralDTO showGeneralInfo() {

        if (personneDTO == null) {
            log.debug("user is null");
            return null;
        }

        InfoGeneralDTO infoGeneral = null;

        List<FonctionDTO> listFonctions;

        Long id = personneDTO.getAPersonneBase().getId();
        log.info("id user: {}", id);

        Collection<FonctionDTO> fonctions = fonctionRepository.findAllFonction(id);

        listFonctions = new ArrayList<>(fonctions);
        log.info("listFonctions : {}", listFonctions);

        ClasseGroupeDTO classes = classeGroupeService.calculCG(personneDTO.getExtUser());

        infoGeneral = new InfoGeneralDTO(listFonctions, classes);

        return infoGeneral;
    }

    private boolean isSubOk() {

        final boolean isOk = soffitHolder.getSub() != null && !soffitHolder.getSub().startsWith("guest");
        if (!isOk)
            log.info("User is guest : sub {}", soffitHolder.getSub());

        return isOk;
    }

    @Override
    public UserDTO getCurrentUser() {

        if (!isSubOk())
            return null;
        final UserDTO user = from(soffitHolder.getSub());

        if (user == null)
            log.info("No user found with sub: {}", soffitHolder.getSub());

        return user;
    }

}
