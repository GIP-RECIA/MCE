package fr.recia.mce.api.escomceapi.aop.notification.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "notification-conf-password")
@Configuration
public class NotificationAspectConfigPassword {
    String title;
    String message;
}
