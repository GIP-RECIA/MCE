package fr.recia.mce.api.escomceapi.ldap.services;

import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class LdapUserService {

    public static final String LDAP_CACHE_NAME = "personneLDAPCache";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private LdapUserDaoImpl userDao;

    /**
     * Charge un utilisateur depuis le LDAP
     */
    public LdapUserDto getUserLdap(String uid) {
        // Récupération dans le cache si possible
        LdapUserDto userLdap = null;
        Cache cache = cacheManager.getCache(LDAP_CACHE_NAME);
        if (cache != null) {
            userLdap = cache.get(uid, LdapUserDto.class);
            if (!Objects.isNull(userLdap)) {
                log.debug("Cache hit pour 'personneLDAPCache' : Données LDAP chargées pour l'uid [{}]", uid);
                return userLdap;
            }
        }

        // Sinon on requête le LDAP et on met dans le cache
        try {
            log.debug("Cache miss pour 'personneLDAPCache' : Récupération des données LDAP depuis l'annuaire pour l'uid [{}]", uid);
            userLdap = userDao.getUserByUid(uid);
            if (cache != null) {
                cache.putIfAbsent(uid, userLdap);
            }
        } catch (Exception e) {
            log.error("Échec du chargement des données LDAP pour l'uid [{}] - Détail : {}", uid, e.getMessage());
        }
        return userLdap;
    }

}
