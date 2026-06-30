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
package fr.recia.mce.api.escomceapi.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class ConstantsTest {

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldHaveExpectedConstants() {
        assertThat(Constants.SPRING_PROFILE_DEVELOPMENT).isEqualTo("dev");
        assertThat(Constants.SPRING_PROFILE_PRODUCTION).isEqualTo("prod");
        assertThat(Constants.SPRING_PROFILE_TEST).isEqualTo("test");
        assertThat(Constants.PROPERTIES_TO_JSON_DELIMITER).isEqualTo("\", \"");
        assertThat(Constants.PROPERTIES_TO_JSON_PREFIX).isEqualTo("[ \"");
        assertThat(Constants.PROPERTIES_TO_JSON_SUFFIX).isEqualTo("\" ]");
    }

}
