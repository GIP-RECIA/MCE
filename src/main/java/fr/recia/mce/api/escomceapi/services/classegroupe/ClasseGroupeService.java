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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.recia.mce.api.escomceapi.ldap.ConstLDAP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.configuration.MCEProperties;
import fr.recia.mce.api.escomceapi.ldap.LdapUserDto;
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

    private final Pattern patternNomMatiere;
    private final Pattern patternEnsCGMatiere;
    private final Pattern patternElvCGMatiere;

    @Autowired
    private StructureService structureService;

    public ClasseGroupeService(MCEProperties mceProperties) {
        this.patternNomMatiere = Pattern.compile(mceProperties.getService().getCgCalculator().getRegexNomMatiere());
        this.patternEnsCGMatiere = Pattern.compile(mceProperties.getService().getCgCalculator().getRegexEnsCGMatiere());
        this.patternElvCGMatiere = Pattern.compile(mceProperties.getService().getCgCalculator().getRegexElvCGMatiere());
    }

    public ClasseGroupeDTO calculCG(LdapUserDto person) {
        final String uid = person.getId();
        log.debug("Attributs complets disponibles pour uid={} : {}", uid, person);
        final String profil = person.getAttribute(ConstLDAP.PROFILS).stream().findFirst().orElse("");
        log.debug("Profil utilisateur détecté pour uid={} : {}", uid, profil);

        // Populate sectionProf
        SubSectionProf sectionProf = new SubSectionProf();
        sectionProf.setEtabs(calculEnseignementCGProf(person));

        // Populate sectionEleve
        SubSectionEleve sectionEleve = new SubSectionEleve();
        List<String> enseignementEleve = calculEnseignementEleve(person);
        List<ClasseGroupe> cgEleve = calculCGEleve(person);
        sectionEleve.setEtabs(cgEleve);
        sectionEleve.setEnseignementSuivis(enseignementEleve);

        // Populate ClasseGroupeDTO with the processed sections
        ClasseGroupeDTO cg = new ClasseGroupeDTO();
        cg.setSectionProf(sectionProf);
        cg.setSectionEleve(sectionEleve);
        return cg;
    }

    /**
     * Parmi une liste d'enseignements, retourne (ou créé) l'enseignement correspondant à un nom de matière donné
     * Utile car c'est une liste et non une map indéxée par nom de matière
     */
    private EnseignementProf createOrGetEnseignementInEtab(List<EnseignementProf> listeEnseignements, String nomMatiere){
        for(EnseignementProf ens : listeEnseignements){
            if(ens.getMatiere().equals(nomMatiere)){
                return ens;
            }
        }
        // Si on passe ici c'est qu'il n'y a pas encore d'enseignenement, donc on le créé, on l'ajoute à la liste et on le retourne
        EnseignementProf ens = new EnseignementProf(nomMatiere);
        listeEnseignements.add(ens);
        return ens;
    }

    /**
     * Retourne les enseignements d'un enseignant sur des classesGroupes
     */
    private Map<String, List<EnseignementProf>> calculEnseignementCGProf(LdapUserDto person) {
        // Première étape : associer tous les codes matières par établissement a un nom de matière
        Map<String, Map<String, String>> matiereByCodeAndEtab = new HashMap<>();
        List<String> codeNomMatieres = person.getAttribute(ConstLDAP.ENS_CLASSES_MATIERES_ETAB);
        for(String codeNomMatiere : codeNomMatieres){
            final Matcher matcherNomMatiere = patternNomMatiere.matcher(codeNomMatiere);
            if (matcherNomMatiere.matches()) {
                final String siren = matcherNomMatiere.group(1);
                final String codeMatiere = matcherNomMatiere.group(2);
                final String nomMatiere = matcherNomMatiere.group(3);
                if(!matiereByCodeAndEtab.containsKey(siren)){
                    matiereByCodeAndEtab.put(siren, new HashMap<>());
                }
                if(!matiereByCodeAndEtab.get(siren).containsKey(codeMatiere)){
                    matiereByCodeAndEtab.get(siren).put(codeMatiere, nomMatiere);
                }
            }
        }

        // Deuxième étape : créer l'objet final à retourner
        Map<String, List<EnseignementProf>> matieresByEtab = new HashMap<>();
        List<String> classeCodeMatieres = person.getAttribute(ConstLDAP.ENS_CLASSES_MATIERES);
        for(String classeCodeMatiere : classeCodeMatieres) {
            final Matcher matcher = patternEnsCGMatiere.matcher(classeCodeMatiere);
            if (matcher.matches()) {
                final String siren = matcher.group(1);
                final String classeName = matcher.group(2);
                final String codeMatiere = matcher.group(3);
                final String structureName = structureService.findStructureBySiren(siren).getName();
                if(!matieresByEtab.containsKey(structureName)){
                    matieresByEtab.put(structureName, new ArrayList<>());
                }
                final String nomMatiere = matiereByCodeAndEtab.get(siren).get(codeMatiere);
                EnseignementProf enseignementProf = createOrGetEnseignementInEtab(matieresByEtab.get(structureName), nomMatiere);
                enseignementProf.getCg().getClasses().add(classeName);
            }
        // TODO : gérer aussi les groupes
        }

        return matieresByEtab;
    }


    /**
     * Retourne les enseignements d'un élève
     */
    private List<String> calculEnseignementEleve(LdapUserDto person) {
        return person.getAttribute(ConstLDAP.ELEVE_ENSEIGNEMENTS);
    }

    /**
     * Retourne les classes et groupes d'un élève par établissement
     */
    private List<ClasseGroupe> calculCGEleve(LdapUserDto person) {
        Map<String, ClasseGroupe> cgElevesIndexedBySiren = new HashMap<>();
        List<String> classes = person.getAttribute(ConstLDAP.ELEVE_CLASSES);
        List<String> groupes = person.getAttribute(ConstLDAP.ELEVE_GROUPES);
        for(String classe : classes){
            final Matcher matcher = patternElvCGMatiere.matcher(classe);
            if (matcher.matches()) {
                final String siren = matcher.group(1);
                final String classeName = matcher.group(2);
                final String structureName = structureService.findStructureBySiren(siren).getName();
                if(!cgElevesIndexedBySiren.containsKey(siren)){
                    cgElevesIndexedBySiren.put(siren, new ClasseGroupe(structureName));
                }
                cgElevesIndexedBySiren.get(siren).getClasses().add(classeName);
            }
        }
        for(String groupe : groupes){
            final Matcher matcher = patternElvCGMatiere.matcher(groupe);
            if (matcher.matches()) {
                final String siren = matcher.group(1);
                final String groupeName = matcher.group(2);
                final String structureName = structureService.findStructureBySiren(siren).getName();
                if(!cgElevesIndexedBySiren.containsKey(siren)){
                    cgElevesIndexedBySiren.put(siren, new ClasseGroupe(structureName));
                }
                cgElevesIndexedBySiren.get(siren).getGroupes().add(groupeName);
            }
        }
        return new ArrayList<>(cgElevesIndexedBySiren.values());
    }

}
