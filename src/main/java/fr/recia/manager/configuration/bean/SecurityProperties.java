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
package fr.recia.manager.configuration.bean;

import lombok.Data;

@Data
public class SecurityProperties {

    private ResetPolicy resetPolicy = new ResetPolicy();
    private PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Data
    public static class ResetPolicy {
        private int maxAttempts = 5;
        private long resendCooldownMs = 60_000L;
    }

    @Data
    public static class PasswordPolicy {
        private int minLength = 12;
        private int minTypes = 3;
    }

}
