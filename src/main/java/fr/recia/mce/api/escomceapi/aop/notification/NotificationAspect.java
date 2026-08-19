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

import fr.recia.mce.api.escomceapi.aop.notification.configuration.NotificationAspectConfig;
import fr.recia.notifications.event_rest_client_kafka.HttpNotificationClient;
import fr.recia.notifications.model_kafka.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;


@Component
@Aspect
@Slf4j
@Data
public class NotificationAspect {
    private final HttpNotificationClient notificationClient;
    private final NotificationAspectConfig notificationAspectConfig;


    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    private String buildMessage(String template) {
        return template + ZonedDateTime.now().format(DATE_TIME_FORMATTER);
    }

    @After("execution(* fr.recia.mce.api.escomceapi.ldap.repository.LdapUserDaoImp.updatePassword(..)) && args(uid, newHashedPassword)")
    public void notifPasswordChange(String uid, String newHashedPassword) {
        try {
            final String title = notificationAspectConfig.getTitleMdp();
            final String message = buildMessage(notificationAspectConfig.getMessageMdp());
            final List<Channel> channel = List.of(Channel.WEB);
            final String idLink = "";
            final Priority priority = Priority.HIGH;
            final TargetType targetType = TargetType.UID;
            notificationClient.sendNotification(title, message, idLink, uid, channel, priority, targetType);
            log.debug("Notification pour changement de mot de passe de {} a été mis à jour", uid);
        } catch (Exception e) {
            log.error("Erreur: la notification n'a pas pu être envoyée", e);
        }
    }

    @After("execution(* fr.recia.mce.api.escomceapi.services.PersonneService.updateEmail(..)) && args(uid, ..)")
    public void notifEmailChange(String uid) {
        try {
            final String title = notificationAspectConfig.getTitleMail();
            final String message = buildMessage(notificationAspectConfig.getMessageMail());
            final List<Channel> channel = List.of(Channel.WEB);
            final String idLink = "";
            final Priority priority = Priority.HIGH ;
            final TargetType targetType = TargetType.UID;
            notificationClient.sendNotification(title, message, idLink, uid, channel, priority, targetType);
            log.info("Notification pour changement de mail {} a été envoyé", uid);
        } catch (Exception e) {
            log.error("Erreur: la notification n'a pas pu être envoyée", e);
        }
    }
}
