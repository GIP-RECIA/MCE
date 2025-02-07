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
package fr.recia.mce.api.escomceapi.web.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import fr.recia.mce.api.escomceapi.web.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/personne/mce")
public class PersonneRestController {

    @Autowired
    private PersonneService personneService;

    @Autowired
    private IUserDTOFactory userDTOFactory;

    @GetMapping("/getuser")
    public ResponseEntity<PersonneDTO> getPersonneByUid() {
        PersonneDTO personne = personneService.getPersonneByUid("uid");
        log.info("personne: {}", personne);
        if (personne == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        return new ResponseEntity<>(personne, HttpStatus.OK);
    }

    @GetMapping("/ldap")
    public ResponseEntity<IExternalUser> getPersonLdap() {
        IExternalUser personne = personneService.getPersonLdap("uid");
        if (personne == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(personne, HttpStatus.OK);

    }

    @GetMapping("/")
    public ResponseEntity<UserDTO> getMCE() {

        UserDTO user = userDTOFactory.getCurrentUser();

        log.info("userDTO: {}", user);
        if (user == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(user, HttpStatus.OK);

    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getDetailEnfant(@PathVariable String id) {
        UserDTO enfant = userDTOFactory.from(id);
        if (enfant == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(enfant, HttpStatus.OK);
    }

}
