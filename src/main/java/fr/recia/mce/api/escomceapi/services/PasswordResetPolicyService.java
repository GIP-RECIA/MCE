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
import fr.recia.mce.api.escomceapi.configuration.bean.MailProperties;
import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.entities.APersonne;
import fr.recia.mce.api.escomceapi.db.entities.CerbereConfirmation;
import fr.recia.mce.api.escomceapi.db.enums.EnumPublic;
import fr.recia.mce.api.escomceapi.services.exception.InvalidCodeException;
import fr.recia.mce.api.escomceapi.services.exception.ResendCooldownActiveException;
import fr.recia.mce.api.escomceapi.services.factories.IUserDTOFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Règles de la réinitialisation de mot de passe : état « Valide » du compte, anti-double-clic (cooldown de
 * renvoi), limite maximale de tentatives et autorisation de réinitialisation selon le mode d'authentification
 * local du compte.
 */
@Service
@Slf4j
public class PasswordResetPolicyService {

    static final String VALID_ACCOUNT_STATE = AccountState.VALIDE;

    @Autowired
    private MCEProperties mceProperties;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private IUserDTOFactory userDTOFactory;

    public int maxAttempts() {
        return mceProperties.getSecurity().getResetPolicy().getMaxAttempts();
    }

    public boolean isInactiveAccount(APersonne person) {
        return !VALID_ACCOUNT_STATE.equals(person.getEtat());
    }

    /**
     * Anti-double-clic : lève {@link ResendCooldownActiveException} si un code est encore dans la fenêtre
     * de renvoi (temps restant exposé au client). Simple no-op sinon, le renvoi est permis.
     */
    public void assertResendAllowed(List<CerbereConfirmation> pending, String uid, String logPrefix) {
        if (pending.isEmpty()) {
            return;
        }
        CerbereConfirmation last = pending.get(0);
        if (last.getLimite() == null) {
            return;
        }
        long expiryHours = mailProperties.getVerification().getExpiryHours();
        long estimatedCreation = last.getLimite().getTime() - (expiryHours * 3_600_000L);
        long elapsed = System.currentTimeMillis() - estimatedCreation;
        long cooldownMs = mceProperties.getSecurity().getResetPolicy().getResendCooldownMs();
        if (elapsed < cooldownMs) {
            log.warn("[{}] Anti-double-clic : dernière demande il y a {} ms pour uid={}", logPrefix, elapsed, uid);
            throw new ResendCooldownActiveException(
                    "Un code a déjà été envoyé récemment pour ce compte.", cooldownMs - elapsed);
        }
    }

    /**
     * Vérifie que le compte dispose d'un mode d'authentification local permettant de réinitialiser son mot de passe. La règle est partagée entre la demande de
     * code et son utilisation.
     *
     * <p>
     * Comptes sans mot de passe local réinitialisable :
     * </p>
     * <ul>
     * <li>EduConnect (parents/élèves éduc nat) : le mot de passe se gère sur le portail EduConnect ;</li>
     * <li>profil indéterminé ({@code enumPublic == null}) : aucun mode d'authentification local reconnu
     * (même règle que UserDTOFactoryImpl.computePassEditable qui renvoie false sur un profil null) ;</li>
     * <li>sans connectOk ni ntPass, aucun mode d'authentification local n'existe (même règle que UserDTOFactoryImpl.computePassEditable).</li>
     * </ul>
     */
    public void assertPasswordResetAllowed(PersonneDTO personneDTO, String uid) {
        if (!userDTOFactory.canResetPassword(personneDTO)) {
            EnumPublic pub = personneDTO.getEnumPublic();
            if (pub == null && userDTOFactory != null) {
                try {
                    pub = userDTOFactory.evalPublic(personneDTO);
                    personneDTO.setEnumPublic(pub);
                } catch (RuntimeException e) {
                    log.error("[PASSWORD_RESET] Échec de l'évaluation du profil uid={} : {}", uid, e.getMessage());
                }
            }
            if (pub == null) {
                log.warn("[PASSWORD_RESET] Profil non défini pour uid={} : réinitialisation refusée", uid);
                throw new InvalidCodeException("profil non reconnu pour le compte : réinitialisation impossible");
            }
            if (pub.isEduconnect()) {
                log.warn("[PASSWORD_RESET] Refus : compte EduConnect uid={}, enumPublic={}, ntPass={}",
                        uid, pub, personneDTO.isNtPass());
                throw new InvalidCodeException("Votre compte utilise EduConnect : le mot de passe se gère sur le portail EduConnect");
            }
            log.warn("[PASSWORD_RESET] Refus : mot de passe local non éditable uid={}, enumPublic={}, ntPass={}",
                    uid, pub, personneDTO.isNtPass());
            throw new InvalidCodeException("Aucune réinitialisation possible pour ce compte : aucun mode d'authentification local n'est actif");
        }
        log.info("[PASSWORD_RESET] Autorisation uid={} : enumPublic={}, ntPass={}",
                uid, personneDTO.getEnumPublic(), personneDTO.isNtPass());
    }

}
