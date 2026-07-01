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
package fr.recia.mce.api.escomceapi.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestDTOTest {

    @Test
    void emailUpdateRequestDTO() {
        EmailUpdateRequestDTO dto = new EmailUpdateRequestDTO();
        dto.setEmail("a@x.fr");
        dto.setConfirmEmail("a@x.fr");

        assertThat(dto.getEmail()).isEqualTo("a@x.fr");
        assertThat(dto.getConfirmEmail()).isEqualTo("a@x.fr");
        assertThat(dto.toString()).isNotNull();

        EmailUpdateRequestDTO dto2 = new EmailUpdateRequestDTO();
        dto2.setEmail("a@x.fr");
        dto2.setConfirmEmail("a@x.fr");
        assertThat(dto).isEqualTo(dto2).hasSameHashCodeAs(dto2);
        assertThat(dto).isNotEqualTo(null);

        dto2.setConfirmEmail("b@x.fr");
        assertThat(dto).isNotEqualTo(dto2);
    }

    @Test
    void passwordChangeRequestDTO() {
        PasswordChangeRequestDTO dto = new PasswordChangeRequestDTO();
        dto.setOldPass("old");
        dto.setNewPass("new");
        dto.setConfirmPass("new");

        assertThat(dto.getOldPass()).isEqualTo("old");
        assertThat(dto.getNewPass()).isEqualTo("new");
        assertThat(dto.getConfirmPass()).isEqualTo("new");
        assertThat(dto.toString()).isNotNull();

        PasswordChangeRequestDTO dto2 = new PasswordChangeRequestDTO();
        dto2.setOldPass("old");
        dto2.setNewPass("new");
        dto2.setConfirmPass("new");
        assertThat(dto).isEqualTo(dto2).hasSameHashCodeAs(dto2);
        assertThat(dto).isNotEqualTo(null);

        dto2.setNewPass("other");
        assertThat(dto).isNotEqualTo(dto2);
    }
}
