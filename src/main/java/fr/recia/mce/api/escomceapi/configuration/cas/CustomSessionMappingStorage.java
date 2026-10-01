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

package fr.recia.mce.api.escomceapi.configuration.cas;

import fr.recia.mce.api.escomceapi.configuration.bean.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CustomSessionMappingStorage {

    @Autowired
    private RedisProperties redisProperties;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    protected String prefixedKey(String key){
        return String.format("%1$s:%2$s", redisProperties.getMappingPrefix(), key);
    }

    public void setSessionTicketSessionIdPair(String sessionTicket, String sessionId) {
        log.trace("[CustomSessionMappingStorage] setSessionTicketSessionIdPair {} {}", sessionTicket, sessionId);
        redisTemplate.opsForValue().set(prefixedKey(sessionTicket), sessionId,8, TimeUnit.HOURS);
    }

    public String getSessionIdFromSessionTicket(String sessionTicket) {
        log.trace("[CustomSessionMappingStorage] getSessionIdFromSessionTicket {}", sessionTicket);
        return redisTemplate.opsForValue().get(prefixedKey(sessionTicket));
    }

    public void removeSessionTicket(String sessionTicket) {
        log.trace("[CustomSessionMappingStorage] removeSessionTicket {}", sessionTicket);
        redisTemplate.delete(prefixedKey(sessionTicket));
    }

    public void deleteSessionContext(String sessionId) {
        log.trace("[CustomSessionMappingStorage] deleteSessionContext {}", sessionId);
        sessionRepository.deleteById(sessionId);
    }
}
