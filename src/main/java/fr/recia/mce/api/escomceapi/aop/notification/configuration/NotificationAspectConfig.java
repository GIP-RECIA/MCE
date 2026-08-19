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
package fr.recia.mce.api.escomceapi.aop.notification.configuration;

import fr.recia.notifications.event_rest_client_kafka.HttpNotificationClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "notification-api")
public class NotificationAspectConfig {
    private String url;
    private String serviceName;
    private String apiKey;
    private String titleMdp;
    private String messageMdp;
    private String titleMail;
    private String messageMail;

    @Bean
    public HttpNotificationClient notificationClient() {
        return new HttpNotificationClient(url, serviceName, apiKey);
    }
}
