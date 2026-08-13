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

import org.junit.jupiter.api.Test;

class FonctionDTOTest {

    @Test
    void testConstructorWithStrings() {
        FonctionDTO dto = new FonctionDTO("Maths", "Professeur", "AC-test", "123456789");

        assertThat(dto.getDiscipline()).isEqualTo("Maths");
        assertThat(dto.getFonction()).isEqualTo("Professeur");
        assertThat(dto.getSource()).isEqualTo("AC-test");
        assertThat(dto.getSiren()).isEqualTo("123456789");
    }

    @Test
    void testConstructorWithAllFields() {
        FonctionDTO dto = new FonctionDTO(1L, "Professeur", "Maths", "123456789", "CD01", "CF01", true);

        assertThat(dto.getIdFonction()).isEqualTo(1L);
        assertThat(dto.getFonction()).isEqualTo("Professeur");
        assertThat(dto.getDiscipline()).isEqualTo("Maths");
        assertThat(dto.getSiren()).isEqualTo("123456789");
        assertThat(dto.getCodeD()).isEqualTo("CD01");
        assertThat(dto.getCodeF()).isEqualTo("CF01");
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void testDefaultActive() {
        FonctionDTO dto = new FonctionDTO("Maths", "Professeur", "AC-test", "123456789");

        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void testCompareToBySiren() {
        FonctionDTO dto1 = new FonctionDTO(null, null, null, "100");
        dto1.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));
        FonctionDTO dto2 = new FonctionDTO(null, null, null, "200");
        dto2.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "200", null, null, null));

        assertThat(dto1.compareTo(dto2)).isNegative();
        assertThat(dto2.compareTo(dto1)).isPositive();
    }

    @Test
    void testCompareToSameSiren() {
        FonctionDTO dto1 = new FonctionDTO("Maths", "Prof", null, "100");
        dto1.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));
        FonctionDTO dto2 = new FonctionDTO("Maths", "Prof", null, "100");
        dto2.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));

        assertThat(dto1.compareTo(dto2)).isZero();
    }

    @Test
    void testCompareToByFonctionWhenSameSiren() {
        FonctionDTO dto1 = new FonctionDTO(null, "A", null, "100");
        dto1.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));
        FonctionDTO dto2 = new FonctionDTO(null, "B", null, "100");
        dto2.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));

        assertThat(dto1.compareTo(dto2)).isNegative();
        assertThat(dto2.compareTo(dto1)).isPositive();
    }

    @Test
    void testCompareToByDisciplineWhenSameSirenAndFonction() {
        FonctionDTO dto1 = new FonctionDTO("A", "Prof", null, "100");
        dto1.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));
        FonctionDTO dto2 = new FonctionDTO("B", "Prof", null, "100");
        dto2.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));

        assertThat(dto1.compareTo(dto2)).isNegative();
        assertThat(dto2.compareTo(dto1)).isPositive();
    }

    @Test
    void testEquals() {
        FonctionDTO dto1 = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        dto1.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));
        FonctionDTO dto2 = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        dto2.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));

        assertThat(dto1).isEqualTo(dto2);
    }

    @Test
    void testEqualsWithNull() {
        FonctionDTO dto = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        assertThat(dto).isNotEqualTo(null);
    }

    @Test
    void testEqualsWithDifferentClass() {
        FonctionDTO dto = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        assertThat(dto).isNotEqualTo(new Object());
    }

    @Test
    void testEqualsWithDifferentDiscipline() {
        FonctionDTO dto1 = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        FonctionDTO dto2 = new FonctionDTO("Physics", "Prof", "AC-test", "100");

        assertThat(dto1).isNotEqualTo(dto2);
    }

    @Test
    void testEqualsWithNullDiscipline() {
        FonctionDTO dto1 = new FonctionDTO(null, "Prof", "AC-test", "100");
        FonctionDTO dto2 = new FonctionDTO("Maths", "Prof", "AC-test", "100");

        assertThat(dto1).isNotEqualTo(dto2);
    }

    @Test
    void testHashCode() {
        FonctionDTO dto1 = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        dto1.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));
        FonctionDTO dto2 = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        dto2.setStructure(new fr.recia.mce.api.escomceapi.db.beans.Structure(false, null, null, null, null, null, "100", null, null, null));

        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    void testSettersAndGetters() {
        FonctionDTO dto = new FonctionDTO("Maths", "Prof", "AC-test", "100");
        dto.setCodeD("CD01");
        dto.setCodeF("CF01");
        dto.setIdFonction(1L);
        dto.setActive(false);

        assertThat(dto.getCodeD()).isEqualTo("CD01");
        assertThat(dto.getCodeF()).isEqualTo("CF01");
        assertThat(dto.getIdFonction()).isEqualTo(1L);
        assertThat(dto.isActive()).isFalse();
    }

}
