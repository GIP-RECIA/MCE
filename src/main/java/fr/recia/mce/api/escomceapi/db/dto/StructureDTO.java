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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import fr.recia.mce.api.escomceapi.db.beans.Structure;
import fr.recia.mce.api.escomceapi.db.entities.AStructure;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StructureDTO extends Structure {

    static private final Pattern P = Pattern.compile("(\\w+)-.+");

    public enum DomSource {
        AC,
        CFA,
        GIP,
        LA,
        REGION,
        COLL,
        EF2S;
    }

    private final AStructure aStructure;

    private DomSource domSource;

    public StructureDTO(final AStructure aStructure) {
        super();
        this.aStructure = aStructure;
    }

    @Override
    public boolean isOpen4Ent() {
        return false;
    }

    @Override
    public String getNom() {
        String nom = aStructure.getNom();
        if (nom != null) {
            nom = nom.replace('$', ' ');
            return nom.intern();
        }
        return nom;
    }

    @Override
    public String getDisplayName() {
        String dn = super.getDisplayName();
        if (StringUtils.isBlank(dn)) {
            return getNom();
        }
        return dn.intern();
    }

    @Override
    public String getSiren() {
        String s = super.getSiren();
        if (s == null) {
            s = aStructure.getSiren();
            super.setSiren(s);
        }
        return s;
    }

    public DomSource getDomSource() {
        Matcher m;
        if (domSource != null) {
            return domSource;
        }

        String source = aStructure.getSource();
        if (source != null) {

            m = P.matcher(source);
            if (m.matches()) {
                try {
                    domSource = DomSource.valueOf(m.group(1));
                    return domSource;
                } catch (Exception e) {
                    log.error("error");
                }
            } else {

                log.info("SOURCE_STRUCT_NO_DOM");
            }

        } else {
            log.info("SOURCE_STRUCT_NULL");

        }
        return null;

    }

    @Override
    public String getSource() {
        String src = super.getSource();
        if (src == null) {
            src = aStructure.getSource();
            super.setSource(src);
        }
        return src;
    }
}
