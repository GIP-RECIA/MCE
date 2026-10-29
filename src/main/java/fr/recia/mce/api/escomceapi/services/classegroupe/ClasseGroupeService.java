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
package fr.recia.mce.api.escomceapi.services.classegroupe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.recia.mce.api.escomceapi.ldap.ConstLDAP;
import fr.recia.mce.api.escomceapi.ldap.LdapStructureDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.ClasseGroupe;
import fr.recia.mce.api.escomceapi.services.beans.EnseignementProf;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionEleve;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionProf;
import fr.recia.mce.api.escomceapi.services.structure.StructureService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Service
public class ClasseGroupeService {

    private final Pattern patternClasse;
    private final Pattern patternGroup;
    private final ServiceProperties serviceProperties;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private StructureService structureService;

    public ClasseGroupeService(MCEProperties mceProperties) {
        this.serviceProperties = mceProperties.getService();
        this.patternClasse = Pattern.compile(mceProperties.getService().getClasseProperties().getRegexSirenAndClasse());
        this.patternGroup = Pattern.compile(mceProperties.getService().getGrpPedagoProperties().getRegexSirenAndClasse());
    }

    public ClasseGroupeDTO calculCG(LdapUserDto person) {
        final String uid = person.getId();
        log.debug("Inspection des attributs de l'utilisateur : {}", uid);
        log.debug("Attributs complets disponibles pour uid={} : {}", uid, person);

        ClasseGroupeDTO cg = new ClasseGroupeDTO();
        SubSectionEleve sectionEleve = new SubSectionEleve();
        SubSectionProf sectionProf = new SubSectionProf();
        Map<String, Map<String, ClasseGroupe>> profMap = new HashMap<>();
        Map<String, ClasseGroupe> cgMap = new HashMap<>();
        Map<String, List<String>> classes = new HashMap<>();
        Map<String, List<String>> groups = new HashMap<>();

        String profil = person.getAttribute(ConstLDAP.PROFILS).stream().findFirst().orElse("");
        log.debug("Profil utilisateur détecté pour uid={} : {}", uid, profil);

        // TODO : a mon avis il n'y a pas besoin de tous ces attributs à chaque fois
        String[] classAttrs;
        String[] groupAttrs;
        if (profil.contains("ENS")) {
            classAttrs = new String[]{ConstLDAP.ENS_CLASSES, ConstLDAP.ENS_CLASSES_MATIERES, ConstLDAP.ENS_GROUPES};
            groupAttrs = new String[]{ConstLDAP.ENS_CLASSES, ConstLDAP.ENS_CLASSES_MATIERES, ConstLDAP.ENS_GROUPES};
        } else {
            classAttrs = new String[]{ConstLDAP.ELEVE_CLASSES, ConstLDAP.ENS_CLASSES_MATIERES};
            groupAttrs = new String[]{ConstLDAP.ELEVE_GROUPES, ConstLDAP.ENS_GROUPES_MATIERES, ConstLDAP.ENS_GROUPES};
        }

        log.debug("Traitement des attributs classe/groupe pour uid={}", uid);
        retriveClassesGroupsOfPerson(classAttrs, patternClasse, person, classes, groups, profMap, classAttrs, groupAttrs);
        retriveClassesGroupsOfPerson(groupAttrs, patternGroup, person, classes, groups, profMap, classAttrs, groupAttrs);

        log.debug("Classes détectées pour uid={} : {}", uid, classes);
        log.debug("Groupes détectés pour uid={} : {}", uid, groups);
        log.debug("ProfMap (structure/matière) pour uid={} : {}", uid, profMap);

        // Create a set of all keys (union of both class and group keys for eleve)
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(classes.keySet());
        allKeys.addAll(groups.keySet());

        for (String key : allKeys) {
            // Get or create the ClasseGroupe object
            ClasseGroupe existingGroup = cgMap.getOrDefault(key, new ClasseGroupe(key, new ArrayList<>(), new ArrayList<>()));
            LdapStructureDto struct = structureService.findStructureBySiren(key);
            existingGroup.setNameEtab(struct.getName());
            if (classes.containsKey(key)) {
                existingGroup.getClasses().addAll(classes.get(key));
            }
            if (groups.containsKey(key)) {
                existingGroup.getGroupes().addAll(groups.get(key));
            }

            // Put the updated SourceData object back in the map
            cgMap.put(key, existingGroup);

        }

        // Populate SubSectionProf with profMap data
        Map<String, List<EnseignementProf>> mapListSectionProf = new HashMap<>();
        profMap.forEach((siren, matMap) -> {
            List<EnseignementProf> matListCG = mapListSectionProf.getOrDefault(siren, new ArrayList<>());
            matMap.forEach((matiere, sourceData) -> {
                log.debug("Traitement matière code={} pour siren={} (uid={})", matiere, siren, uid);
                String nameMatiere = findMatiere(person, siren, matiere);
                log.debug("Matière trouvée pour code={} (uid={}) : {}", matiere, uid, nameMatiere);
                EnseignementProf ens = new EnseignementProf();
                ens.setMatiere(nameMatiere);
                ens.setCg(sourceData);
                matListCG.add(ens);
            });
            LdapStructureDto struct = structureService.findStructureBySiren(siren);
            mapListSectionProf.put(struct.getName(), matListCG);
        });
        sectionProf.setEtabs(mapListSectionProf);

        // Populate sectionEleve with cgMap data
        List<ClasseGroupe> listCG = new ArrayList<>(cgMap.values());
        List<String> enseignementEleve = calculEnseignementSuivis(person);
        sectionEleve.setEtabs(listCG);
        sectionEleve.setEnseignementSuivis(enseignementEleve);

        // Populate ClasseGroupeDTO with the processed sections
        cg.setSectionProf(sectionProf);
        cg.setSectionEleve(sectionEleve);

        return cg;
    }

    private void retriveClassesGroupsOfPerson(String[] attributs, Pattern pattern, LdapUserDto person, Map<String, List<String>> classes,
                                              Map<String, List<String>> groups, Map<String, Map<String, ClasseGroupe>> profMap, String[] classAttrs, String[] groupAttrs) {
        log.debug("retriveClassesGroupsOfPerson pour uid={} avec attributs={}", person.getId(), attributs);
        Map<String, ClasseGroupe> cgMap = new HashMap<>();
        for (String ldapAttr : attributs) {
            List<String> ldapLines = person.getAttribute(ldapAttr);
            log.debug("Traitement attribut LDAP {} pour uid={} : {}", ldapAttr, person.getId(), ldapLines);

            if (ldapLines != null) {
                for (String val : ldapLines) {
                    Matcher matcher = pattern.matcher(val);
                    if (matcher.find()) {
                        String siren = matcher.group(1);
                        String value = matcher.group(2);
                        String matiere = matcher.group(4);
                        log.debug("Match trouvé pour uid={} : siren={}, value={}, matiere={}", person.getId(), siren, value, matiere);
                        if (siren != null) {
                            if (matiere != null) {
                                handleProf(profMap, siren, value, matiere, ldapAttr, cgMap, person.getId(), classAttrs, groupAttrs);
                            } else {
                                handleEleve(cgMap, siren, value, ldapAttr, classes, groups, person.getId(), classAttrs, groupAttrs);
                            }
                        }
                    } else {
                        log.debug("Aucune correspondance pour la valeur {} avec le pattern (uid={})", val, person.getId());
                    }
                }
            }
        }

    }

    private void handleEleve(Map<String, ClasseGroupe> eleveMap, String siren, String value, String ldapAttr, Map<String, List<String>> classes,
                             Map<String, List<String>> groups, String uid, String[] classAttrs, String[] groupAttrs) {

        ClasseGroupe sourceData = eleveMap.computeIfAbsent(siren, k -> new ClasseGroupe());
        if (sourceData.getNameEtab() != null) {
            LdapStructureDto struct = structureService.findStructureBySiren(siren);
            sourceData.setNameEtab(struct.getName());
        }

        // Check if it’s a class or a group and add to respective lists
        log.debug("handleEleve pour uid={} : ldapAttr={}, classAttrs={}, groupAttrs={}", uid, ldapAttr, Arrays.toString(classAttrs), Arrays.toString(groupAttrs));
        boolean isClassAttr = Arrays.asList(classAttrs).contains(ldapAttr);
        boolean isGroupAttr = Arrays.asList(groupAttrs).contains(ldapAttr);

        if (isClassAttr) {
            if (sourceData.getClasses() == null) {
                sourceData.setClasses(new ArrayList<>());
            }
            classes.put(siren, new ArrayList<>(List.of(value)));
            sourceData.getClasses().add(value);
        }

        if (isGroupAttr) {
            if (sourceData.getGroupes() == null) {
                sourceData.setGroupes(new ArrayList<>());
            }
            groups.put(siren, new ArrayList<>(List.of(value)));
            sourceData.getGroupes().add(value);
        }

        eleveMap.put(siren, sourceData);

    }

    private void handleProf(Map<String, Map<String, ClasseGroupe>> profMap, String siren, String value, String matiere, String ldapAttr,
                            Map<String, ClasseGroupe> cgMap, String uid, String[] classAttrs, String[] groupAttrs) {
        log.debug("handleProf appelé pour uid={} : siren={} matiere={} value={} ldapAttr={}", uid, siren, matiere, value, ldapAttr);

        profMap.putIfAbsent(siren, new HashMap<>());
        Map<String, ClasseGroupe> matMap = profMap.get(siren);
        matMap.putIfAbsent(matiere, new ClasseGroupe());
        ClasseGroupe sourceData = matMap.get(matiere);
        sourceData.setNameEtab(siren);

        // Check if it’s a class or a group and add to respective lists
        if (ldapAttr.contains(classAttrs[1])) {
            if (sourceData.getClasses() == null) {
                sourceData.setClasses(new ArrayList<>());
            }
            sourceData.getClasses().add(value); // Add the class value
        }

        if (ldapAttr.contains(groupAttrs[1])) {
            if (sourceData.getGroupes() == null) {
                sourceData.setGroupes(new ArrayList<>());
            }
            sourceData.getGroupes().add(value); // Add the group value
        }

        cgMap.putIfAbsent(matiere, sourceData);
        profMap.putIfAbsent(siren, cgMap);
    }

    private List<String> calculEnseignementSuivis(LdapUserDto person) {
        List<String> valuesAttr = person.getAttribute(ConstLDAP.ELEVE_ENSEIGNEMENTS);
        List<String> enseignements = new ArrayList<>();
        if (valuesAttr != null) {
            enseignements.addAll(valuesAttr);
        }
        return enseignements;
    }

    // TODO : méthode à refaire beaucoup trop complexe
    private String findMatiere(LdapUserDto person, String siren, String code) {

        String nomMatiere = null;
        // 1. Essayer de trouver via la liste des codes matieres (configuration)
        List<String> listCodeMatieres = person.getAttribute(ConstLDAP.ENS_MATIERE_ETAB);
        if (listCodeMatieres != null) {
            for (String codeMat : listCodeMatieres) {
                Matcher matcher = patternClasse.matcher(codeMat);
                if (matcher.find()) {
                    String struct = matcher.group(1);
                    String value = matcher.group(2);
                    String matiere = matcher.group(4);
                    if (struct.equals(siren) && value.equals(code)) {
                        nomMatiere = matiere;
                    }
                }
            }
        }
        if (nomMatiere != null) {
            log.debug("findMatiere trouvé via configuration pour uid={} siren={} code={} : {}", person.getId(), siren, code, nomMatiere);
            return nomMatiere;
        }

        // 2. Essayer de trouver via l'attribut ENTAuxEnsMatiereEnseignEtab
        List<String> attrMatiereEnseignEtab = person.getAttribute(ConstLDAP.ENS_MATIERE_ETAB);
        log.debug("Recherche dans ENTAuxEnsMatiereEnseignEtab pour uid={} siren={} code={} : attr={}", person.getId(), siren, code, attrMatiereEnseignEtab);
        if (attrMatiereEnseignEtab != null) {
            // TODO : pattern en dur
            Pattern patternMatiere = Pattern.compile("ENTStructureSIREN=(\\w+).+\\$([^$]+)");
            for (String matEtab : attrMatiereEnseignEtab) {
                Matcher matcher = patternMatiere.matcher(matEtab);
                if (matcher.find()) {
                    String struct = matcher.group(1);
                    String libelleMatiere = matcher.group(2);
                    if (struct.equals(siren)) {
                        log.debug("trouvé via ENTAuxEnsMatiereEnseignEtab pour uid={} : siren={} nom={}", person.getId(), struct, libelleMatiere);
                        return libelleMatiere;
                    }
                }
            }
        }

        log.debug("findMatiere non trouvé pour uid={} siren={} code={}", person.getId(), siren, code);
        return code;
    }
}
