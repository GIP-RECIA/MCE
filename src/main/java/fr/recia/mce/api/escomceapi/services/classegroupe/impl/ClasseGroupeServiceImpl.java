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
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.ClasseGroupe;
import fr.recia.mce.api.escomceapi.services.beans.EnseignementProf;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionEleve;
import fr.recia.mce.api.escomceapi.services.beans.SubSectionProf;
import fr.recia.mce.api.escomceapi.services.classegroupe.ClasseGroupeDTO;
import fr.recia.mce.api.escomceapi.services.classegroupe.IClasseGroupeService;
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

    private final Map<String, ClasseGroupe> bufferUid2mapClasses2 = new HashMap<>();

    public ClasseGroupeServiceImpl(MCEProperties mceProperties) {
        this.serviceProperties = mceProperties.getService();
    }

    @Override
    public ClasseGroupeDTO calculCG(IExternalUser person) {

        if (person == null) {
            return null;
        }

        ClasseGroupeDTO cg = new ClasseGroupeDTO();
        SubSectionEleve sectionEleve = new SubSectionEleve();
        SubSectionProf sectionProf = new SubSectionProf();

        Map<String, Map<String, ClasseGroupe>> profMap = new HashMap<>();
        Map<String, ClasseGroupe> cgMap = new HashMap<>();

        Map<String, List<String>> classes = new HashMap<>();
        Map<String, List<String>> groups = new HashMap<>();

        classAttrs = this.serviceProperties.getClasseProperties().getLdapAttributsClasse().split("\\s+");
        groupAttrs = this.serviceProperties.getGrpPedagoProperties().getLdapAttributsClasse().split("\\s+");

        regexClasse = this.serviceProperties.getClasseProperties().getRegexSirenAndClasse();
        regexGroup = this.serviceProperties.getGrpPedagoProperties().getRegexSirenAndClasse();

        List<String> listCodeMatieres = person.getAttribute(extUserHelper.getUserCodeMatiereEnseignement());

        // process each ldap attribute classe and groupes
        retriveClassesGroupsOfPerson(classAttrs, regexClasse, person, classes, groups,
                profMap);

        retriveClassesGroupsOfPerson(groupAttrs, regexGroup, person, classes, groups,
                profMap);

        // Create a set of all keys (union of both class and group keys for eleve)
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(classes.keySet());
        allKeys.addAll(groups.keySet());

        for (String key : allKeys) {
            // Get or create the ClasseGroupe object
            ClasseGroupe existingGroup = cgMap.getOrDefault(key,
                    new ClasseGroupe(key, new ArrayList<>(), new ArrayList<>()));

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
        // List<EnseignementProf> ensProf = new ArrayList<>();
        Map<String, List<EnseignementProf>> mapListSectionProf = new HashMap<>();
        // profMap.forEach((siren, matMap) -> matMap.forEach((matiere, sourceData) -> {
        // log.info("siren prof: {}", siren);
        // EnseignementProf ens = new EnseignementProf();
        // ens.setMatiere(matiere);
        // ens.setCg(sourceData);
        // ensProf.add(ens);
        // mapListSectionProf.put(siren, ensProf);
        // }));
        // sectionProf.setEtabs(mapListSectionProf);
        // log.info("ensProf: {}", ensProf);

        profMap.forEach((siren, matMap) -> {
            List<EnseignementProf> matListCG = mapListSectionProf.getOrDefault(siren, new ArrayList<>());

            matMap.forEach((matiere, sourceData) -> {
                String nameMatiere = findMatiere(listCodeMatieres, siren, matiere);
                EnseignementProf ens = new EnseignementProf();
                ens.setMatiere(nameMatiere);
                ens.setCg(sourceData);
                matListCG.add(ens);
            });

            mapListSectionProf.put(siren, matListCG);
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

        Map<String, ClasseGroupe> cgMap = new HashMap<>();

        for (String ldapAttr : attributs) {
            List<String> ldapLines = person.getAttribute(ldapAttr);

            if (ldapLines != null) {

                for (String val : ldapLines) {
                    Matcher matcher = pattern.matcher(val);
                    if (matcher.find()) {
                        String siren = matcher.group(1);
                        String value = matcher.group(2);
                        String matiere = matcher.group(4);

                        if (siren != null) {

                            if (matiere != null) {
                                handleProf(profMap, siren, value, matiere, ldapAttr, cgMap);
                            } else {
                                handleEleve(cgMap, siren, value, ldapAttr, classes, groups);
                            }
                        }

                    }
                }
            }
        }

    }

    private void handleEleve(Map<String, ClasseGroupe> eleveMap, String siren, String value, String ldapAttr,
            Map<String, List<String>> classes, Map<String, List<String>> groups) {

        ClasseGroupe sourceData = eleveMap.computeIfAbsent(siren, k -> new ClasseGroupe());
        List<String> cls = new ArrayList<>();
        List<String> grp = new ArrayList<>();

        // Set the name (only if it hasn't been set yet)
        if (sourceData.getNameEtab() == null) {
            sourceData.setNameEtab(siren);
        }

        // Check if it’s a class or a group and add to respective lists
        if (ldapAttr.contains(classAttrs[0])) {
            if (sourceData.getClasses() == null) {
                sourceData.setClasses(new ArrayList<>());
            }
            cls.add(value);
            classes.put(siren, cls);
            sourceData.getClasses().add(value); // Add the class value
        }

        if (ldapAttr.contains(groupAttrs[0])) {
            if (sourceData.getGroupes() == null) {
                sourceData.setGroupes(new ArrayList<>());
            }
            grp.add(value);
            groups.put(siren, grp);
            sourceData.getGroupes().add(value); // Add the group value
        }

        eleveMap.put(siren, sourceData);

    }

    private void handleProf(Map<String, Map<String, ClasseGroupe>> profMap, String siren, String value, String matiere,
            String ldapAttr, Map<String, ClasseGroupe> cgMap) {

        profMap.putIfAbsent(siren, new HashMap<>());
        Map<String, ClasseGroupe> matMap = profMap.get(siren);

        matMap.putIfAbsent(matiere, new ClasseGroupe());
        ClasseGroupe sourceData = matMap.get(matiere);
        sourceData.setNameEtab(siren);

        List<String> cls = new ArrayList<>();
        List<String> grp = new ArrayList<>();

        // Check if it’s a class or a group and add to respective lists
        if (ldapAttr.contains(classAttrs[1])) {
            if (sourceData.getClasses() == null) {
                sourceData.setClasses(new ArrayList<>());
            }
            cls.add(value);
            sourceData.getClasses().add(value); // Add the class value
        }

        if (ldapAttr.contains(groupAttrs[1])) {
            if (sourceData.getGroupes() == null) {
                sourceData.setGroupes(new ArrayList<>());
            }
            grp.add(value);
            sourceData.getGroupes().add(value); // Add the group value
        }

        cgMap.putIfAbsent(matiere, sourceData);
        profMap.putIfAbsent(siren, cgMap);
    }

    private List<String> calculEnseignementSuivis(IExternalUser person, String attrEnsSuivi) {

        List<String> valuesAttr = person.getAttribute(attrEnsSuivi);
        List<String> enseignements = new ArrayList<>();

        if (valuesAttr != null) {

            for (String enseignement : valuesAttr) {
                enseignements.add(enseignement);

            }
        }

        return enseignements;
    }

    private String findMatiere(List<String> attrCodeEns, String siren, String code) {

        String nomMatiere = null;

        if (attrCodeEns != null) {
            for (String codeMat : attrCodeEns) {
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

        return nomMatiere;
    }
}
