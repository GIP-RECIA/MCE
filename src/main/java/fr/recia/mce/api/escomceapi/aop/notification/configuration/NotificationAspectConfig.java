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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationAspectConfig {
    @Value("${notification-api.url}")
    private String apiUrl;

    @Value("${notification-api.service-name}")
    private String serviceName;

    @Value("${notification-api.api-key}")
    private String apiKey;

    @Bean
    public HttpNotificationClient notificationClient() {
        return new HttpNotificationClient(apiUrl, serviceName, apiKey);
    }
}
