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
package fr.recia.mce.api.escomceapi.utils;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.MD4;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LdapPassword {

    public static final String PREFIXCODE = "{SSHA}";

    private static Argon2PasswordEncoder encoder = new Argon2PasswordEncoder();

    public static enum Algo {
        SSHA,
        ARGON2
    }

    /**
     * mot de passe des compte actif sans mot de passe
     */
    public static final String Active = LdapPassword.PREFIXCODE
            + "Active==================================================";

    public static boolean isAcceptable(final String passEnClair) {
        if (passEnClair == null) {
            return false;
        }
        /*
         * pass doit faire au moins 12 caractères si tout type de caractère
         * 14 si que des Maj, Min et Chiffre,
         */
        String pass = passEnClair.trim();
        int score = 17;
        if (pass.matches(".*\\W.*")) {
            score = 15;
        }

        if (pass.matches(".*\\p{Lower}.*")) {
            score--;
        }
        if (pass.matches(".*\\p{Upper}.*")) {
            score--;
        }
        if (pass.matches(".*\\d.*")) {
            score--;
        }
        return pass.length() >= score;
    }

    private Algo algo;

    private MessageDigest md;

    /**
     * Le resultat de cryptage
     */
    private byte[] digest;
    private String hash;

    /**
     * Le germe utilisé au cryptage.
     */
    private byte[] salt;

    /**
     * Le codage base64 du mot de passe crypté.
     */
    private String base64;

    /**
     * Le codage complet au format LDAP.
     */
    private String codeLdap;

    private String sambaLMPassword;
    private String sambaNTPassword;

    private LdapPassword(final byte[] clair, final byte[] salt) {
        makeSSHAPassword(clair, salt);
    }

    /**
     * Cree un object {@link LdapPassword} à partir d'un mot passe en clair et un
     * germe.
     * 
     * @param clair         le mot de passe en clair
     * @param salt          le germe (pour que 2 mdp identique soit code
     *                      differemment)
     * @param algo          l'algo a utilise SSHA ou ARGON2 le salt ne sert pas pour
     *                      ARGON2
     * @param withSambaPass true genere les Samba*Password
     */
    public LdapPassword(final String clair, final String salt, Algo algo, boolean withSambaPass) {
        byte[] saltb = salt.getBytes();

        this.algo = algo == null ? Algo.SSHA : algo;

        switch (this.algo) {
            case SSHA:
                makeSSHAPassword(clair.getBytes(), saltb);
                break;
            case ARGON2:
                makeArgonPassword(clair);
                break;
            default:
                log.error("Algo non pris en charge:" + this.algo);
                break;
        }
        if (withSambaPass) {
            makeSambaPassword(clair, saltb);
        }
    }

    private void makeArgonPassword(String clair) {
        hash = encoder.encode(clair);
        codeLdap = String.format("{%s}%s", Algo.ARGON2, hash);
    }

    private void makeSSHAPassword(final byte[] clair, final byte[] salt) {
        int saltsize;
        this.salt = salt;

        try {
            md = MessageDigest.getInstance("SHA-1");

            md.update(clair);
            if (salt != null) {
                md.update(salt);
                saltsize = salt.length;
            } else {
                saltsize = 0;
            }
            digest = md.digest();
            byte[] baux = new byte[digest.length + saltsize];

            System.arraycopy(digest, 0, baux, 0, digest.length);
            if (this.salt != null) {
                System.arraycopy(this.salt, 0, baux, digest.length, saltsize);
            }

            base64 = Base64.encodeBase64String(baux);
            codeLdap = LdapPassword.PREFIXCODE + base64;

        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage());
        }
    }

    private void makeSambaPassword(final String clair, final byte[] salt) {
        byte[] passcode;

        if (codeLdap != null) {

            passcode = LdapPassword.getPreNTLMResponse(clair);
            sambaLMPassword = Hex.encodeHexString(passcode);

            passcode = getNTLMResponse(clair);
            sambaNTPassword = Hex.encodeHexString(passcode);

        }
    }

    /**
     * Cree un object {@link LdapPassword} à partir du cryptage a la LDAP.
     * 
     * @param codageLdap mot de passe au format ldap
     *                   ex:
     *                   {SSHA}D7IuqZaVbRB4IqyGB5E1R0G5Se8wODczZDM5MWU5ODc5ODJmYmJkMw==
     */
    public LdapPassword(final String codageLdap) {
        byte[] digestsalt;
        int digestSize;
        int totalSize;
        Pattern p = Pattern.compile("\\{((SSHA)|(ARGON2))\\}(.+)");
        Matcher m = p.matcher(codageLdap);

        codeLdap = codageLdap;

        if (m.matches()) {
            this.algo = Algo.valueOf(m.group(1));
            switch (this.algo) {
                case SSHA:
                    base64 = m.group(4);
                    try {
                        md = MessageDigest.getInstance("SHA-1");
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    digestsalt = Base64.decodeBase64(base64);
                    digestSize = md.getDigestLength();
                    totalSize = digestsalt.length;
                    if (totalSize >= digestSize) { // cas normal
                        digest = Arrays.copyOf(digestsalt, digestSize);
                        salt = Arrays.copyOfRange(digestsalt, digestSize, totalSize);
                    } else {
                        // sinon mot de passe bidon du genre {SSHA}LOCK
                        // on l'accepte mais les comparaisons doivent rendre toujours false
                        // optenue avec le digest null
                        digest = null;
                        salt = null;
                    }
                    break;
                case ARGON2:
                    // base64 = m.group(4);
                    // hash = new String(Base64.decode(base64));
                    hash = m.group(4);
                    break;
                default:
                    log.error("Unknow crypt algo");
                    break;
            }
        }
    }

    private static final byte[] S8 = {
            (byte) 0x4b, (byte) 0x47, (byte) 0x53, (byte) 0x21,
            (byte) 0x40, (byte) 0x23, (byte) 0x24, (byte) 0x25
    };

    private static void E(final byte[] key, final byte[] data, final byte[] e) {
        byte[] key7 = new byte[7];
        byte[] e8 = new byte[8];

        for (int i = 0; i < key.length / 7; i++) {
            System.arraycopy(key, i * 7, key7, 0, 7);
            // DES des = new DES();
            // des.encrypt(data, e8);
            SecretKey secretKey = new SecretKeySpec(data, "DES");

            Cipher cipher;
            try {
                cipher = Cipher.getInstance("DES");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                e8 = cipher.doFinal(data);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
                    | BadPaddingException e1) {

                e1.printStackTrace();
            }

            System.arraycopy(e8, 0, e, i * 8, 8);
        }
    }

    /**
     * Generate the ANSI DES hash for the password.
     */
    private static byte[] getPreNTLMResponse(final String password) {
        byte[] p14 = new byte[14];
        byte[] p16 = new byte[16];
        byte[] passwordBytes;
        try {
            // pb changement du codage pour simulé le codage 'à la perl...'
            // passwordBytes = password.toUpperCase().getBytes("ISO-8859-1");
            passwordBytes = password.toUpperCase().getBytes("Cp850");
            // passwordBytes = password.toUpperCase().getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Try setting jcifs.encoding=US-ASCII", uee);
        }
        int passwordLength = passwordBytes.length;

        // Only encrypt the first 14 bytes of the password for Pre 0.12 NT LM
        if (passwordLength > 14) {
            passwordLength = 14;
        }
        System.arraycopy(passwordBytes, 0, p14, 0, passwordLength);
        LdapPassword.E(p14, LdapPassword.S8, p16);
        return p16;
    }

    /**
     * Generate the Unicode MD4 hash for the password.
     */
    private byte[] getNTLMResponse(final String password) {
        byte[] uni = null;
        byte[] p16 = new byte[16];

        try {
            uni = password.getBytes("UTF-16LE");
        } catch (UnsupportedEncodingException uee) {
            if (log.isDebugEnabled()) {
                log.trace(uee.getMessage());
            }
        }
        MD4.Digest md4 = new MD4.Digest();
        md4.update(uni);
        try {
            md4.digest(p16, 0, 16);
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.trace(ex.getMessage());
            }
        }
        return p16;
    }

    /**
     * Test si deux mot de passes sont egaux.
     *
     * @param atester le LdapPassword a comparer
     * @return true si leurs cryptages (digest) sont égaux et non null
     */
    public boolean eguals(final LdapPassword atester) {
        if (atester == null || atester.digest == null) {
            return false;
        }
        return Arrays.equals(digest, atester.digest);
    }

    /**
     * Test si une chaine de carractere en clair correspond au mot de passe.
     * La version en clair sera cryptée puis testée.
     * 
     * @param clair version en clair du mot de passe a tester
     * @return true si mot de pass ok
     *
     */
    public boolean test(final String clair) {
        try {
            return testOneShot(clair) ||
                    testOneShot(new String(clair.getBytes("UTF-8"), "ISO-8859-1")) ||
                    testOneShot(new String(clair.getBytes("ISO-8859-1"), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    private boolean testOneShot(final String clair) {
        if (clair != null) {
            switch (this.algo) {
                case ARGON2:
                    return encoder.matches(clair, this.hash);
                case SSHA:
                default:
                    LdapPassword atester = new LdapPassword(clair.getBytes(), salt);
                    return eguals(atester);
            }
        }
        return false;
    }

    /**
     *
     * @return
     *         la chaine a la ldap du mot de passe crytpté et encodé
     */
    public String getCodageLdap() {
        return codeLdap;
    }

    /**
     * @return the sambaLMPassword
     */
    public String getSambaLMPassword() {
        return sambaLMPassword;
    }

    /**
     * @param sambaLMPassword the sambaLMPassword to set
     */
    public void setSambaLMPassword(final String sambaLMPassword) {
        this.sambaLMPassword = sambaLMPassword;
    }

    /**
     * @return the sambaNTPassword
     */
    public String getSambaNTPassword() {
        return sambaNTPassword;
    }

    /**
     * @param sambaNTPassword the sambaNTPassword to set
     */
    public void setSambaNTPassword(final String sambaNTPassword) {
        this.sambaNTPassword = sambaNTPassword;
    }

    public static void main(final String[] args) {
        LdapPassword lp = new LdapPassword(LdapPassword.Active);
        System.out.println(lp.codeLdap);
        System.out.println(lp.base64);
        lp = new LdapPassword("un pass ok", "un salt", Algo.ARGON2, false);
        System.out.println("ARGON2 " + lp.codeLdap);
        System.out.println("is ok " + lp.test("un pass ok"));
        LdapPassword lp2 = new LdapPassword("un pass ok", "deux salt", null, false);
        System.out.println("SSHA:" + lp2.codeLdap);
        System.out.println("is ok " + lp2.test("un pass ok"));
    }

}
