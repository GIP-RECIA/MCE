package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserDto {

    // Utilisateur chargé depuis la base de données
    private DatabaseUserDto databaseUserDto;
    // Utilisateur chargé depuis le LDAP
    private LdapUserDto ldapUserDto;
    // UID en direct pour pouvoir y accéder plus facilement
    private String uid;
    // Attributs calculés au moment de la création de l'utilisateur
    private EnumPublic enumPublic;
    private List<RelationEleveContact> apprentisList;
    private List<RelationEleveContact> respEleves;
    private List<RelationEleveContact> eleves;

}
