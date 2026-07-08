package fr.recia.mce.api.escomceapi.aop.notification;

import fr.recia.event_rest_client_kafka.HttpNotificationClient;
import fr.recia.model_kafka.model.*;
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
