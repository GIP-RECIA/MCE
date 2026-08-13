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
package fr.recia.mce.api.escomceapi.db.enums;

public enum EnumObjectClass {

    ENTAuxTuteurStage,
    ENTEleve,
    ENTAuxPersRelEleve,
    ENTAuxEnseignant,
    ENTAuxNonEnsEtab,
    ENTAuxNonEnsServAc;

    public static boolean contains(java.util.Collection<String> objectClasses, EnumObjectClass target) {
        if (objectClasses == null) return false;
        for (String oc : objectClasses) {
            if (target.name().equalsIgnoreCase(oc)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(java.util.Collection<String> objectClasses, EnumObjectClass... targets) {
        if (objectClasses == null) return false;
        for (String oc : objectClasses) {
            for (EnumObjectClass target : targets) {
                if (target.name().equalsIgnoreCase(oc)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean containsMaitre(java.util.Collection<String> objectClasses) {
        return contains(objectClasses, ENTAuxTuteurStage);
    }

    public static boolean containsEleve(java.util.Collection<String> objectClasses) {
        return contains(objectClasses, ENTEleve);
    }

    public static boolean containsParent(java.util.Collection<String> objectClasses) {
        return contains(objectClasses, ENTAuxPersRelEleve);
    }

    public static boolean containsEnseignant(java.util.Collection<String> objectClasses) {
        return contains(objectClasses, ENTAuxEnseignant);
    }

    public static boolean containsNonEns(java.util.Collection<String> objectClasses) {
        return containsAny(objectClasses, ENTAuxNonEnsEtab, ENTAuxNonEnsServAc);
    }
}
