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
package fr.recia.mce.api.escomceapi.db.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.recia.mce.api.escomceapi.db.entities.AStructure;

class StructureDTOTest {

    private AStructure aStructure;
    private StructureDTO dto;

    @BeforeEach
    void setUp() {
        aStructure = mock(AStructure.class);
        dto = new StructureDTO(aStructure);
    }

    @Test
    void shouldReturnFalseForOpen4Ent() {
        assertThat(dto.isOpen4Ent()).isFalse();
    }

    @Test
    void shouldGetNomFromAStructure() {
        when(aStructure.getNom()).thenReturn("Lycee$Test");

        String nom = dto.getNom();

        assertThat(nom).isEqualTo("Lycee Test");
    }

    @Test
    void shouldReturnNullNomWhenNull() {
        when(aStructure.getNom()).thenReturn(null);

        assertThat(dto.getNom()).isNull();
    }

    @Test
    void shouldReturnDisplayNameOrNom() {
        dto.setDisplayName("Display Name");

        assertThat(dto.getDisplayName()).isEqualTo("Display Name");
    }

    @Test
    void shouldReturnNomWhenDisplayNameBlank() {
        dto.setDisplayName(null);
        when(aStructure.getNom()).thenReturn("Lycee Test");

        String displayName = dto.getDisplayName();

        assertThat(displayName).isEqualTo("Lycee Test");
    }

    @Test
    void shouldGetSirenFromSuperThenAStructure() {
        when(aStructure.getSiren()).thenReturn("123456789");

        assertThat(dto.getSiren()).isEqualTo("123456789");
    }

    @Test
    void shouldCacheSirenAfterFirstCall() {
        when(aStructure.getSiren()).thenReturn("123456789");

        assertThat(dto.getSiren()).isEqualTo("123456789");
    }

    @Test
    void shouldGetDomSourceFromSourceMatch() {
        when(aStructure.getSource()).thenReturn("AC-something");

        StructureDTO.DomSource ds = dto.getDomSource();

        assertThat(ds).isEqualTo(StructureDTO.DomSource.AC);
    }

    @Test
    void shouldReturnNullWhenSourceDoesNotMatchPattern() {
        when(aStructure.getSource()).thenReturn("invalid");

        StructureDTO.DomSource ds = dto.getDomSource();

        assertThat(ds).isNull();
    }

    @Test
    void shouldReturnNullWhenSourceIsNull() {
        when(aStructure.getSource()).thenReturn(null);

        StructureDTO.DomSource ds = dto.getDomSource();

        assertThat(ds).isNull();
    }

    @Test
    void shouldCacheDomSource() {
        when(aStructure.getSource()).thenReturn("CFA-other");

        assertThat(dto.getDomSource()).isEqualTo(StructureDTO.DomSource.CFA);
    }

    @Test
    void shouldGetSourceFromSuperThenAStructure() {
        when(aStructure.getSource()).thenReturn("AC-test");

        assertThat(dto.getSource()).isEqualTo("AC-test");
    }

    @Test
    void shouldCacheSource() {
        when(aStructure.getSource()).thenReturn("AC-test");

        assertThat(dto.getSource()).isEqualTo("AC-test");
    }

}
