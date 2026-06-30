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
package fr.recia.mce.api.escomceapi.configuration.cache;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.ehcache.event.CacheEvent;
import org.ehcache.event.EventType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CacheEventLoggerTest {

    @Test
    void shouldLogEventWithoutError() {
        CacheEventLogger logger = new CacheEventLogger();
        CacheEvent<?, ?> event = Mockito.mock(CacheEvent.class);
        Mockito.when(event.getType()).thenReturn(EventType.CREATED);
        Mockito.when(event.getKey()).thenReturn("key1");
        Mockito.when(event.getOldValue()).thenReturn(null);
        Mockito.when(event.getNewValue()).thenReturn("value1");

        assertThatCode(() -> logger.onEvent(event)).doesNotThrowAnyException();
    }

}
