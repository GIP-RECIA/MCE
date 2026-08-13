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
package fr.recia.mce.api.escomceapi.db.beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StructureTest {

    @Test
    void shouldInternNom() {
        Structure s = new Structure();
        s.setNom("Test");
        assertThat(s.getNom()).isSameAs(s.getNom().intern());
    }

    @Test
    void shouldHandleNullNom() {
        Structure s = new Structure();
        s.setNom(null);
        assertThat(s.getNom()).isNull();
    }

    @Test
    void shouldInternDisplayName() {
        Structure s = new Structure();
        s.setDisplayName("Display");
        assertThat(s.getDisplayName()).isSameAs(s.getDisplayName().intern());
    }

    @Test
    void shouldHandleNullDisplayName() {
        Structure s = new Structure();
        s.setDisplayName(null);
        assertThat(s.getDisplayName()).isNull();
    }

    @Test
    void shouldInternUai() {
        Structure s = new Structure();
        s.setUai("0450001A");
        assertThat(s.getUai()).isSameAs(s.getUai().intern());
    }

    @Test
    void shouldHandleNullUai() {
        Structure s = new Structure();
        s.setUai(null);
        assertThat(s.getUai()).isNull();
    }

    @Test
    void shouldInternSkin() {
        Structure s = new Structure();
        s.setSkin("skin1");
        assertThat(s.getSkin()).isSameAs(s.getSkin().intern());
    }

    @Test
    void shouldHandleNullSkin() {
        Structure s = new Structure();
        s.setSkin(null);
        assertThat(s.getSkin()).isNull();
    }

    @Test
    void shouldInternSiren() {
        Structure s = new Structure();
        s.setSiren("123456789");
        assertThat(s.getSiren()).isSameAs(s.getSiren().intern());
    }

    @Test
    void shouldHandleNullSiren() {
        Structure s = new Structure();
        s.setSiren(null);
        assertThat(s.getSiren()).isNull();
    }

    @Test
    void shouldInternType() {
        Structure s = new Structure();
        s.setType("LYCEE");
        assertThat(s.getType()).isSameAs(s.getType().intern());
    }

    @Test
    void shouldHandleNullType() {
        Structure s = new Structure();
        s.setType(null);
        assertThat(s.getType()).isNull();
    }

    @Test
    void shouldInternDomaines() {
        Structure s = new Structure();
        s.setDomaines(new String[]{"ac-test.fr", "lycee.test.fr"});
        assertThat(s.getDomaines()[0]).isSameAs(s.getDomaines()[0].intern());
        assertThat(s.getDomaines()[1]).isSameAs(s.getDomaines()[1].intern());
    }

    @Test
    void shouldHandleNullDomaines() {
        Structure s = new Structure();
        s.setDomaines(null);
        assertThat(s.getDomaines()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        Structure s = new Structure(true, "Nom", "Display", "0450001A", "skin1",
                "Ville", "123456789", new String[]{"ac.fr"}, "LYCEE", "AC-test");

        assertThat(s.isOpen4Ent()).isTrue();
        assertThat(s.getNom()).isEqualTo("Nom");
        assertThat(s.getUai()).isEqualTo("0450001A");
    }

    @Test
    void testNoArgsConstructor() {
        Structure s = new Structure();
        assertThat(s.isOpen4Ent()).isFalse();
        assertThat(s.getNom()).isNull();
    }

    @Test
    void testCompareTo() {
        Structure s1 = new Structure();
        s1.setSiren("100");
        Structure s2 = new Structure();
        s2.setSiren("200");

        assertThat(s1.compareTo(s2)).isNegative();
        assertThat(s2.compareTo(s1)).isPositive();
    }

    @Test
    void testCompareToSameSiren() {
        Structure s1 = new Structure();
        s1.setSiren("100");
        Structure s2 = new Structure();
        s2.setSiren("100");
        assertThat(s1.compareTo(s2)).isZero();
    }

    @Test
    void testEquals() {
        Structure s1 = new Structure();
        s1.setSiren("100");
        Structure s2 = new Structure();
        s2.setSiren("100");
        Structure s3 = new Structure();
        s3.setSiren("200");

        assertThat(s1).isEqualTo(s2);
        assertThat(s1).isNotEqualTo(s3);
        assertThat(s1).isNotEqualTo(null);
    }

    @Test
    void testHashCode() {
        Structure s1 = new Structure();
        s1.setSiren("100");
        Structure s2 = new Structure();
        s2.setSiren("100");

        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void testHashCodeWithNullSiren() {
        Structure s = new Structure();
        assertThat(s.hashCode()).isEqualTo(31);
    }

    @Test
    void testEqualsWithNullSiren() {
        Structure s1 = new Structure();
        Structure s2 = new Structure();

        assertThat(s1.equals(s2)).isTrue();
    }

    @Test
    void testSettersAndGetters() {
        Structure s = new Structure();
        s.setVille("Paris");
        s.setSource("AC-test");

        assertThat(s.getVille()).isEqualTo("Paris");
        assertThat(s.getSource()).isEqualTo("AC-test");
    }

}
