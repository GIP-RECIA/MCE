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
package fr.recia.mce.api.escomceapi.aop.notification;

import fr.recia.notifications.event_rest_client_kafka.HttpNotificationClient;
import fr.recia.notifications.model_kafka.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Aspect
@Slf4j
@Data
public class NotificationAspect {
    private final HttpNotificationClient notificationClient;

    @After("execution(* fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp.updatePassword(..)) && args(uid, newHashedPassword)")
    public void notifPasswordChange(String uid, String newHashedPassword) {
        try {
            String id = uid;
            String title = "MON COMPTE ÉTUDIANT";
            String message = "Votre mot de passe a été mis à jour";
            List<Channel> channel = List.of(Channel.WEB);
            String idLink = "";
            Priority priority = Priority.NORMAL;
            TargetType targetType = TargetType.UID;

            notificationClient.sendNotification(title, message, idLink, id, channel, priority, targetType);

            log.debug("Le mot de passe de {} a été mis à jour", id);
        }catch (Exception e) {
            log.debug("Erreur: la notification n'a pas pu être envoyée", e);
        }
    }

    @After("execution(* fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp.updateEmail(..)) && args(uid, newEmail)")
    public void notifEmailChange(String uid, String newEmail) {
        try {
            String id = uid;
            String title = "MON COMPTE ÉTUDIANT";
            String message = "Votre email a été mis à jour, avec cette adresse : " + newEmail;
            List<Channel> channel = List.of(Channel.WEB);
            String idLink = "";
            Priority priority = Priority.NORMAL;
            TargetType targetType = TargetType.UID;

            notificationClient.sendNotification(title, message, idLink, id, channel, priority, targetType);

            log.debug("Le mail de {} a été modifié", id);
        }catch (Exception e) {
            log.debug("Erreur: la notification n'a pas pu être envoyée", e);
        }
    }
}
