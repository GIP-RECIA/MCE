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

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.enums.EnumCategorie;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import jcifs.util.DES;
import jcifs.util.Hexdump;
import jcifs.util.MD4;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.rmi.server.UID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
@Slf4j
public class PasswordService {

    private static final int SALT_LENGTH = 8;
    private static final String PREFIXCODE = "{SSHA}";
    private static final String PREFIXCODE_ARGON2 = "{ARGON2}";
    private static final String ACTIVE_PASSWORD = PREFIXCODE + "Active==================================================";

    private static final Argon2PasswordEncoder argon2Encoder = new Argon2PasswordEncoder();

    private static final Pattern HASH_PATTERN = Pattern.compile("\\{((SSHA)|(ARGON2))\\}(.+)");

    public enum Algo {
        SSHA, ARGON2
    }

    @Autowired
    private IExternalUserDao externalUserDao;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private ExternalUserHelper externalUserHelper;



    // ---------------------------------------------------------------
    // DTO internes
    // ---------------------------------------------------------------

    /** Résultat de la génération d'un mot de passe hashé. */
    private static class PasswordResult {
        String ldapHash;
        String sambaLm;
        String sambaNt;
    }

    /**
     * Résultat du parsing d'un hash LDAP stocké.
     * Contient l'algo détecté et les données associées (digest + salt pour SSHA,
     * hash brut pour ARGON2).
     */
    private static class ParsedPassword {
        final Algo   algo;
        final byte[] digest; // SSHA uniquement
        final byte[] salt;   // SSHA uniquement
        final String hash;   // ARGON2 uniquement

        /** Constructeur SSHA */
        ParsedPassword(byte[] digest, byte[] salt) {
            this.algo   = Algo.SSHA;
            this.digest = digest;
            this.salt   = salt;
            this.hash   = null;
        }

        /** Constructeur ARGON2 */
        ParsedPassword(String hash) {
            this.algo   = Algo.ARGON2;
            this.digest = null;
            this.salt   = null;
            this.hash   = hash;
        }
    }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void changePassword(PersonneDTO person, PasswordChangeRequest request) {

    if (person == null) {
        log.error("action=CHANGE_PASSWORD | status=ERROR | reason=PERSON_NULL");
        throw new PersonneNotFoundException("Utilisateur introuvable");
    }

    String uid = person.getUid() != null ? person.getUid() : "unknown";

    try {
        validateRequest(person, request);

    } catch (WeakPasswordException e) {
        log.warn("action=CHANGE_PASSWORD | status=ERROR | uid={} | reason=WEAK_PASSWORD", uid);
        throw e;

    } catch (IllegalArgumentException e) {
        log.warn("action=CHANGE_PASSWORD | status=ERROR | uid={} | reason=INVALID_REQUEST", uid);
        throw e;
    }

    try {
        boolean ok = verifyPassword(person, request.getOldPass(), false);

        if (!ok) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }

    } catch (IllegalArgumentException e) {
        throw e;

    } catch (Exception e) {
        log.error("action=CHANGE_PASSWORD | status=FATAL | uid={} | reason=VERIFY_PASSWORD_EXCEPTION", uid, e);
        throw new RuntimeException("Erreur technique lors de la vérification", e);
    }

    try {

        // Choix de l'algo selon les groupes LDAP
//            Algo algo = requiresSSHA(person) ? Algo.SSHA : Algo.ARGON2;
        Algo algo = Algo.ARGON2;

        boolean withSamba = requiresSamba(person);

        PasswordResult result = generatePassword(request.getNewPass(), withSamba, algo);

         updatePasswordInDatabase(person, result);
         updatePasswordInLdap(uid, result.ldapHash);

        log.info("action=CHANGE_PASSWORD | status=SUCCESS | uid={} | algo={} | samba={}",
                uid, algo, withSamba);

    } catch (WeakPasswordException | IllegalArgumentException e) {
        log.warn("action=CHANGE_PASSWORD | status=ERROR | uid={} | reason=BUSINESS_ERROR", uid);
        throw e;

    } catch (Exception e) {
        log.error("action=CHANGE_PASSWORD | status=FATAL | uid={} | reason=TECHNICAL_ERROR", uid, e);
        throw new RuntimeException("Erreur technique", e);
    }
}
    // ---------------------------------------------------------------
    // Génération du hash
    // ---------------------------------------------------------------

    private PasswordResult generatePassword(String password, boolean withSamba, Algo algo) {

        PasswordResult result = new PasswordResult();

        switch (algo) {
            case SSHA:
                result.ldapHash = makeSSHA(password);
                break;
            case ARGON2:
                result.ldapHash = PREFIXCODE_ARGON2 + argon2Encoder.encode(password);
                break;
            default:
                log.warn("action=GENERATE_PASSWORD | reason=ALGO inconnue | algo = {}" ,algo );
                throw new IllegalStateException("Algo non supporté : " + algo);
        }

        if (withSamba) {
            result.sambaLm = makeLmHash(password);
            result.sambaNt = makeNtHash(password);

            log.debug("SAMBA HASH GENERATED lm={} nt={}",
                    result.sambaLm,
                    result.sambaNt);
        }

        return result;
    }


    // ---------------------------------------------------------------
    // SSHA
    // ---------------------------------------------------------------

    private String makeSSHA(String password) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            new SecureRandom().nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(password.getBytes(StandardCharsets.UTF_8));
            md.update(salt);
            byte[] digest = md.digest();

            byte[] combined = new byte[digest.length + salt.length];
            System.arraycopy(digest, 0, combined, 0, digest.length);
            System.arraycopy(salt,   0, combined, digest.length, salt.length);

            return PREFIXCODE + new String(Base64.encodeBase64(combined, false), StandardCharsets.UTF_8);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur SSHA", e);
        }
    }

    private boolean requiresSSHA(PersonneDTO person) {
        String regex = mceProperties.getService()
                .getCustomParams()
                .getRegexGroupsWithSshaPass();

        log.debug("requiresSSHA — regex configurée : '{}'", regex);

        if (regex == null || regex.isBlank()) {
            log.warn("action=REQUIRES_SSHA | status=WARN | reason=NO_REGEX_CONFIGURED | feature=SSHA_DISABLED");
            return false;
        }

        if (person.getExtUser() == null) {
            log.warn("action=REQUIRES_SSHA | status=WARN | reason=EXT_USER_NULL | uid={}", person.getUid());
            return false;
        }

        Pattern pattern = Pattern.compile(regex);
        List<String> groups = person.getExtUser()
                .getAttribute(externalUserHelper.getUserGroupAttribute());

        if (groups == null || groups.isEmpty()) {
            log.warn("action=REQUIRES_SSHA | status=WARN | reason=NO_LDAP_GROUPS | uid={}", person.getUid());
            return false;
        }

        log.debug("requiresSSHA — {} groupe(s) trouvé(s) pour uid={} :", groups.size(), person.getUid());

        boolean matched = false;
        for (String group : groups) {
            boolean matches = pattern.matcher(group).matches();
            log.debug("  → groupe='{}' | match={}", group, matches);
            if (matches) {
                matched = true;
            }
        }

        log.debug("requiresSSHA — résultat final pour uid={} : withSSHA={}", person.getUid(), matched);
        return matched;
    }

    // ---------------------------------------------------------------
    // Samba
    // ---------------------------------------------------------------

    private String makeLmHash(String password) {
        try {
            byte[] lm = getPreNTLMResponse(password);
            return Hexdump.toHexString(lm, 0, lm.length * 2).toLowerCase();
        } catch (Exception e) {
            log.error("Erreur calcul LM hash", e);
            throw new IllegalStateException("Impossible de générer le LM hash", e);
        }
    }

    private String makeNtHash(String password) {
        try {
            byte[] nt = getNTLMResponse(password);
            return Hexdump.toHexString(nt, 0, nt.length * 2).toLowerCase();
        } catch (Exception e) {
            log.warn("action=GENERATE_NT_HASH | status=ERROR | reason=NULL_RETURNED | uid impact possible");
            return null;
        }
    }

    /** Clé constante DES de l'algorithme NTLM **/
    private static final byte[] S8 = {
            (byte) 0x4b, (byte) 0x47, (byte) 0x53, (byte) 0x21,
            (byte) 0x40, (byte) 0x23, (byte) 0x24, (byte) 0x25
    };

    private static byte[] getPreNTLMResponse(String password) {
        byte[] p14 = new byte[14];
        byte[] p16 = new byte[16];
        try {
            byte[] passwordBytes = password.toUpperCase().getBytes("Cp850");
            int len = Math.min(passwordBytes.length, 14);
            System.arraycopy(passwordBytes, 0, p14, 0, len);
            E(p14, S8, p16);
            return p16;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encodage Cp850 non supporté", e);
        }
    }

    private static void E(byte[] key, byte[] data, byte[] out) {
        byte[] key7 = new byte[7];
        byte[] e8   = new byte[8];
        for (int i = 0; i < key.length / 7; i++) {
            System.arraycopy(key, i * 7, key7, 0, 7);
            DES des = new DES(key7);
            des.encrypt(data, e8);
            System.arraycopy(e8, 0, out, i * 8, 8);
        }
    }

    private byte[] getNTLMResponse(String password) {
        byte[] uni = password.getBytes(StandardCharsets.UTF_16LE);
        byte[] p16 = new byte[16];
        MD4 md4 = new MD4();
        md4.update(uni);
        try {
            md4.digest(p16, 0, 16);
        } catch (Exception e) {
            log.error("Erreur MD4 (NT hash)", e);
        }
        return p16;
    }

    private boolean requiresSamba(PersonneDTO person) {
        String regex = mceProperties.getService()
                .getCustomParams()
                .getRegexGroupsWithSambaNt();

        log.debug("requiresSamba — regex configurée : '{}'", regex);

        if (regex == null || regex.isBlank()) {
            log.warn("action=REQUIRES_SAMBA | status=WARN | reason=NO_REGEX_CONFIGURED | feature=SAMBA");
            return false;
        }

        if (person.getExtUser() == null) {
            log.warn("action=REQUIRES_SAMBA | status=WARN | reason=EXT_USER_NULL | uid={}", person.getUid());
            return false;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.error("action=REQUIRES_SAMBA | status=ERROR | reason=INVALID_REGEX | regex={}", regex, e);
            return false;
        }

        List<String> groups = person.getExtUser()
                .getAttribute(externalUserHelper.getUserGroupAttribute());

        if (groups == null || groups.isEmpty()) {
            log.warn("action=REQUIRES_SAMBA | status=WARN | reason=NO_LDAP_GROUPS | uid={}", person.getUid());
            return false;
        }

        log.debug("requiresSamba — {} groupe(s) trouvé(s) pour uid={} :", groups.size(), person.getUid());

        boolean matched = false;
        for (String group : groups) {
            boolean matches = pattern.matcher(group).find();
            log.debug("  → groupe='{}' | match={}", group, matches);
            if (matches) {
                matched = true;
            }
        }

        log.debug("requiresSamba — résultat final pour uid={} : withSamba={}", person.getUid(), matched);
        return matched;
    }

    // ---------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------

    /**
     * Analyse un hash LDAP stocké et retourne un {@link ParsedPassword} immuable.
     *
     * <p>Formats supportés :</p>
     * <ul>
     *   <li>{SSHA}… — digest SHA-1 + salt encodés en Base64</li>
     *   <li>{ARGON2}… — hash Argon2 brut</li>
     * </ul>
     *
     * <p>Pattern à 4 groupes capturants (aligné sur {@code LdapPassword}) :
     * {@code \{((SSHA)|(ARGON2))\}(.+)} — groupe(1) = algo, groupe(4) = contenu.</p>
     *
     * @param codageLdap valeur brute lue depuis la base/LDAP
     * @return {@link ParsedPassword} ou {@code null} si le format est invalide
     */
    private ParsedPassword parse(String codageLdap) {

        if (codageLdap == null || codageLdap.isBlank()) {
            log.warn("action=PARSE | status=ERROR | reason=EMPTY_LDAP_HASH");
            return null;
        }

        Matcher m = HASH_PATTERN.matcher(codageLdap.trim());
        if (!m.matches()) {
            log.warn("action=PARSE | status=ERROR | reason=HASH_REGEX_MISMATCH | input_length={}",
                    codageLdap != null ? codageLdap.length() : 0);
            return null;
        }

        Algo   algo    = Algo.valueOf(m.group(1));
        String content = m.group(4);

        switch (algo) {
            case SSHA: {
                try {
                    byte[] digestsalt = Base64.decodeBase64(content);
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    int digestSize = md.getDigestLength();

                    if (digestsalt.length < digestSize) {
                        log.warn("action=PARSE | status=WARN | reason=SSHA_PAYLOAD_TOO_SHORT | size={}", digestsalt.length);
                        return null;
                    }

                    byte[] digest = Arrays.copyOf(digestsalt, digestSize);
                    byte[] salt   = Arrays.copyOfRange(digestsalt, digestSize, digestsalt.length);

                    log.debug("parse() SSHA — digest: {} o, salt: {} o", digest.length, salt.length);
                    return new ParsedPassword(digest, salt);

                } catch (NoSuchAlgorithmException e) {
                    log.warn("action=PARSE | algo=SHA1 | event=ALGORITHM_NOT_AVAILABLE", e);
                    return null;
                }
            }

            case ARGON2: {
                log.debug("parse() ARGON2 — hash extrait");
                return new ParsedPassword(content);
            }

            default:
                log.warn("action=PARSE | status=ERROR | reason=ALGO INCONNUE — {}", algo);
                return null;
        }
    }


    // ---------------------------------------------------------------
    // Vérification
    // ---------------------------------------------------------------

    /**
     * Vérifie un mot de passe en clair contre le hash stocké.
     *
     * <p>L'algorithme est détecté automatiquement via {@link #parse(String)}.</p>
     *
     * @param personne   utilisateur concerné
     * @param input      mot de passe en clair à tester
     * @param allowPlain autorise la comparaison directe si le hash n'a pas de préfixe
     * @return {@code true} si le mot de passe correspond
     */
    public boolean verifyPassword(PersonneDTO personne, String input, boolean allowPlain) {


        if (personne == null || input == null || input.isBlank()) {
            log.warn("action=VERIFY_PASSWORD | status=ERROR | reason=INVALID_INPUT");
            return false;
        }

        String uid = personne.getUid() != null ? personne.getUid() : "unknown";

        String stored = personne.getAPersonneBase().getPassword();

        if (stored == null || stored.isBlank()) {
            log.warn("action=VERIFY_PASSWORD | status=ERROR | uid={} | reason=NO_PASSWORD_STORED", uid);
            return false;
        }

        if (stored.startsWith(ACTIVE_PASSWORD)) {
            log.warn("action=VERIFY_PASSWORD | status=ERROR | uid={} | reason=ACCOUNT_ACTIVE_NO_PASSWORD", uid);
            return false;
        }

        if (!stored.startsWith("{")) {

            if (allowPlain) {
                boolean match = MessageDigest.isEqual(
                        stored.getBytes(StandardCharsets.UTF_8),
                        input.getBytes(StandardCharsets.UTF_8)
                );

                if (!match) {
                    log.warn("action=VERIFY_PASSWORD | status=ERROR | uid={} | reason=PLAIN_PASSWORD_MISMATCH", uid);
                }

                return match;
            }

            log.warn("action=VERIFY_PASSWORD | status=ERROR | uid={} | reason=PLAIN_PASSWORD_NOT_ALLOWED", uid);
            return false;
        }

        ParsedPassword parsed = parse(stored);

        if (parsed == null) {
            log.warn("action=VERIFY_PASSWORD | status=ERROR | uid={} | reason=CORRUPTED_HASH", uid);
            return false;
        }

        boolean match;

        switch (parsed.algo) {

            case ARGON2:
                match = argon2Encoder.matches(input, parsed.hash);
                break;

            case SSHA:
                match = verifySSHA(parsed.digest, parsed.salt, input);
                break;

            default:
                log.error("action=VERIFY_PASSWORD | status=FATAL | uid={} | reason=UNKNOWN_ALGO | algo={}",
                        uid, parsed.algo);
                return false;
        }

        if (!match) {
            log.warn("action=VERIFY_PASSWORD | status=ERROR | uid={} | reason=PASSWORD_MISMATCH", uid);
        }

        return match;
    }

    /**
     * Vérifie un mot de passe contre un digest SSHA.
     *
     * <ol>
     *   <li>clair en UTF-8 (cas normal)</li>
     *   <li>{@code new String(clair.getBytes("UTF-8"), "ISO-8859-1")} — bytes UTF-8 relus en ISO</li>
     *   <li>{@code new String(clair.getBytes("ISO-8859-1"), "UTF-8")} — bytes ISO relus en UTF-8</li>
     * </ol>
     */

    private boolean verifySSHA(byte[] expectedDigest, byte[] salt, String input) {

        if (expectedDigest == null || salt == null) {
            log.warn("verifySSHA : digest ou salt null (hash corrompu)");
            return false;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            if (matchSSHA(md, expectedDigest, salt, input.getBytes(StandardCharsets.UTF_8)))
                return true;

            String v2 = new String(input.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            if (matchSSHA(md, expectedDigest, salt, v2.getBytes(StandardCharsets.UTF_8)))
                return true;

            String v3 = new String(input.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            return matchSSHA(md, expectedDigest, salt, v3.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("verifySSHA : erreur", e);
            return false;
        }
    }

    private boolean matchSSHA(MessageDigest md, byte[] expectedDigest, byte[] salt, byte[] inputBytes) {
        md.reset();
        md.update(inputBytes);
        md.update(salt);
        return Arrays.equals(expectedDigest, md.digest());
    }


    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    public void validateRequest(PersonneDTO person, PasswordChangeRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Requête invalide");
        }

        if (request.getOldPass() == null || request.getOldPass().isBlank()) {
            throw new IllegalArgumentException("Ancien mot de passe requis");
        }

        if (request.getNewPass() == null || request.getNewPass().isBlank()) {
            throw new IllegalArgumentException("Nouveau mot de passe requis");
        }

        if (request.getOldPass().equals(request.getNewPass())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien");
        }


        if (!request.getNewPass().equals(request.getConfirmPass())) {
            throw new IllegalArgumentException("La confirmation du mot de passe ne correspond pas");
        }

        isPasswordStrongEnough(request.getNewPass());
    }

    /**
     * Vérifie si le mot de passe est assez fort et lance une exception si ce n'est pas le cas.
     * @param pass Le mot de passe à valider.
     * @throws WeakPasswordException Si le mot de passe ne respecte pas les critères de sécurité.
     */
    public static void isPasswordStrongEnough(String pass) {
        if (pass == null) {
            throw new WeakPasswordException("Mot de passe requis");
        }

        if (pass.length() < 12) {
            throw new WeakPasswordException("Le mot de passe doit contenir au moins 12 caractères");
        }

        boolean hasLower = pass.matches(".*[a-z].*");
        boolean hasUpper = pass.matches(".*[A-Z].*");
        boolean hasDigit = pass.matches(".*\\d.*");
        boolean hasSymbol = pass.matches(".*[^a-zA-Z0-9].*");

        int types = 0;
        if (hasLower) types++;
        if (hasUpper) types++;
        if (hasDigit) types++;
        if (hasSymbol) types++;

        if (types < 3) {
            throw new WeakPasswordException("Le mot de passe doit contenir au moins trois types différents de caractères (minuscules, majuscules, chiffres, symboles)");
        }
    }


    // ---------------------------------------------------------------
    // dbb
    // ---------------------------------------------------------------

    private void updatePasswordInDatabase(PersonneDTO person, PasswordResult result) {
        Long id = person.getAPersonneBase().getId();
        log.debug("updatePasswordInDatabase — id={} lm={} nt={}",
                id, result.sambaLm, result.sambaNt);

        APersonne entity = aPersonneRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("action=UPDATE_DB | status=FATAL | id={} | reason=USER_NOT_FOUND_IN_DB", id);
                    return new IllegalStateException("Utilisateur introuvable en base");
                });

        log.debug("BEFORE SET — sambaLm actuel en base: {}", entity.getSambaLmpassword());

        entity.setPassword(result.ldapHash);
        entity.setSambaLmpassword(result.sambaLm);
        entity.setSambaNtpassword(result.sambaNt);
        entity.setDateModification(new Date());

        log.debug("AFTER SET — sambaLm à sauvegarder: {}", entity.getSambaLmpassword());

        APersonne saved = aPersonneRepository.saveAndFlush(entity);

        log.debug("AFTER SAVE — sambaLm sauvegardé: {}", saved.getSambaLmpassword());
    }

    private void updatePasswordInLdap(String uid, String hash) {
        try {
            externalUserDao.updatePassword(uid, hash);
        } catch (Exception e) {
            log.error("action=UPDATE_LDAP | status=FATAL | uid={} | reason=LDAP_UPDATE_FAILED", uid, e);
            throw e;
        }
    }
}