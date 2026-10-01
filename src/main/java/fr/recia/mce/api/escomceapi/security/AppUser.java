package fr.recia.mce.api.escomceapi.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class AppUser extends User {

    private final String uid;
    private final String domaine;

    public AppUser(String username, String password, Collection<? extends GrantedAuthority> authorities, String uid, String domaine) {
        super(username, password, authorities);
        this.uid = uid;
        this.domaine = domaine;
    }

}

