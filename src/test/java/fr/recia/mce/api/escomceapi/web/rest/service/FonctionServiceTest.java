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
package fr.recia.mce.api.escomceapi.web.rest.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;

import fr.recia.mce.api.escomceapi.services.FonctionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.recia.mce.api.escomceapi.db.dto.FonctionDTO;
import fr.recia.mce.api.escomceapi.db.entities.AFonction;
import fr.recia.mce.api.escomceapi.db.repositories.AFonctionRepository;
import fr.recia.mce.api.escomceapi.db.repositories.FonctionRepository;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;

@ExtendWith(MockitoExtension.class)
class FonctionServiceTest {

    @Mock
    private FonctionRepository fonctionRepository;

    @Mock
    private AFonctionRepository aFonctionRepository;

    @Mock
    private IStructureService structureService;

    @InjectMocks
    private FonctionService fonctionService;

    @Test
    void testGetAllFonctionOfPersonne() {
        FonctionDTO f1 = new FonctionDTO("maths", "teacher", "source", "123");
        f1.setStruct(null);

        FonctionDTO f2 = new FonctionDTO("physics", "teacher", "source", "456");
        f2.setStruct(mock(fr.recia.mce.api.escomceapi.ldap.IExternalStructure.class));

        when(fonctionRepository.findAllFonction(1L)).thenReturn(Arrays.asList(f1, f2));
        when(structureService.findStructureBySiren("123")).thenReturn(mock(fr.recia.mce.api.escomceapi.ldap.IExternalStructure.class));

        Collection<FonctionDTO> result = fonctionService.getAllFonctionOfPersonne(1L);

        assertEquals(2, result.size());
        assertNotNull(f1.getStruct());
        assertNotNull(f2.getStruct());
        verify(structureService, times(1)).findStructureBySiren("123");
    }

    @Test
    void testUpdateDateFin_Active() {
        AFonction aFonction = new AFonction();
        aFonction.setDateFin(new Date());

        when(aFonctionRepository.findById(1L)).thenReturn(Optional.of(aFonction));

        fonctionService.updateDateFin(1L, true);

        assertNull(aFonction.getDateFin());
        verify(aFonctionRepository).save(aFonction);
    }

    @Test
    void testUpdateDateFin_Inactive() {
        AFonction aFonction = new AFonction();

        when(aFonctionRepository.findById(1L)).thenReturn(Optional.of(aFonction));

        fonctionService.updateDateFin(1L, false);

        assertNotNull(aFonction.getDateFin());
        verify(aFonctionRepository).save(aFonction);
    }

    @Test
    void testUpdateDateFin_NotFound() {
        when(aFonctionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> fonctionService.updateDateFin(1L, true));
    }
}
