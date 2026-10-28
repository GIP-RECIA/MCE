package fr.recia.mce.api.escomceapi.db.services;

import fr.recia.mce.api.escomceapi.db.dto.DatabaseUserDto;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseUserService {

    public static final String DB_CACHE_NAME = "personneDBCache";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private APersonneRepository aPersonneRepository;

    /**
     * Charge un utilisateur depuis la base de données
     */
    public DatabaseUserDto getUserDatabase(String uid) {
        // Récupération dans le cache si possible
        Cache cache = cacheManager.getCache(DB_CACHE_NAME);
        if (cache != null) {
            DatabaseUserDto cached = cache.get(uid, DatabaseUserDto.class);
            if (cached != null) {
                log.debug("Cache hit pour 'personneDBCache' : Données DB chargées pour l'uid [{}]", uid);
                return cached;
            }
        }

        // Sinon on requête la BDD et on met dans le cache
        log.debug("Cache miss pour 'personneDBCache' : Récupération des données DB pour l'uid [{}]", uid);
        DatabaseUserDto personne = aPersonneRepository.getPersonneByUid(uid);
        if (personne != null) {
            if (cache != null) {
                cache.putIfAbsent(uid, personne);
            }
        }

        return personne;
    }

}
