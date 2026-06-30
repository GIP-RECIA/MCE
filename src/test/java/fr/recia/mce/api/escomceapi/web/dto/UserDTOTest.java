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

import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.Test;

class UserDTOTest {

    @Test
    void testNoArgsConstructor() {
        UserDTO dto = new UserDTO();

        assertThat(dto.getId()).isNull();
        assertThat(dto.getUid()).isNull();
    }

    @Test
    void testConstructorWithAllFields() {
        Date bod = new Date();
        UserDTO dto = new UserDTO(1L, "testuser", "Test User", "John", "Doe", "M.", "ENS",
                true, "jdoe", "0450001A", "john@test.com", "john.perso@test.com", bod,
                "avatar123", "ACTIF", true,
                Arrays.asList("public1"), Arrays.asList("menu1"),
                null, null, null, null);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUid()).isEqualTo("testuser");
        assertThat(dto.getUserName()).isEqualTo("Test User");
        assertThat(dto.getGivenName()).isEqualTo("John");
        assertThat(dto.getSn()).isEqualTo("Doe");
        assertThat(dto.getCivilite()).isEqualTo("M.");
        assertThat(dto.getCategorie()).isEqualTo("ENS");
        assertThat(dto.getCanEditEmail()).isTrue();
        assertThat(dto.getEmail()).isEqualTo("john@test.com");
        assertThat(dto.getEmailPersonnel()).isEqualTo("john.perso@test.com");
        assertThat(dto.getIdentifiant()).isEqualTo("jdoe");
        assertThat(dto.getEtab()).isEqualTo("0450001A");
        assertThat(dto.getBod()).isEqualTo(bod);
        assertThat(dto.getAvatar()).isEqualTo("avatar123");
        assertThat(dto.getEtat()).isEqualTo("ACTIF");
        assertThat(dto.getMdp()).isTrue();
        assertThat(dto.getUserPublic()).containsExactly("public1");
        assertThat(dto.getListMenu()).containsExactly("menu1");
    }

    @Test
    void testConstructorWithMinimalFields() {
        Date bod = new Date();
        UserDTO dto = new UserDTO(1L, "testuser", "Test User", "jdoe", "0450001A",
                "john@test.com", "john.perso@test.com", bod,
                "avatar123", "ACTIF", Arrays.asList("menu1"));

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getUid()).isEqualTo("testuser");
        assertThat(dto.getUserName()).isEqualTo("Test User");
        assertThat(dto.getIdentifiant()).isEqualTo("jdoe");
        assertThat(dto.getEtab()).isEqualTo("0450001A");
        assertThat(dto.getEmail()).isEqualTo("john@test.com");
        assertThat(dto.getEmailPersonnel()).isEqualTo("john.perso@test.com");
        assertThat(dto.getBod()).isEqualTo(bod);
        assertThat(dto.getAvatar()).isEqualTo("avatar123");
        assertThat(dto.getEtat()).isEqualTo("ACTIF");
        assertThat(dto.getListMenu()).containsExactly("menu1");
        assertThat(dto.getGivenName()).isNull();
        assertThat(dto.getSn()).isNull();
        assertThat(dto.getMdp()).isNull();
    }

    @Test
    void testToString() {
        UserDTO dto = new UserDTO(1L, "testuser", "Test User", "jdoe", "0450001A",
                "john@test.com", null, null, null, "ACTIF", null);

        String str = dto.toString();

        assertThat(str).contains("testuser", "Test User", "0450001A", "ACTIF");
    }

    @Test
    void testSettersAndGetters() {
        UserDTO dto = new UserDTO();
        dto.setUid("newuser");
        dto.setAvatarUrl("http://example.com/avatar.jpg");

        assertThat(dto.getUid()).isEqualTo("newuser");
        assertThat(dto.getAvatarUrl()).isEqualTo("http://example.com/avatar.jpg");
    }

}
