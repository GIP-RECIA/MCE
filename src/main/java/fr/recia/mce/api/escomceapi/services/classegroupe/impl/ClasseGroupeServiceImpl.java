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
package fr.recia.mce.api.escomceapi.services.classegroupe.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalStructure;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.ClasseGroupe;
import fr.recia.mce.api.escomceapi.services.beans.EnseignementProf;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionEleve;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionProf;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
import fr.recia.mce.api.escomceapi.services.structure.IStructureService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Service
public class ClasseGroupeServiceImpl implements IClasseGroupeService {

    String[] classAttrs;
    String regexClasse;

    String[] groupAttrs;
    String regexGroup;

    private ServiceProperties serviceProperties;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private ExternalUserHelper extUserHelper;

    @Autowired
    private IStructureService structureService;

    private final Map<String, ClasseGroupe> bufferUid2mapClasses2 = new HashMap<>();

    public ClasseGroupeServiceImpl(MCEProperties mceProperties) {
        this.serviceProperties = mceProperties.getService();
    }

    @Override
    public ClasseGroupeDTO calculCG(IExternalUser person) {

        log.debug("DEBUG: Entrée dans calculCG pour {}", person != null ? person.getId() : "null");
        if (person == null) {
            return null;
        }
        String uid = person.getId();

        if (log.isDebugEnabled()) {
            log.debug("DEBUG: Inspection des attributs de l'utilisateur : {}", uid);
            log.debug("DEBUG: Attributs complets disponibles pour uid={} : {}", uid, person.toString());
        }

        ClasseGroupeDTO cg = new ClasseGroupeDTO();
        SubSectionEleve sectionEleve = new SubSectionEleve();
        SubSectionProf sectionProf = new SubSectionProf();

        Map<String, Map<String, ClasseGroupe>> profMap = new HashMap<>();
        Map<String, ClasseGroupe> cgMap = new HashMap<>();

        Map<String, List<String>> classes = new HashMap<>();
        Map<String, List<String>> groups = new HashMap<>();

        String profil = person.getAttribute("ENTPersonProfils").stream().findFirst().orElse("");
        log.debug("DEBUG: Profil utilisateur détecté pour uid={} : {}", uid, profil);

        if (profil.contains("ENS")) {
            classAttrs = new String[]{"ENTAuxEnsClasses", "ENTAuxEnsClassesMatieres", "ENTAuxEnsGroupes"};
            groupAttrs = new String[]{"ENTAuxEnsClasses", "ENTAuxEnsClassesMatieres", "ENTAuxEnsGroupes"};
        } else {
            classAttrs = this.serviceProperties.getClasseProperties().getLdapAttributsClasse().split("\\s+");
            groupAttrs = this.serviceProperties.getGrpPedagoProperties().getLdapAttributsClasse().split("\\s+");
        }

        regexClasse = this.serviceProperties.getClasseProperties().getRegexSirenAndClasse();
        regexGroup = this.serviceProperties.getGrpPedagoProperties().getRegexSirenAndClasse();

        List<String> listCodeMatieres = person.getAttribute(extUserHelper.getUserCodeMatiereEnseignement());

        // process each ldap attribute classe and groupes
        log.debug("DEBUG: Traitement des attributs classe/groupe pour uid={}", uid);
        retriveClassesGroupsOfPerson(classAttrs, regexClasse, person, classes, groups,
                profMap);

        retriveClassesGroupsOfPerson(groupAttrs, regexGroup, person, classes, groups,
                profMap);

        log.debug("DEBUG: Classes détectées pour uid={} : {}", uid, classes);
        log.debug("DEBUG: Groupes détectés pour uid={} : {}", uid, groups);
        log.debug("DEBUG: ProfMap (structure/matière) pour uid={} : {}", uid, profMap);

        // Create a set of all keys (union of both class and group keys for eleve)
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(classes.keySet());
        allKeys.addAll(groups.keySet());

        for (String key : allKeys) {
            // Get or create the ClasseGroupe object
            ClasseGroupe existingGroup = cgMap.getOrDefault(key,
                    new ClasseGroupe(key, new ArrayList<>(), new ArrayList<>()));
            IExternalStructure struct = structureService.findStructureBySiren(key);
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
                if (log.isDebugEnabled()) {
                    log.debug("DEBUG: Traitement matière code={} pour siren={} (uid={})", matiere, siren, uid);
                }
                String nameMatiere = findMatiere(person, siren, matiere);
                if (log.isDebugEnabled()) {
                    log.debug("DEBUG: Matière trouvée pour code={} (uid={}) : {}", matiere, uid, nameMatiere);
                }
                EnseignementProf ens = new EnseignementProf();
                ens.setMatiere(nameMatiere);
                ens.setCg(sourceData);
                matListCG.add(ens);
            });
            IExternalStructure struct = structureService.findStructureBySiren(siren);

            mapListSectionProf.put(struct.getName(), matListCG);
        });
        sectionProf.setEtabs(mapListSectionProf);

        // Populate sectionEleve with cgMap data
        List<ClasseGroupe> listCG = new ArrayList<>(cgMap.values());
        List<String> enseignementEleve = calculEnseignementSuivis(person, extUserHelper.getUserEleveEnseignement());
        sectionEleve.setEtabs(listCG);
        sectionEleve.setEnseignementSuivis(enseignementEleve);

        // Populate ClasseGroupeDTO with the processed sections
        cg.setSectionProf(sectionProf);
        cg.setSectionEleve(sectionEleve);

        return cg;
    }

    private void retriveClassesGroupsOfPerson(String[] attributs, String regexCG,
            IExternalUser person,
            Map<String, List<String>> classes, Map<String, List<String>> groups,
            Map<String, Map<String, ClasseGroupe>> profMap) {
        Pattern pattern = Pattern.compile(regexCG);
        if (log.isDebugEnabled()) {
            log.debug("DEBUG: retriveClassesGroupsOfPerson pour uid={} avec attributs={}", person.getId(), (Object) attributs);
        }

        Map<String, ClasseGroupe> cgMap = new HashMap<>();

        for (String ldapAttr : attributs) {
            List<String> ldapLines = person.getAttribute(ldapAttr);
            if (log.isDebugEnabled()) {
                log.debug("DEBUG: Traitement attribut LDAP {} pour uid={} : {}", ldapAttr, person.getId(), ldapLines);
            }

            if (ldapLines != null) {

                for (String val : ldapLines) {
                    Matcher matcher = pattern.matcher(val);
                    if (matcher.find()) {
                        String siren = matcher.group(1);
                        String value = matcher.group(2);
                        String matiere = matcher.group(4);

                        if (log.isDebugEnabled()) {
                            log.debug("DEBUG: Match trouvé pour uid={} : siren={}, value={}, matiere={}", person.getId(), siren, value, matiere);
                        }
                        if (siren != null) {

                            if (matiere != null) {
                                handleProf(profMap, siren, value, matiere, ldapAttr, cgMap, person.getId());
                            } else {
                                handleEleve(cgMap, siren, value, ldapAttr, classes, groups, person.getId());
                            }
                        }

                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("DEBUG: Aucune correspondance pour la valeur {} avec le pattern (uid={})", val, person.getId());
                        }
                    }
                }
            }
        }

    }

    private void handleEleve(Map<String, ClasseGroupe> eleveMap, String siren, String value, String ldapAttr,
            Map<String, List<String>> classes, Map<String, List<String>> groups, String uid) {

        ClasseGroupe sourceData = eleveMap.computeIfAbsent(siren, k -> new ClasseGroupe());

        // Set the name (only if it hasn't been set yet)
        if (sourceData.getNameEtab() != null) {
            IExternalStructure struct = structureService.findStructureBySiren(siren);
            sourceData.setNameEtab(struct.getName());
        }

        // Check if it’s a class or a group and add to respective lists
        if (log.isDebugEnabled()) {
            log.debug("DEBUG: handleEleve pour uid={} : ldapAttr={}, classAttrs={}, groupAttrs={}", uid, ldapAttr, Arrays.toString(classAttrs), Arrays.toString(groupAttrs));
        }

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

    private void handleProf(Map<String, Map<String, ClasseGroupe>> profMap, String siren, String value, String matiere,
            String ldapAttr, Map<String, ClasseGroupe> cgMap, String uid) {
        if (log.isDebugEnabled()) {
            log.debug("DEBUG: handleProf appelé pour uid={} : siren={} matiere={} value={} ldapAttr={}", uid, siren, matiere, value, ldapAttr);
        }

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

    private List<String> calculEnseignementSuivis(IExternalUser person, String attrEnsSuivi) {

        List<String> valuesAttr = person.getAttribute(attrEnsSuivi);
        List<String> enseignements = new ArrayList<>();

        if (valuesAttr != null) {

            enseignements.addAll(valuesAttr);
        }

        return enseignements;
    }

    private String findMatiere(IExternalUser person, String siren, String code) {

        String nomMatiere = null;

        // 1. Essayer de trouver via la liste des codes matieres (configuration)
        List<String> listCodeMatieres = person.getAttribute(extUserHelper.getUserCodeMatiereEnseignement());
        if (listCodeMatieres != null) {
            for (String codeMat : listCodeMatieres) {
                Pattern pattern = Pattern.compile(regexClasse);
                Matcher matcher = pattern.matcher(codeMat);
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
            if (log.isDebugEnabled()) {
                log.debug("DEBUG: findMatiere trouvé via configuration pour uid={} siren={} code={} : {}", person.getId(), siren, code, nomMatiere);
            }
            return nomMatiere;
        }

        // 2. Essayer de trouver via l'attribut ENTAuxEnsMatiereEnseignEtab
        List<String> attrMatiereEnseignEtab = person.getAttribute("ENTAuxEnsMatiereEnseignEtab");
        if (log.isDebugEnabled()) {
            log.debug("DEBUG: Recherche dans ENTAuxEnsMatiereEnseignEtab pour uid={} siren={} code={} : attr={}", person.getId(), siren, code, attrMatiereEnseignEtab);
        }
        if (attrMatiereEnseignEtab != null) {
            Pattern patternMatiere = Pattern.compile("ENTStructureSIREN=(\\w+).+\\$([^$]+)");
            for (String matEtab : attrMatiereEnseignEtab) {
                Matcher matcher = patternMatiere.matcher(matEtab);
                if (matcher.find()) {
                    String struct = matcher.group(1);
                    String libelleMatiere = matcher.group(2);

                    if (struct.equals(siren)) {
                        if (log.isDebugEnabled()) {
                            log.debug("DEBUG: trouvé via ENTAuxEnsMatiereEnseignEtab pour uid={} : siren={} nom={}", person.getId(), struct, libelleMatiere);
                        }
                        return libelleMatiere;
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("DEBUG: findMatiere non trouvé pour uid={} siren={} code={}", person.getId(), siren, code);
        }
        return code; // Retourner le code si aucun nom trouvé
    }
}
