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
package fr.recia.mce.api.escomceapi.db.dto;

import java.util.Date;

import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import org.apache.commons.lang3.StringUtils;

import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import fr.recia.mce.api.escomceapi.db.entities.CerbereEnfant;
import fr.recia.mce.api.escomceapi.db.entities.Login;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class DatabaseUserDto {

    private APersonne aPersonneBase;
    private CerbereEnfant cerbereEnfant;
    private Login loginBase;
    private StructureDTO structureDto;
    private String mailFromLdap;
    private String displayName;
    private String identifiant;
    private Date dateValidCharte;
    private String uid;
    private String mailFixe;
    private String mail;
    private boolean mailForward;
    private boolean charteValide;
    private String domaineDeConnexion;
    private EnumPublic enumPublic;
    private String codeConfirmation;

    private boolean isCfa;
    private boolean isSSHAPass;
    private boolean isNtPass;

    private boolean mailFixeConfirm;
    private boolean mailPersoConfirm;
    private String mailConfirme;
    private boolean mailFixeConfiance;

    private String nom;
    private String prenom;
    private String aliasLogin;
    private Date naissance;

    private String avatarUrl;

    public DatabaseUserDto(final APersonne aPersonne) {
        this(aPersonne, aPersonne.getAStructure());
    }

    public DatabaseUserDto(final APersonne aPersonne, final AStructure aStructure) {
        super();
        aPersonneBase = aPersonne;
        structureDto = new StructureDTO(aStructure);
    }

    public DatabaseUserDto(final APersonne aPersonne, final AStructure aStructure, final Login login) {
        super();
        aPersonneBase = aPersonne;
        structureDto = new StructureDTO(aStructure);
        this.loginBase = login;
    }

    public DatabaseUserDto(final APersonne aPersonne, final Login login) {
        this(aPersonne);
        loginBase = login;
    }

    public DatabaseUserDto(final Login login) {
        this(login.getAPersonneByAPersonneLogin());
    }

    public DatabaseUserDto(final APersonne aPersonne, final CerbereEnfant enfant, final AStructure aStructure, final Login login) {
        super();
        aPersonneBase = aPersonne;
        structureDto = new StructureDTO(aStructure);
        cerbereEnfant = enfant;
        loginBase = login;
    }

    public String getIdentifiant() {
        if (loginBase != null) {
            return loginBase.getNom();
        }
        return getUid();
    }

    public String getNom() {
        return aPersonneBase.getSn();
    }

    public String getPrenom() {
        return aPersonneBase.getGivenName();
    }

    public String getUid() {
        return aPersonneBase.getUid();
    }

    public Date getNaissance() {
        return aPersonneBase.getDateNaissance();
    }

    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((getUid() == null) ? 0 : getUid().hashCode());
        return result;
    }

    public String getDisplayName() {
        return aPersonneBase.getDisplayName();
    }

    public String getMailFixe() {
        String mail = aPersonneBase.getEmail();
        if (StringUtils.isBlank(mail)) {
            log.warn(
                    "L'utilisateur [uid={}] n'a pas d'email principal en base de données. Tentative de récupération d'un email de secours depuis l'annuaire LDAP.",
                    getUid());
            mail = getMailFromLdap();
        }

        return mail;
    }

    public String getMail() {
        return aPersonneBase.getEmailPersonnel();
    }

    public boolean isMailForward() {
        return aPersonneBase.isDoForward();
    }

    /**
     * met a jour l'url de la photo dans sarapis mais pas dans ldap pour la mise a jour dans les 2 utiliser DaoServicePersonne.setPhotoUrl
     *
     * @param url
     *            nouvelle URL de l'avatar
     */
    public void setAvatarUrl(String url) {
        aPersonneBase.setPhoto(url);
        setDateModification();
        setAvatarUrl(url);
    }

    /**
     * Modifie la date de modification.
     *
     * @param dateModification
     *            nouvelle date
     */
    public void setDateModification(final Date dateModification) {
        aPersonneBase.setDateModification(dateModification);
    }

    /**
     * Fixe la date de modification au jour courant.
     */
    public void setDateModification() {
        setDateModification(new Date());
    }

    /**
     * Fixe la date de signature de la charte.
     * @param datedesignature date de signature
     */
    public void setDateValideCharte(final Date datedesignature) {
        setDateValidCharte(datedesignature);
        setCharteValide(datedesignature != null);
        setDateModification();
    }

    /**
     *
     * @return apersonne
     */
    public APersonne getApersonne() {
        return aPersonneBase;
    }

    /**
     *
     * @return login
     */
    Login getLogin() {
        return loginBase;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DatabaseUserDto other = (DatabaseUserDto) obj;
        return StringUtils.equals(getUid(), other.getUid());
    }

    public String typeOfParent() {
        if (cerbereEnfant == null) {
            return null;
        }
        String rel = cerbereEnfant.getTypeRelation();
        if (rel == null) {
            return null;
        }
        return rel.intern();
    }

    public String lienParente() {
        if (cerbereEnfant == null) {
            return null;
        }
        String lien = cerbereEnfant.getLienParente();
        return lien == null ? null : lien.intern();
    }

    public String getSource() {
        if (aPersonneBase != null) {
            return aPersonneBase.getSource();
        }
        return null;
    }

}
