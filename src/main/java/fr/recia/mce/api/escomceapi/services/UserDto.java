package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDto {

    private DatabaseUserDto databaseUserDto;
    private LdapUserDto ldapUserDto;
    private EnumPublic enumPublic;

}
