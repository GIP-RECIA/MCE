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
package fr.recia.mce.api.escomceapi.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.recia.mce.api.escomceapi.configuration.bean.CustomLdapProperties;
import fr.recia.mce.api.escomceapi.ldap.ExternalStructHelper;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class LDAPConfiguration {

    private final CustomLdapProperties ldapProperties;

    public LDAPConfiguration(MCEProperties mceProperties) {
        this.ldapProperties = mceProperties.getLdap();

    }

    @Bean
    public ExternalUserHelper externalUserHelper() {
        final ExternalUserHelper ldapUser = new ExternalUserHelper(
                ldapProperties.getUserBranch().getIdAttribute(),
                ldapProperties.getUserBranch().getDisplayNameAttribute(),
                ldapProperties.getUserBranch().getMailAttribute(),
                ldapProperties.getUserBranch().getSearchAttribute(),
                ldapProperties.getUserBranch().getGroupAttribute(),
                ldapProperties.getUserBranch().getEleveRelation(),
                ldapProperties.getUserBranch().getEleveTuteurEntr(),
                ldapProperties.getUserBranch().getTuteurEleves(),
                ldapProperties.getUserBranch().getEleveEnseignements(),
                ldapProperties.getUserBranch().getCodeMatiereEnseignement(),
                ldapProperties.getUserBranch().getOtherBackendAttributes(),
                ldapProperties.getUserBranch().getOtherDisplayedAttributes(),
                ldapProperties.getUserBranch().getBaseDN());
        log.debug("LdapAttributes for user configured: {}", ldapUser);

        return ldapUser;
    }

    @Bean
    public ExternalStructHelper externalStructHelper() {
        final ExternalStructHelper ldapStruct = new ExternalStructHelper(
                ldapProperties.getStructBranch().getIdAttribute(),
                ldapProperties.getStructBranch().getNameStruct(),
                ldapProperties.getStructBranch().getDisplayNameAttribute(),
                ldapProperties.getStructBranch().getTypeStruct(),
                ldapProperties.getStructBranch().getDomaines(),
                ldapProperties.getStructBranch().getStructureJointure(),
                ldapProperties.getStructBranch().getUai(),
                ldapProperties.getStructBranch().getVille(),
                ldapProperties.getStructBranch().getBaseDN());
        log.debug("LdapAttributes for structure configured: {}", ldapStruct);

        return ldapStruct;
    }
}
