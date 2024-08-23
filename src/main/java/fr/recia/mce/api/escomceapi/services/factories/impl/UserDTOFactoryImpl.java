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
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.factories.EnumOnglet;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
public class UserDTOFactoryImpl implements IUserDTOFactory {

    @Autowired
    @Getter
    private transient APersonneRepository daoPersonne;

    @Autowired
    @Getter
    private transient IExternalUserDao extDao;

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
            PersonneDTO optionalAPersonne = daoPersonne.getPersonneByUid(extModel.getId());
            optionalAPersonne.setMailFromLdap(extModel.getEmail());
            model = optionalAPersonne;
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
        IExternalUser extUser = getExtDao().getUserByUid(model.getUid());
        return from(model, extUser);
    }

    @Override
    public UserDTO from(@NotNull String uid) {

        log.debug("from uid to DTO of {}", uid);
        IExternalUser extUser = getExtDao().getUserByUid(uid);

        return from(extUser, true);
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
                menu.add(EnumOnglet.PARENT_ELEVE.name());
                menu.add(EnumOnglet.SERVICE.name());
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
}
