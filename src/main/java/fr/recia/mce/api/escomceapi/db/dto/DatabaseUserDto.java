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

import org.apache.commons.lang3.StringUtils;

import fr.recia.mce.api.escomceapi.db.beans.Personne;
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
public class DatabaseUserDto extends Personne {

    private APersonne aPersonneBase;
    private CerbereEnfant cerbereEnfant;
    private Login loginBase;
    private StructureDTO structureDto;
    private String mailFromLdap;

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

    @Override
    public String getIdentifiant() {
        if (loginBase != null) {
            return loginBase.getNom();
        }
        return getUid();
    }

    @Override
    public String getNom() {
        return aPersonneBase.getSn();
    }

    @Override
    public String getPrenom() {
        return aPersonneBase.getGivenName();
    }

    @Override
    public String getUid() {
        return aPersonneBase.getUid();
    }

    @Override
    public Date getNaissance() {
        return aPersonneBase.getDateNaissance();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((getUid() == null) ? 0 : getUid().hashCode());
        return result;
    }

    @Override
    public String getDisplayName() {
        return aPersonneBase.getDisplayName();
    }

    @Override
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

    @Override
    public String getMail() {
        return aPersonneBase.getEmailPersonnel();
    }

    @Override
    public boolean isMailForward() {
        return aPersonneBase.isDoForward();
    }

    /**
     * met a jour l'url de la photo dans sarapis mais pas dans ldap pour la mise a jour dans les 2 utiliser DaoServicePersonne.setPhotoUrl
     *
     * @param url
     *            nouvelle URL de l'avatar
     */
    @Override
    public void setAvatarUrl(String url) {
        aPersonneBase.setPhoto(url);
        setDateModification();
        super.setAvatarUrl(url);
    }

    @Override
    public String getAvatarUrl() {
        String url = super.getAvatarUrl();
        if (url == null && aPersonneBase != null) {
            url = aPersonneBase.getPhoto();
            super.setAvatarUrl(url);
        }
        return url;
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
     * <p>
     * Portée sur les champs métier du bean {@link Personne} ({@code dateValidCharte}/{@code charteValide}) :
     * la colonne {@code apersonne.validationCharte} n'est plus utilisée comme source de vérité depuis la
     * migration vers la table {@code validation_charte}.
     *
     * @param datedesignature date de signature
     */
    public void setDateValideCharte(final Date datedesignature) {
        super.setDateValidCharte(datedesignature);
        super.setCharteValide(datedesignature != null);
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

    @Override
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

    @Override
    public String lienParente() {
        if (cerbereEnfant == null) {
            return null;
        }
        String lien = cerbereEnfant.getLienParente();
        return lien == null ? null : lien.intern();
    }

    @Override
    public String getSource() {
        if (aPersonneBase != null) {
            return aPersonneBase.getSource();
        }
        return null;
    }

}
