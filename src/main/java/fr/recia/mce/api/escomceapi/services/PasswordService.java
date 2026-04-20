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

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequest;
import jcifs.util.DES;
import jcifs.util.Hexdump;
import jcifs.util.MD4;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            throw new PersonneNotFoundException("Utilisateur introuvable");
        }

        String uid = person.getUid();
        log.info("Début changement mot de passe uid={}", uid);

        validateRequest(person, request);

        if (!verifyPassword(person, request.getOldPass(), false)) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }

        // TODO : déterminer l'algo selon le profil de la personne
        Algo algo = Algo.ARGON2;

        try {
            // TODO : vérifier si Samba est requis pour ce groupe
            PasswordResult result = generatePassword(request.getNewPass(), true, algo);

            log.info("PASSWORD RESULT uid={} ldapHash={} lm={} nt={}",
                    uid,
                    result.ldapHash,
                    result.sambaLm,
                    result.sambaNt);

            updatePasswordInDatabase(person, result);
            updatePasswordInLdap(uid, result.ldapHash);

            log.info("Mot de passe changé avec succès pour uid={}", uid);

        } catch (Exception e) {
            log.error("Erreur changement mot de passe uid={}", uid, e);
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
                throw new IllegalStateException("Algo non supporté : " + algo);
        }

        if (withSamba) {
            result.sambaLm = makeLmHash(password);
            result.sambaNt = makeNtHash(password);

            log.info("SAMBA HASH GENERATED lm={} nt={}",
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


    // ---------------------------------------------------------------
    // Samba
    // ---------------------------------------------------------------

    private String makeLmHash(String password) {
        try {
            byte[] lm = getPreNTLMResponse(password);
            String hex = Hexdump.toHexString(lm, 0, lm.length * 2).toLowerCase();

            log.debug("LM HASH generated raw={} hex={}", Arrays.toString(lm), hex);

            return hex;

        } catch (Exception e) {
            log.error("Erreur calcul LM hash", e);
            return "aad3b435b51404eeaad3b435b51404ee";
        }
    }

    private String makeNtHash(String password) {
        try {
            byte[] nt = getNTLMResponse(password);
            return Hexdump.toHexString(nt, 0, nt.length * 2).toLowerCase();
        } catch (Exception e) {
            log.error("Erreur calcul NT hash", e);
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
            log.error("parse() : hash LDAP vide ou null");
            return null;
        }

        Matcher m = HASH_PATTERN.matcher(codageLdap.trim());
        if (!m.matches()) {
            log.error("parse() : format invalide — {}", codageLdap);
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
                        log.warn("parse() : payload SSHA trop court ({} o) — comparaison refusée", digestsalt.length);
                        return new ParsedPassword((byte[]) null, (byte[]) null);
                    }

                    byte[] digest = Arrays.copyOf(digestsalt, digestSize);
                    byte[] salt   = Arrays.copyOfRange(digestsalt, digestSize, digestsalt.length);

                    log.debug("parse() SSHA — digest: {} o, salt: {} o", digest.length, salt.length);
                    return new ParsedPassword(digest, salt);

                } catch (NoSuchAlgorithmException e) {
                    log.error("parse() : SHA-1 indisponible", e);
                    return null;
                }
            }

            case ARGON2: {
                log.debug("parse() ARGON2 — hash extrait");
                return new ParsedPassword(content);
            }

            default:
                log.error("parse() : algo inconnu — {}", algo);
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
            return false;
        }

        String stored = personne.getAPersonneBase().getPassword();
        log.info("Password - 359 {}", stored);
        if (stored == null || stored.isBlank() || stored.startsWith(ACTIVE_PASSWORD)) {
            return false;
        }

        if (allowPlain && !stored.startsWith("{")) {
            return stored.equals(input);
        }

        ParsedPassword parsed = parse(stored);
        if (parsed == null) {
            log.warn("verifyPassword : impossible de parser le hash stocké");
            return false;
        }

        switch (parsed.algo) {

            case ARGON2:
                return argon2Encoder.matches(input, parsed.hash);

            case SSHA:
                return verifySSHA(parsed.digest, parsed.salt, input);

            default:
                return false;
        }
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
            return false;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            if (matchSSHA(md, expectedDigest, salt, input.getBytes(StandardCharsets.UTF_8)))
                return true;

            String v2 = new String(input.getBytes("UTF-8"), "ISO-8859-1");
            if (matchSSHA(md, expectedDigest, salt, v2.getBytes(StandardCharsets.UTF_8)))
                return true;


            String v3 = new String(input.getBytes("ISO-8859-1"), "UTF-8");
            return matchSSHA(md, expectedDigest, salt, v3.getBytes(StandardCharsets.UTF_8));

        } catch (UnsupportedEncodingException e) {
            log.error("verifySSHA : encodage non supporté", e);
            return false;
        } catch (NoSuchAlgorithmException e) {
            log.error("verifySSHA : SHA-1 indisponible", e);
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

    private void validateRequest(PersonneDTO person, PasswordChangeRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Requête invalide");
        }

        if (request.getOldPass() == null || request.getOldPass().isBlank()) {
            throw new IllegalArgumentException("Ancien mot de passe requis");
        }

        if (request.getNewPass() == null || request.getNewPass().isBlank()) {
            throw new IllegalArgumentException("Nouveau mot de passe requis");
        }

        if (!isPasswordStrongEnough(request.getNewPass())) {
            throw new IllegalArgumentException("Mot de passe trop faible");
        }
    }

    public static boolean isPasswordStrongEnough(String pass) {
        if (pass == null) return false;
        int score = 16;
        if (pass.matches(".*\\W.*"))        score--;
        if (pass.matches(".*\\p{Lower}.*")) score--;
        if (pass.matches(".*\\p{Upper}.*")) score--;
        if (pass.matches(".*\\d.*"))        score--;
        return pass.length() >= score;
    }


    // ---------------------------------------------------------------
    // dbb
    // ---------------------------------------------------------------

    private void updatePasswordInDatabase(PersonneDTO person, PasswordResult result) {
        Long id = person.getAPersonneBase().getId();
        log.info("updatePasswordInDatabase — id={} lm={} nt={}",
                id, result.sambaLm, result.sambaNt);

        APersonne entity = aPersonneRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable en base"));

        log.info("BEFORE SET — sambaLm actuel en base: {}", entity.getSambaLmpassword());

        entity.setPassword(result.ldapHash);
        entity.setSambaLmpassword(result.sambaLm);
        entity.setSambaNtpassword(result.sambaNt);
        entity.setDateModification(new Date());

        log.info("AFTER SET — sambaLm à sauvegarder: {}", entity.getSambaLmpassword());

        APersonne saved = aPersonneRepository.saveAndFlush(entity);

        log.info("AFTER SAVE — sambaLm sauvegardé: {}", saved.getSambaLmpassword());
    }

    private void updatePasswordInLdap(String uid, String hash) {
        externalUserDao.updatePassword(uid, hash);
    }
}