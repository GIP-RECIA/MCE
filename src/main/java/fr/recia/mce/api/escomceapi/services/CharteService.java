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
package fr.recia.mce.api.escomceapi.services;

import fr.recia.mce.api.escomceapi.configuration.bean.CharteProperties;
import fr.recia.mce.api.escomceapi.configuration.interceptor.bean.SoffitHolder;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.ValidationCharte;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.ValidationCharteRepository;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class CharteService {

    /**
     * Service utilisé quand une personne n'a pas de source (charte « par défaut »).
     */
    public static final String DEFAULT_SERVICE = "default";

    /**
     * Clé de charte du CSV utilisée quand le domaine d'arrivée n'est pas référencé
     * (hors requête web, hôte inconnu) : la ligne {@code default} du CSV.
     */
    public static final String DEFAULT_CHARTE_DOMAIN = "default";

    /**
     * Préfixe hôte indifférent à la plateforme : {@code www.touraine-eschool.fr} et
     * {@code touraine-eschool.fr} désignent le même domaine. Les URL de {@code charte.urls}
     * et les clés {@code dom} du CSV ne sont pas homogènes sur ce point, la comparaison se
     * fait donc sur la forme sans {@code www.}.
     */
    private static final String WWW_PREFIX = "www.";

    /**
     * Date de version de la charte utilisée si le CSV ne la fournit ni pour le domaine, ni via sa
     * ligne {@code default}.
     */
    public static final LocalDate DEFAULT_CHARTE_VERSION_DATE = LocalDate.of(2024, 1, 1);

    /**
     * Attribut LDAP listant les domaines (hôtes de sites) rattachés à une personne.
     */
    public static final String ESCO_DOMAINES_ATTRIBUTE = "ESCODomaines";

    private static final String LDAP_CACHE_NAME = "personneLDAPCache";

    @Autowired
    private CharteProperties charteProperties;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private ValidationCharteRepository validationCharteRepository;

    @Autowired
    private SoffitHolder soffitHolder;

    @Autowired
    private IExternalUserDao externalUserDao;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Résout le service de charte d'une personne : le domaine du site d'où elle arrive
     * (hôte de l'URL, cf. {@link SoffitHolder#getArrivalHost()}), sinon — hors requête web —
     * sa source (ex. COLL-37), sinon {@link #DEFAULT_SERVICE}.
     *
     * <p>Règle du domaine d'arrivée (faill-open) : l'hôte doit être référencé dans la
     * configuration ({@code charte.domains} puis repli sur {@code charte.urls}) ET figuré
     * parmi les domaines de la personne ({@code ESCODomaines} en annuaire). Dans le cas
     * contraire on journalise en warning et on fait signer la charte par défaut
     * ({@link #DEFAULT_SERVICE}) — on ne refuse jamais l'utilisateur.</p>
     */
    public String resolveService(APersonne person) {
        if (person == null || person.getId() == null) {
            return DEFAULT_SERVICE;
        }
        String arrivalHost = soffitHolder != null ? soffitHolder.getArrivalHost() : null;
        if (StringUtils.isNotBlank(arrivalHost)) {
            return resolveServiceForArrival(person, arrivalHost.trim().toLowerCase(Locale.ROOT));
        }
        // Hors requête web (tests, flux internes) : comportement historique basé sur la source.
        if (StringUtils.isBlank(person.getSource())) {
            return DEFAULT_SERVICE;
        }
        return person.getSource().trim();
    }

    private String resolveServiceForArrival(APersonne person, String host) {
        String key = firstNonNull(
                lookupHost(charteProperties.getDomains(), host),
                lookupKeyByUrlHost(host)
        );
        if (key == null) {
            log.warn("[CHARTE][DOMAINE] l'hôte '{}' (uid={}) n'est référencé ni dans charte.domains ni dans charte.urls → charte par défaut",
                    host, person.getUid());
            return DEFAULT_SERVICE;
        }
        if (!isHostRattache(person, host)) {
            return DEFAULT_SERVICE;
        }
        log.info("[CHARTE][DOMAINE] uid={} arrivée depuis l'hôte '{}' → service '{}'", person.getUid(), host, key);
        return key;
    }

    /**
     * Domaine de charte d'une personne : l'hôte du site d'arrivée s'il est rattaché à la
     * personne, sinon {@link #DEFAULT_CHARTE_DOMAIN} (pas d'hôte d'arrivée hors requête web).
     * C'est cette valeur qui indexe la colonne {@code dom} du CSV des versions.
     */
    public String resolveCharteDomain(APersonne person) {
        if (person == null) {
            return DEFAULT_CHARTE_DOMAIN;
        }
        String host = arrivalHost();
        if (StringUtils.isBlank(host)) {
            return DEFAULT_CHARTE_DOMAIN;
        }
        String normalized = normalizeHost(host);
        if (!isHostRattache(person, normalized)) {
            return DEFAULT_CHARTE_DOMAIN;
        }
        return normalized;
    }

    /**
     * Règle de rattachement : l'hôte d'arrivée doit figurer parmi les domaines de la personne
     * ({@code ESCODomaines} en annuaire). L'annuaire indisponible ({@code null}) est traité
     * comme un rattachement refusé : on retombe alors sur la charte par défaut.
     */
    private boolean isHostRattache(APersonne person, String host) {
        List<String> personDomains = personDomains(person.getUid());
        if (personDomains == null) {
            log.warn("[CHARTE][DOMAINE] domaines (ESCODomaines) indéterminés pour l'uid={} (annuaire indisponible) → charte par défaut pour l'hôte '{}'",
                    person.getUid(), host);
            return false;
        }
        if (!containsHost(personDomains, host)) {
            log.warn("[CHARTE][DOMAINE] l'hôte '{}' n'est pas rattaché à l'uid={} (domaines de la personne={}) → charte par défaut (signature non refusée)",
                    host, person.getUid(), personDomains);
            return false;
        }
        return true;
    }

    /**
     * Recherche une clé de configuration par hôte, en tolérant le préfixe {@code www.}
     * et la casse des deux côtés.
     */
    private String lookupHost(Map<String, String> mapping, String host) {
        if (mapping == null || host == null) {
            return null;
        }
        String direct = mapping.get(host);
        if (direct != null) {
            return direct;
        }
        String needle = withoutWww(host);
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey() != null && needle.equals(withoutWww(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Contient un hôte dans une liste de domaines, en tolérant le préfixe {@code www.}
     * et la casse des deux côtés.
     */
    private boolean containsHost(List<String> hosts, String host) {
        if (hosts == null || host == null) {
            return false;
        }
        String needle = withoutWww(host);
        for (String candidate : hosts) {
            if (candidate != null && needle.equals(withoutWww(candidate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forme de comparaison d'un hôte : minuscules, sans préfixe {@code www.}.
     */
    private String withoutWww(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(WWW_PREFIX) ? normalized.substring(WWW_PREFIX.length()) : normalized;
    }

    private String normalizeHost(String host) {
        return host == null ? null : host.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Recherche la clé de charte dont l'URL (value de {@code charte.urls}) porte l'hôte donné.
     */
    private String lookupKeyByUrlHost(String host) {
        Map<String, String> urls = charteProperties.getUrls() != null ? charteProperties.getUrls() : Map.of();
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            String urlHost = urlHost(entry.getValue());
            if (withoutWww(host).equals(withoutWww(urlHost))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String urlHost(String url) {
        if (url == null) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /**
     * Domaines (hôtes de sites) de la personne depuis l'annuaire via l'attribut
     * {@value #ESCO_DOMAINES_ATTRIBUTE}, en minuscules. Retourne {@code null} si l'annuaire
     * est indisponible (le contrôle fait alors faill-open sur la charte par défaut).
     */
    private List<String> personDomains(String uid) {
        IExternalUser user = loadLdapUserCached(uid);
        if (user == null) {
            return null;
        }
        List<String> domains = user.getAttribute(ESCO_DOMAINES_ATTRIBUTE);
        if (domains == null) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>(domains.size());
        for (String domain : domains) {
            normalized.add(domain.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private IExternalUser loadLdapUserCached(String uid) {
        if (externalUserDao == null || uid == null) {
            return null;
        }
        Cache cache = cacheManager != null ? cacheManager.getCache(LDAP_CACHE_NAME) : null;
        if (cache != null) {
            IExternalUser hit = cache.get(uid, IExternalUser.class);
            if (hit != null) {
                return hit;
            }
        }
        try {
            IExternalUser user = externalUserDao.getUserByUid(uid);
            if (cache != null && user != null) {
                cache.putIfAbsent(uid, user);
            }
            return user;
        } catch (Exception e) {
            log.warn("[CHARTE][DOMAINE] échec du chargement ESCODomaines pour l'uid={} : {}", uid, e.getMessage());
            return null;
        }
    }

    /**
     * Retourne la date de version de la charte à enregistrer pour un domaine (colonne {@code dom}
     * du CSV, début de l'URL du site). Le préfixe {@code www.} est indifférent : la clé exacte est
     * essayée en premier, puis la même clé sans {@code www.}. Priorité : CSV
     * ({@code charte/chartes.csv}), puis ligne {@code default} du CSV, puis
     * {@link #DEFAULT_CHARTE_VERSION_DATE}.
     */
    public Date getCharteVersionDate(String dom) {
        Map<String, String> versions = charteProperties != null ? charteProperties.getVersions() : null;
        String version = lookupVersion(versions, dom);
        if (version == null) {
            version = lookupVersion(versions, DEFAULT_CHARTE_DOMAIN);
            if (version != null) {
                log.info("[CHARTE][VERSION] domaine '{}' absent du CSV → repli sur la version 'default' ({})",
                        dom, version);
            }
        }
        if (version == null || version.isBlank()) {
            log.warn("[CHARTE][VERSION] version absente du CSV pour le domaine '{}' → défaut {}", dom, DEFAULT_CHARTE_VERSION_DATE);
            return java.sql.Date.valueOf(DEFAULT_CHARTE_VERSION_DATE);
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(version));
        } catch (Exception e) {
            log.warn("[CHARTE][VERSION] date de version invalide '{}' (domaine={}) → défaut {}", version, dom, DEFAULT_CHARTE_VERSION_DATE);
            return java.sql.Date.valueOf(DEFAULT_CHARTE_VERSION_DATE);
        }
    }

    /**
     * Variante personne : résout le domaine d'arrivée puis délègue à
     * {@link #getCharteVersionDate(String)}. Les deux points d'appel de la version (signature et
     * contrôle de re-signature) passent par ici, pour que la version enregistrée et la version
     * comparée soient toujours résolues de la même façon.
     */
    public Date getCharteVersionDateFor(APersonne person) {
        return getCharteVersionDate(resolveCharteDomain(person));
    }

    /**
     * Cherche la version d'un domaine dans le CSV, clé exacte puis clé sans {@code www.}.
     */
    private String lookupVersion(Map<String, String> versions, String dom) {
        if (versions == null || dom == null) {
            return null;
        }
        String direct = versions.get(dom);
        if (direct != null) {
            return direct;
        }
        String needle = withoutWww(dom);
        for (Map.Entry<String, String> entry : versions.entrySet()) {
            if (entry.getKey() != null && needle.equals(withoutWww(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Résout l'URL de la charte propre au service de l'utilisateur (domaine du site d'arrivée,
     * sinon sa source), depuis {@code charte.urls} (exact puis préfixe, sinon {@code charte.default-url}).
     */
    public String getCharteUrl(String uid) {
        log.info("[CHARTE][URL] getCharteUrl(uid={}) — defaultUrl={}", uid, charteProperties.getDefaultUrl());
        if (uid == null || uid.isBlank()) {
            log.warn("[CHARTE][URL] uid null ou vide → defaultUrl");
            return charteProperties.getDefaultUrl();
        }

        APersonne person = findPerson(uid);
        if (person == null) {
            log.warn("[CHARTE][URL] personne introuvable pour uid={} → defaultUrl", uid);
            return charteProperties.getDefaultUrl();
        }

        try {
            String serviceId = resolveService(person);
            log.info("[CHARTE][URL] uid={} → serviceId='{}' (urls dispo={})", uid, serviceId, charteProperties.getUrls().keySet());
            if (serviceId == null || serviceId.isBlank()) {
                log.warn("[CHARTE][URL] serviceId absent/vide pour uid={} → defaultUrl", uid);
                return charteProperties.getDefaultUrl();
            }

            String charteUrl = charteProperties.getUrls().get(serviceId);
            if (charteUrl != null) {
                log.info("[CHARTE][URL] uid={} → service exact '{}' → {}", uid, serviceId, charteUrl);
                return charteUrl;
            }

            String prefix = serviceId.contains("-") ? serviceId.substring(0, serviceId.indexOf('-')) : serviceId;
            log.info("[CHARTE][URL] uid={} → pas de match exact pour '{}', essai du préfixe '{}'", uid, serviceId, prefix);
            charteUrl = charteProperties.getUrls().get(prefix);
            if (charteUrl != null) {
                log.info("[CHARTE][URL] uid={} → préfixe '{}' → {}", uid, prefix, charteUrl);
                return charteUrl;
            }
            log.warn("[CHARTE][URL] uid={} → aucun match pour serviceId='{}' ni préfixe='{}' → defaultUrl", uid, serviceId, prefix);
        } catch (Exception e) {
            log.warn("Impossible de résoudre l'URL de la charte pour l'uid [{}] : {}", uid, e.getMessage());
        }

        return charteProperties.getDefaultUrl();
    }

    /**
     * Règle unique du service : la charte est requise tant qu'aucune validation n'est enregistrée
     * pour l'utilisateur et son service courant (table {@code validationcharteservice}), ou lorsque
     * la version signée ne correspond plus à la version courante du domaine d'arrivée
     * ({@code charte/chartes.csv}, colonne {@code version} indexée par {@code dom}) : une charte
     * mise à jour impose une re-signature. Ne touche pas la base : l'entité est déjà chargée.
     */
    public boolean isCharteRequired(APersonne person) {
        if (person == null || person.getId() == null) {
            return true;
        }
        String serviceId = resolveService(person);
        String domain = resolveCharteDomain(person);
        try {
            ValidationCharte validation = validationCharteRepository.findByApersonneIdAndServiceId(person.getId(), serviceId);
            if (validation == null) {
                return true;
            }
            Date currentVersion = getCharteVersionDate(domain);
            Date signedVersion = validation.getCharterVersionDate();
            if (signedVersion != null && !signedVersion.equals(currentVersion)) {
                log.info("[CHARTE][VERSION] uid={} service={} domaine={} : charte signée pour la version {} mais version courante du CSV = {} → re-signature requise",
                        person.getUid(), serviceId, domain, signedVersion, currentVersion);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Impossible de vérifier la validation de la charte pour l'uid [{}] (service={}, domaine={}) : {}",
                    person.getUid(), serviceId, domain, e.getMessage());
            return true;
        }
    }

    /**
     * Variante par uid : charge la personne puis délègue à la règle commune
     * {@link #isCharteRequired(APersonne)}.
     */
    public boolean isCharteRequired(String uid) {
        log.info("[CHARTE][REQUIRED] isCharteRequired(uid={})", uid);
        if (uid == null || uid.isBlank()) {
            log.warn("[CHARTE][REQUIRED] uid null ou vide → charte requise");
            return true;
        }

        APersonne person = findPerson(uid);
        if (person == null) {
            log.warn("[CHARTE][REQUIRED] personne introuvable pour uid={} → charte requise", uid);
            return true;
        }

        boolean required = isCharteRequired(person);
        log.info("[CHARTE][REQUIRED] uid={} → service={} (hôte d'arrivée={}) → charte requise ? {}", uid, resolveService(person), arrivalHost(), required);
        return required;
    }

    private String arrivalHost() {
        return soffitHolder != null ? soffitHolder.getArrivalHost() : null;
    }

    /**
     * Charge la personne par uid pour les contrôles de charte ; {@code null} si l'uid ne correspond
     * à aucune personne ou si le chargement échoue (l'appelant applique alors son geste de repli).
     * Factorise le chargement partagé par {@link #getCharteUrl(String)} et
     * {@link #isCharteRequired(String)}.
     */
    private APersonne findPerson(String identifiant) {
        try {
            return aPersonneRepository.findByLogin(identifiant);
        } catch (Exception e) {
            log.warn("Impossible de charger la personne pour l'identifiant [{}] : {}", identifiant, e.getMessage());
            return null;
        }
    }
}