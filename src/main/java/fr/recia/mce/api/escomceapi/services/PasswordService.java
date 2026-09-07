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
import fr.recia.mce.api.escomceapi.db.dto.StructureDTO;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerberePassword;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.db.repositories.CerberePasswordRepository;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.repository.IExternalUserDao;
import fr.recia.mce.api.escomceapi.services.exception.PersonneNotFoundException;
import fr.recia.mce.api.escomceapi.services.exception.WeakPasswordException;
import fr.recia.mce.api.escomceapi.services.logging.Loggers;
import fr.recia.mce.api.escomceapi.web.dto.PasswordChangeRequestDTO;
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

    private static final String VALID_ACCOUNT_STATE = AccountState.VALIDE;

    private static final int SALT_LENGTH = 8;
    private static final String PREFIXCODE = "{SSHA}";
    private static final String PREFIXCODE_ARGON2 = "{ARGON2}";
    private static final String ACTIVE_PASSWORD = PREFIXCODE + "Active==================================================";

    private static final Argon2PasswordEncoder argon2Encoder = new Argon2PasswordEncoder();

    // private static final Pattern HASH_PATTERN = Pattern.compile("\\{((SSHA)|(ARGON2))\\}(.+)");
    private static final Pattern HASH_PATTERN = Pattern.compile("\\{([A-Z0-9]+)\\}(.+)");

    public enum Algo {
        SSHA, ARGON2
    }

    @Autowired
    private IExternalUserDao externalUserDao;

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private CerberePasswordRepository cerberePasswordRepository;

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private ExternalUserHelper externalUserHelper;

    private static final Logger specialLog = LoggerFactory.getLogger(Loggers.AUDIT);

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
     * Résultat du parsing d'un hash LDAP stocké. Contient l'algo détecté et les données associées (digest + salt pour SSHA, hash brut pour ARGON2).
     */
    private static class ParsedPassword {
        final Algo algo;
        final byte[] digest; // SSHA uniquement
        final byte[] salt; // SSHA uniquement
        final String hash; // ARGON2 uniquement

        /** Constructeur SSHA */
        ParsedPassword(byte[] digest, byte[] salt) {
            this.algo = Algo.SSHA;
            this.digest = digest;
            this.salt = salt;
            this.hash = null;
        }

        /** Constructeur ARGON2 */
        ParsedPassword(String hash) {
            this.algo = Algo.ARGON2;
            this.digest = null;
            this.salt = null;
            this.hash = hash;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void changePassword(PersonneDTO person, PasswordChangeRequestDTO request) {

        if (person == null) {
            specialLog.error("Audit [CHANGE_PASSWORD] : REFUSÉ - Raison : Objet personne nul (méthode : changePassword)");
            throw new PersonneNotFoundException("Utilisateur introuvable");
        }

        String uid = person.getUid() != null ? person.getUid() : "unknown";

        String etat = person.getAPersonneBase().getEtat();
        if (!VALID_ACCOUNT_STATE.equals(etat)) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Compte non actif (etat={})", uid, etat);
            throw new IllegalArgumentException("Votre compte n'est pas dans un état valide");
        }

        boolean noOldPass = isNoOldPass(person);

        try {
            validateRequest(person, request, noOldPass);

        } catch (WeakPasswordException e) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Mot de passe trop faible", uid);
            throw e;

        } catch (IllegalArgumentException e) {
            specialLog.warn("Audit [CHANGE_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : Erreur de vérification du mot de passe | Détail : {}", uid,
                    e.getMessage());
            throw e;
        }

        if (!noOldPass) {
            try {
                boolean ok = verifyPassword(person, request.getOldPass(), false);

                if (!ok) {
                    throw new IllegalArgumentException("Ancien mot de passe incorrect");
                }

            } catch (IllegalArgumentException e) {
                throw e;

            } catch (Exception e) {
                specialLog.error(
                        "Audit [CHANGE_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : Erreur technique lors de la vérification du mot de passe | Détail : {}",
                        uid, e.getMessage());
                throw new RuntimeException("Erreur technique lors de la vérification : " + e.getMessage());
            }
        } else {
            specialLog.info(
                    "Audit [CHANGE_PASSWORD] : Contournement de la vérification de l'ancien mot de passe pour l'utilisateur [{}] - Raison : CVDL avec ntPass sans mot de passe stocké",
                    uid);
        }

        updatePassword(person, request.getNewPass(), "CHANGE_PASSWORD");
    }

    @Transactional
    public void resetPassword(PersonneDTO person, String newPassword, String confirmPassword) {
        String uid = person != null ? (person.getUid() != null ? person.getUid() : "unknown") : "null";
        specialLog.info("Audit [RESET_PASSWORD] : DEBUT pour l'utilisateur [{}]", uid);

        if (person == null) {
            specialLog.error("Audit [RESET_PASSWORD] : REFUSÉ - Raison : Objet personne nul");
            throw new PersonneNotFoundException("Utilisateur introuvable");
        }

        try {
            if (newPassword == null || newPassword.isBlank()) {
                throw new IllegalArgumentException("Nouveau mot de passe requis");
            }
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("La confirmation du mot de passe ne correspond pas");
            }
            isPasswordStrongEnough(newPassword);
            specialLog.info("Audit [RESET_PASSWORD] : validation paramètres OK pour [{}]", uid);
        } catch (WeakPasswordException e) {
            specialLog.warn("Audit [RESET_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Mot de passe trop faible", uid);
            throw e;
        } catch (IllegalArgumentException e) {
            specialLog.warn("Audit [RESET_PASSWORD] : ÉCHEC pour l'utilisateur [{}] - Raison : {}", uid, e.getMessage());
            throw e;
        }

        updatePassword(person, newPassword, "RESET_PASSWORD");
        specialLog.info("Audit [RESET_PASSWORD] : FIN pour l'utilisateur [{}]", uid);
    }

    private void updatePassword(PersonneDTO person, String newPassword, String auditPrefix) {
        String uid = person.getUid() != null ? person.getUid() : "unknown";
        try {
            specialLog.info("Audit [{}] : updatePassword DEBUT uid={}", auditPrefix, uid);

            Algo algo = Algo.ARGON2;
            boolean withSamba = requiresSamba(person);
            specialLog.info("Audit [{}] : algo={} withSamba={} uid={}", auditPrefix, algo, withSamba, uid);

            if (isPasswordAlreadyUsed(person, newPassword)) {
                throw new WeakPasswordException("Ce mot de passe a déjà été utilisé");
            }
            specialLog.info("Audit [{}] : passwordAlreadyUsed=false uid={}", auditPrefix, uid);

            PasswordResult result = generatePassword(newPassword, withSamba, algo, person.getUid());
            specialLog.info("Audit [{}] : generatePassword OK uid={}", auditPrefix, uid);

            closeLastPassword(person);
            specialLog.info("Audit [{}] : closeLastPassword OK uid={}", auditPrefix, uid);

            savePasswordToHistory(person, result.ldapHash);
            specialLog.info("Audit [{}] : savePasswordToHistory OK uid={}", auditPrefix, uid);

            updatePasswordInDatabase(person, result);
            specialLog.info("Audit [{}] : updatePasswordInDatabase OK uid={}", auditPrefix, uid);

            updatePasswordInLdap(uid, result.ldapHash);
            specialLog.info("Audit [{}] : updatePasswordInLdap OK uid={}", auditPrefix, uid);

            specialLog.info("Audit [{}] : SUCCÈS pour l'utilisateur [{}]", auditPrefix, uid);
        } catch (WeakPasswordException | IllegalArgumentException e) {
            specialLog.warn("Audit [{}] : ÉCHEC pour l'utilisateur [{}] - Raison : Erreur de logique métier | Détail : {}",
                    auditPrefix, uid, e.getMessage());
            throw e;
        } catch (Exception e) {
            specialLog.error("Audit [{}] : ABANDONNÉ pour l'utilisateur [{}] - Raison : Erreur technique | Détail : {}",
                    auditPrefix, uid, e.getMessage(), e);
            throw new RuntimeException("Erreur technique : " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Génération du hash
    // ---------------------------------------------------------------

    private PasswordResult generatePassword(String password, boolean withSamba, Algo algo, String uid) {

        PasswordResult result = new PasswordResult();

        switch (algo) {
            case SSHA :
                result.ldapHash = makeSSHA(password);
                break;
            case ARGON2 :
                result.ldapHash = PREFIXCODE_ARGON2 + argon2Encoder.encode(password);
                break;
            default :
                specialLog.warn("Audit [GENERATE_PASSWORD] : ÉCHEC - Détail : Algorithme non supporté demandé : {}", algo);
                throw new IllegalStateException("Algo non supporté : " + algo);
        }

        if (withSamba) {
            result.sambaLm = makeLmHash(password);
            result.sambaNt = makeNtHash(password);

            log.debug("Hashes samba générés pour uid={} (lm présent={}, nt présent={})", uid, result.sambaLm, result.sambaNt);
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
            System.arraycopy(salt, 0, combined, digest.length, salt.length);

            return PREFIXCODE + new String(Base64.encodeBase64(combined, false), StandardCharsets.UTF_8);

        } catch (NoSuchAlgorithmException e) {
            specialLog.error("Audit [GENERATE_PASSWORD] : ABANDONNÉ - Raison : Algorithme SHA-1 non trouvé | Détail : {}",
                    e.getMessage());
            throw new RuntimeException("Erreur SSHA : " + e.getMessage());
        }
    }

    private boolean requiresSSHA(PersonneDTO person) {
        if (person == null)
            return false;
        String uid = person.getUid() != null ? person.getUid() : "unknown";
        String regex = mceProperties.getService()
                .getCustomParams()
                .getRegexGroupsWithSshaPass();

        log.debug("vérificationSSHA — regex configurée pour uid={} : '{}'", uid, regex);

        if (regex == null || regex.isBlank()) {
            specialLog.warn("Audit [REQUIRES_SSHA] : ÉCHEC pour l'utilisateur [{}] - Raison : Aucune regex configurée pour les groupes SSHA", uid);
            return false;
        }

        if (person.getExtUser() == null) {
            specialLog.warn("Audit [REQUIRES_SSHA] : ÉCHEC pour l'utilisateur [{}] - Raison : L'objet utilisateur externe (LDAP) est nul", uid);
            return false;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            specialLog.error("Audit [REQUIRES_SSHA] : ÉCHEC pour l'utilisateur [{}] - Raison : Motif regex invalide | Détail : {}", uid, e.getMessage());
            return false;
        }

        List<String> groups = person.getExtUser()
                .getAttribute(externalUserHelper.getUserGroupAttribute());

        if (groups == null || groups.isEmpty()) {
            specialLog.warn("Audit [REQUIRES_SSHA] : ÉCHEC pour l'utilisateur [{}] - Raison : Aucun groupe LDAP trouvé pour l'utilisateur", uid);
            return false;
        }

        log.debug("vérificationSSHA — {} groupe(s) trouvé(s) pour l'uid={} :", groups.size(), person.getUid());

        boolean matched = false;
        boolean debug = log.isDebugEnabled();
        for (String group : groups) {
            boolean matches = pattern.matcher(group).matches();
            if (debug) {
                log.debug("  → groupe='{}' | correspondance={} (uid={})", group, matches, person.getUid());
            }
            if (matches) {
                matched = true;
            }
        }

        log.debug("vérificationSSHA — résultat final pour l'uid={} : withSSHA={}", person.getUid(), matched);
        return matched;
    }

    // --- SAMBA SECTION ---

    private String makeLmHash(String password) {
        try {
            byte[] lm = getPreNTLMResponse(password);
            return Hexdump.toHexString(lm, 0, lm.length * 2).toLowerCase();
        } catch (Exception e) {
            specialLog.error("Audit [GENERATE_PASSWORD] : ABANDONNÉ - Raison : La génération du hash LM a échoué | Détail : {}", e.getMessage());
            throw new RuntimeException("Erreur technique : " + e.getMessage());
        }
    }

    private String makeNtHash(String password) {
        byte[] nt = getNTLMResponse(password);
        return Hexdump.toHexString(nt, 0, nt.length * 2).toLowerCase();
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
        byte[] e8 = new byte[8];
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
            specialLog.error("Audit [GENERATE_PASSWORD] : ABANDONNÉ - Raison : L'empreinte MD4 a échoué pour NTLM | Détail : {}", e.getMessage());
            throw new RuntimeException("Erreur technique : " + e.getMessage());
        }
        return p16;
    }

    private boolean requiresSamba(PersonneDTO person) {
        if (person == null)
            return false;
        String uid = person.getUid() != null ? person.getUid() : "unknown";

        if (!isNtPasswordProfile(person)) {
            specialLog.info("Audit [REQUIRES_SAMBA] : NON pour l'utilisateur [{}] - Raison : Profil non éligible au ntPass (ni CVDL ni source GIP)", uid);
            return false;
        }

        String regex = mceProperties.getService()
                .getCustomParams()
                .getRegexGroupsWithSambaNt();

        log.debug("vérificationSamba — regex configurée pour uid={} : '{}'", uid, regex);

        if (regex == null || regex.isBlank()) {
            specialLog.warn("Audit [REQUIRES_SAMBA] : ÉCHEC pour l'utilisateur [{}] - Raison : Aucune regex configurée pour les groupes Samba NT", uid);
            return false;
        }

        if (person.getExtUser() == null) {
            specialLog.warn("Audit [REQUIRES_SAMBA] : ÉCHEC pour l'utilisateur [{}] - Raison : L'objet utilisateur externe (LDAP) est nul", uid);
            return false;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            specialLog.error("Audit [REQUIRES_SAMBA] : ÉCHEC pour l'utilisateur [{}] - Raison : Motif regex invalide | Détail : {}", uid, e.getMessage());
            return false;
        }

        List<String> groups = person.getExtUser()
                .getAttribute(externalUserHelper.getUserGroupAttribute());

        if (groups == null || groups.isEmpty()) {
            specialLog.warn("Audit [REQUIRES_SAMBA] : ÉCHEC pour l'utilisateur [{}] - Raison : Aucun groupe LDAP trouvé pour l'utilisateur", uid);
            return false;
        }

        log.debug("vérificationSamba — {} groupe(s) trouvé(s) pour uid={} :", groups.size(), person.getUid());

        boolean matched = false;
        boolean debug = log.isDebugEnabled();
        for (String group : groups) {
            boolean matches = pattern.matcher(group).find();
            if (debug) {
                log.debug("  → groupe='{}' | correspondance={} (uid={})", group, matches, person.getUid());
            }
            if (matches) {
                matched = true;
            }
        }

        log.debug("vérificationSamba — résultat final pour l'uid={} : withSamba={}", person.getUid(), matched);
        return matched;
    }

    private boolean isNtPasswordProfile(PersonneDTO person) {
        EnumPublic res = person.getEnumPublic();
        boolean isCvdl = EnumPublic.CVDL.equals(res);
        StructureDTO.DomSource ds = null;
        if (person.getStructureDto() != null) {
            try {
                ds = person.getStructureDto().getDomSource();
            } catch (Exception e) {
                log.debug("vérificationSamba — domSource indisponible pour uid={} : {}", person.getUid(), e.getMessage());
            }
        }
        boolean isGip = StructureDTO.DomSource.GIP.equals(ds);
        log.debug("vérificationSamba — éligibilité profil uid={} : enumPublic={}, domSource={}, ntPass=({} || {})",
                person.getUid(), res, ds, isCvdl, isGip);
        return isCvdl || isGip;
    }

    // ---------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------

    /**
     * Analyse un hash LDAP stocké et retourne un {@link ParsedPassword} immuable.
     *
     * <p>
     * Formats supportés :
     * </p>
     * <ul>
     * <li>{SSHA}… — digest SHA-1 + salt encodés en Base64</li>
     * <li>{ARGON2}… — hash Argon2 brut</li>
     * </ul>
     *
     * <p>
     * Pattern à 4 groupes capturants (aligné sur {@code LdapPassword}) : {@code \{((SSHA)|(ARGON2))\}(.+)} — groupe(1) = algo, groupe(4) = contenu.
     * </p>
     *
     * @param codageLdap
     *            valeur brute lue depuis la base/LDAP
     * @return {@link ParsedPassword} ou {@code null} si le format est invalide
     */
    private ParsedPassword parse(String codageLdap) {

        if (codageLdap == null || codageLdap.isBlank()) {
            specialLog.warn("Audit [PARSE] : ÉCHEC - Raison : Hash LDAP vide fourni pour l'analyse");
            return null;
        }

        Matcher m = HASH_PATTERN.matcher(codageLdap.trim());
        if (!m.matches()) {
            specialLog.warn("Audit [PARSE] : ÉCHEC - Raison : Le hash ne correspond pas au format LDAP attendu (SSHA/ARGON2) | Détail : longueur_entrée={}",
                    codageLdap.length());
            return null;
        }

        Algo algo;

        try {
            algo = Algo.valueOf(m.group(1));
        } catch (IllegalArgumentException e) {
            specialLog.error("Audit [PARSE] : ABANDONNÉ - Raison : Algorithme inconnu | Détail : algo={}", m.group(1));
            return null;
        }

        String content = m.group(2);

        switch (algo) {
            case SSHA : {
                try {
                    byte[] digestsalt = Base64.decodeBase64(content);
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    int digestSize = md.getDigestLength();

                    if (digestsalt.length < digestSize) {
                        specialLog.warn("Audit [PARSE] : ÉCHEC - Raison : La charge utile SSHA est trop courte | Détail : taille={}", digestsalt.length);
                        return null;
                    }

                    byte[] digest = Arrays.copyOf(digestsalt, digestSize);
                    byte[] salt = Arrays.copyOfRange(digestsalt, digestSize, digestsalt.length);

                    log.debug("parse() SSHA — empreinte : {} o, sel : {} o", digest.length, salt.length);
                    return new ParsedPassword(digest, salt);

                } catch (NoSuchAlgorithmException e) {
                    specialLog.error("Audit [PARSE] : ABANDONNÉ - Raison : Algorithme SHA-1 non trouvé pour l'analyse SSHA | Détail : erreur={}",
                            e.getMessage());
                    return null;
                }
            }

            case ARGON2 : {
                log.debug("parse() ARGON2 — hash extrait");
                return new ParsedPassword(content);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Vérification
    // ---------------------------------------------------------------

    /**
     * Vérifie un mot de passe en clair contre le hash stocké.
     *
     * <p>
     * L'algorithme est détecté automatiquement via {@link #parse(String)}.
     * </p>
     *
     * @param personne
     *            utilisateur concerné
     * @param input
     *            mot de passe en clair à tester
     * @param allowPlain
     *            autorise la comparaison directe si le hash n'a pas de préfixe
     * @return {@code true} si le mot de passe correspond
     */
    public boolean verifyPassword(PersonneDTO personne, String input, boolean allowPlain) {

        if (personne == null || input == null || input.isBlank()) {
            specialLog.warn("Audit [VERIFY_PASSWORD] : REFUSÉ - Raison : Entrée invalide fournie (personne ou mot de passe fourni est nul/vide)");
            return false;
        }

        String uid = personne.getUid() != null ? personne.getUid() : "unknown";

        String stored = personne.getAPersonneBase().getPassword();

        if (stored == null || stored.isBlank()) {
            specialLog.warn(
                    "Audit [VERIFY_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Aucun mot de passe trouvé en base de données pour cet utilisateur",
                    uid);
            return false;
        }

        if (stored.startsWith(ACTIVE_PASSWORD)) {
            specialLog.warn(
                    "Audit [VERIFY_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Le compte est actif mais n'a aucun mot de passe utilisable défini",
                    uid);
            return false;
        }

        if (!stored.startsWith("{")) {

            if (allowPlain) {
                boolean match = MessageDigest.isEqual(
                        stored.getBytes(StandardCharsets.UTF_8),
                        input.getBytes(StandardCharsets.UTF_8));

                if (!match) {
                    specialLog.warn("Audit [VERIFY_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Inadéquation du mot de passe en clair", uid);
                }

                return match;
            }

            specialLog.warn(
                    "Audit [VERIFY_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : La vérification du mot de passe en clair n'est pas autorisée par la configuration",
                    uid);
            return false;
        }

        ParsedPassword parsed = parse(stored);

        if (parsed == null) {
            specialLog.warn("Audit [VERIFY_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Le hash du mot de passe stocké est corrompu ou illisible",
                    uid);
            return false;
        }

        boolean match;

        switch (parsed.algo) {

            case ARGON2 :
                match = argon2Encoder.matches(input, parsed.hash);
                break;

            case SSHA :
                match = verifySSHA(parsed.digest, parsed.salt, input);
                break;

            default :
                specialLog.error(
                        "Audit [VERIFY_PASSWORD] : ABANDONNÉ pour l'utilisateur [{}] - Raison : Algorithme inconnu dans le mot de passe stocké | Détail : algo={}",
                        uid, parsed.algo);
                return false;
        }

        if (!match) {
            specialLog.warn("Audit [VERIFY_PASSWORD] : REFUSÉ pour l'utilisateur [{}] - Raison : Inadéquation du mot de passe", uid);
        }

        return match;
    }

    /**
     * Vérifie un mot de passe contre un digest SSHA.
     *
     * <ol>
     * <li>clair en UTF-8 (cas normal)</li>
     * <li>{@code new String(clair.getBytes("UTF-8"), "ISO-8859-1")} — bytes UTF-8 relus en ISO</li>
     * <li>{@code new String(clair.getBytes("ISO-8859-1"), "UTF-8")} — bytes ISO relus en UTF-8</li>
     * </ol>
     */

    private boolean verifySSHA(byte[] expectedDigest, byte[] salt, String input) {

        if (expectedDigest == null || salt == null) {
            specialLog.warn("Audit [VERIFY_PASSWORD] : REFUSÉ - Raison : Hash corrompu détecté (l'empreinte ou le sel est nul)");
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
            specialLog.error("Audit [VERIFY_PASSWORD] : ABANDONNÉ - Raison : Erreur technique lors de la vérification SSHA | Détail : {}", e.getMessage());
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

    public void validateRequest(PersonneDTO person, PasswordChangeRequestDTO request) {
        validateRequest(person, request, false);
    }

    public void validateRequest(PersonneDTO person, PasswordChangeRequestDTO request, boolean noOldPass) {

        if (request == null) {
            throw new IllegalArgumentException("Requête invalide");
        }

        if (!noOldPass && (request.getOldPass() == null || request.getOldPass().isBlank())) {
            throw new IllegalArgumentException("Ancien mot de passe requis");
        }

        if (request.getNewPass() == null || request.getNewPass().isBlank()) {
            throw new IllegalArgumentException("Nouveau mot de passe requis");
        }

        if (request.getOldPass() != null && request.getOldPass().equals(request.getNewPass())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien");
        }

        if (!request.getNewPass().equals(request.getConfirmPass())) {
            throw new IllegalArgumentException("La confirmation du mot de passe ne correspond pas");
        }

        isPasswordStrongEnough(request.getNewPass());
    }

    /**
     * @return {@code true} si l'utilisateur peut changer son mdp sans fournir l'ancien. Conditions : CVDL + ntPass + aucun vrai mdp stocké (null, blank ou
     *         marqueur ACTIVE).
     */
    private boolean isNoOldPass(PersonneDTO person) {
        if (person == null || person.getEnumPublic() == null) {
            return false;
        }
        if (!person.isNtPass()) {
            return false;
        }
        if (person.getEnumPublic() != EnumPublic.CVDL) {
            return false;
        }
        if (person.getEnumPublic().isConnectOk()) {
            return false;
        }
        String stored = person.getAPersonneBase().getPassword();
        return stored == null || stored.isBlank() || stored.startsWith(ACTIVE_PASSWORD);
    }

    /**
     * Vérifie si le mot de passe est assez fort et lance une exception si ce n'est pas le cas.
     *
     * @param pass
     *            Le mot de passe à valider.
     * @throws WeakPasswordException
     *             Si le mot de passe ne respecte pas les critères de sécurité.
     */
    public void isPasswordStrongEnough(String pass) {
        if (pass == null) {
            throw new WeakPasswordException("Mot de passe requis");
        }

        int minLength = mceProperties.getSecurity().getPasswordPolicy().getMinLength();
        int minTypes = mceProperties.getSecurity().getPasswordPolicy().getMinTypes();

        if (pass.length() < minLength) {
            throw new WeakPasswordException("Le mot de passe doit contenir au moins " + minLength + " caractères");
        }

        boolean hasLower = pass.matches(".*[a-z].*");
        boolean hasUpper = pass.matches(".*[A-Z].*");
        boolean hasDigit = pass.matches(".*\\d.*");
        boolean hasSymbol = pass.matches(".*[^a-zA-Z0-9].*");

        int types = 0;
        if (hasLower)
            types++;
        if (hasUpper)
            types++;
        if (hasDigit)
            types++;
        if (hasSymbol)
            types++;

        if (types < minTypes) {
            throw new WeakPasswordException(
                    "Le mot de passe doit contenir au moins " + minTypes + " types différents de caractères (minuscules, majuscules, chiffres, symboles)");
        }
    }

    // ---------------------------------------------------------------
    // dbb
    // ---------------------------------------------------------------

    /**
     * Enregistre le mot de passe actuel dans l'historique (table cerbere_password). En cas de modification multiple le même jour, le hash est mis à jour.
     */
    @Transactional
    public void savePasswordToHistory(PersonneDTO personne, String hashLdap) {
        specialLog.info("Audit [SAVE_PASSWORD_HISTORY] : Sauvegarde du mot de passe dans l'historique pour l'utilisateur [{}]", personne.getUid());

        APersonne aPersonne = personne.getAPersonneBase();
        Date today = new Date();

        List<CerberePassword> existing = cerberePasswordRepository.findByAPersonne(aPersonne);

        boolean alreadyToday = existing.stream().anyMatch(cp -> {
            if (cp.getDebut() == null)
                return false;
            java.util.Calendar c1 = java.util.Calendar.getInstance();
            java.util.Calendar c2 = java.util.Calendar.getInstance();
            c1.setTime(cp.getDebut());
            c2.setTime(today);
            return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR)
                    && c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR);
        });

        if (alreadyToday) {
            specialLog.warn("Audit [SAVE_PASSWORD_HISTORY] : Une entrée existe pour aujourd'hui, mise à jour du hash pour l'utilisateur [{}]",
                    personne.getUid());
            cerberePasswordRepository.updatePasswordForToday(aPersonne.getId(), hashLdap);
        } else {
            CerberePassword cp = new CerberePassword(aPersonne, hashLdap, today);
            cerberePasswordRepository.saveAndFlush(cp);
        }
    }

    /**
     * Ferme le dernier mot de passe actif en renseignant sa date de fin.
     */
    @Transactional
    public void closeLastPassword(PersonneDTO personne) {
        List<CerberePassword> history = cerberePasswordRepository
                .findByAPersonne(personne.getAPersonneBase());

        if (history != null && !history.isEmpty()) {
            CerberePassword last = history.get(0);
            if (last.getFin() == null && !isToday(last.getDebut())) {
                last.setFin(new Date());
                cerberePasswordRepository.save(last);
            }
        }
    }

    private boolean isToday(Date date) {
        if (date == null)
            return false;
        java.util.Calendar c1 = java.util.Calendar.getInstance();
        java.util.Calendar c2 = java.util.Calendar.getInstance();
        c1.setTime(date);
        c2.setTime(new Date());
        return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR)
                && c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR);
    }

    /**
     * Vérifie si le mot de passe en clair a déjà été utilisé par le passé.
     */
    public boolean isPasswordAlreadyUsed(PersonneDTO personne, String newPasswordClair) {
        log.debug("Vérification si le mot de passe a déjà été utilisé pour l'utilisateur [{}]", personne.getUid());
        List<CerberePassword> history = cerberePasswordRepository.findByAPersonne(personne.getAPersonneBase());

        for (CerberePassword cp : history) {
            if (verifyPasswordInternal(newPasswordClair, cp.getPassword())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Version interne de vérification qui prend un hash directement.
     */
    private boolean verifyPasswordInternal(String input, String storedHash) {
        if (input == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        if (!storedHash.startsWith("{")) {
            return storedHash.equals(input);
        }

        ParsedPassword parsed = parse(storedHash);
        if (parsed == null)
            return false;

        switch (parsed.algo) {
            case ARGON2 :
                return argon2Encoder.matches(input, parsed.hash);
            case SSHA :
                return verifySSHA(parsed.digest, parsed.salt, input);
            default :
                return false;
        }
    }

    private void updatePasswordInDatabase(PersonneDTO person, PasswordResult result) {
        Long id = person.getAPersonneBase().getId();
        log.debug("miseÀJourMotDePasseEnBase — uid={} id={} (lm présent={}, nt présent={})", person.getUid(), id, result.sambaLm != null,
                result.sambaNt != null);

        APersonne entity = aPersonneRepository.findById(id)
                .orElseThrow(() -> {
                    specialLog.error("Audit [UPDATE_DB] : ABANDONNÉ - Raison : Utilisateur introuvable en base de données | Détail : id={}", id);
                    return new IllegalStateException("Utilisateur introuvable en base");
                });

        log.debug("AVANT DÉFINITION — sambaLm présent en base pour uid={} : {}", person.getUid(), entity.getSambaLmpassword() != null);

        entity.setPassword(result.ldapHash);
        entity.setSambaLmpassword(result.sambaLm);
        entity.setSambaNtpassword(result.sambaNt);
        entity.setDateModification(new Date());

        log.debug("APRÈS DÉFINITION — sambaLm à sauvegarder pour uid={} : présent={}", person.getUid(), entity.getSambaLmpassword() != null);

        APersonne saved = aPersonneRepository.saveAndFlush(entity);

        log.debug("APRÈS SAUVEGARDE — sambaLm sauvegardé pour uid={} : présent={}", person.getUid(), saved.getSambaLmpassword() != null);
    }

    private void updatePasswordInLdap(String uid, String hash) {
        try {
            externalUserDao.updatePassword(uid, hash);
        } catch (Exception e) {
            specialLog.error("Audit [UPDATE_LDAP] : ABANDONNÉ pour l'utilisateur [{}] - Raison : L'opération de mise à jour LDAP a échoué | Détail : {}", uid,
                    e.getMessage());
            throw e;
        }
    }
}
