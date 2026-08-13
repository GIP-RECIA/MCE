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

import java.util.Collections;

import org.junit.jupiter.api.Test;

class InfoGeneralDTOTest {

    @Test
    void testNoArgsConstructor() {
        InfoGeneralDTO dto = new InfoGeneralDTO();

        assertThat(dto.getListFonctions()).isNull();
        assertThat(dto.getSectionClassesGroupes()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        InfoGeneralDTO dto = new InfoGeneralDTO(Collections.emptyList(), null);

        assertThat(dto.getListFonctions()).isEmpty();
        assertThat(dto.getSectionClassesGroupes()).isNull();
    }

}
