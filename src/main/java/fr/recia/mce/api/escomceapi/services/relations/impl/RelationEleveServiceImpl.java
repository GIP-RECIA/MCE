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
package fr.recia.mce.api.escomceapi.services.relations.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.recia.mce.api.escomceapi.db.dto.PersonneDTO;
import fr.recia.mce.api.escomceapi.db.repositories.APersonneRepository;
import fr.recia.mce.api.escomceapi.ldap.ExternalUserHelper;
import fr.recia.mce.api.escomceapi.ldap.IExternalUser;
import fr.recia.mce.api.escomceapi.services.PersonneService;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact;
import fr.recia.mce.api.escomceapi.services.beans.RelationEleveContact.SensRel;
import fr.recia.mce.api.escomceapi.services.relations.IRelationEleveService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RelationEleveServiceImpl implements IRelationEleveService {

    @Autowired
    private APersonneRepository aPersonneRepository;

    @Autowired
    private PersonneService personneService;

    @Autowired
    private ExternalUserHelper extUserHelper;

    private String regex = "uid=(\\w+),.*";
    private Pattern pattern = Pattern.compile(regex);

    Pattern patternRelation = Pattern.compile("uid=(\\w+),[^$]+\\$([^$]+)\\$([^$]+)\\$(1|2)\\$([^$]+)\\$([^$]+)");
    int grpUid = 1;
    int grpTypRel = 2;
    int grpRespFinance = 3;
    int grpRespLegal = 4;
    int grpCodeContact = 5;
    int grpCodePaiement = 6;

    private void analyseMaitre(final String eleve, final String ldapAttr,
            final String type,
            final boolean autorite,
            final Map<String, RelationEleveContact> uid2relation) {

        IExternalUser personne = personneService.getPersonLdap(eleve);

        List<String> listAttrs = personne.getAttribute(ldapAttr);

        if (listAttrs == null) {
            log.debug("analyse : ldapValues is null for eleve {}", personne.getId());
        } else {
            for (String val : listAttrs) {
                if (val != null) {
                    Matcher m = pattern.matcher(val);
                    if (m.matches()) {
                        String uid = m.group(1);
                        RelationEleveContact re = uid2relation.get(uid);
                        if (re == null) {
                            re = new RelationEleveContact(SensRel.ELEVE2CONTACT);
                            re.setUidRelation(uid);
                            uid2relation.put(uid, re);
                        }
                        if (re.getTypeRelation() == null) {
                            re.setTypeRelation(type);
                        }
                        if (autorite) {
                            re.setAutoriteParental(true);
                        }
                    } else {
                        log.warn("attribut d'eleve ne matche pas");
                    }
                }
            }
        }

    }

    private void analyse(final String eleve, final String ldapAttr,
            final Map<String, RelationEleveContact> uid2relation) {

        IExternalUser personne = personneService.getPersonLdap(eleve);

        List<String> listAttrs = personne.getAttribute(ldapAttr);

        if (listAttrs == null) {
            log.debug("analyse : ladpValues is null for eleve" + personne.getId());
        } else {
            for (String val : listAttrs) {

                if (val != null) {
                    Matcher m = patternRelation.matcher(val);
                    if (!m.matches()) {
                        log.info("no match : " + patternRelation.pattern());
                    } else {
                        String uid = m.group(grpUid);
                        RelationEleveContact re = uid2relation.get(uid);
                        log.info("re : {}", re);

                        if (re == null) {
                            re = new RelationEleveContact(SensRel.ELEVE2CONTACT);
                            // re.setEleve(eleve);
                            re.setUidRelation(uid);
                            uid2relation.put(uid, re);
                            log.info("count");
                        }
                        re.setAutoriteParental(true);
                        String code = m.group(grpTypRel);
                        re.setTypeRelation(code);
                        log.debug("code lien famille = " + code + " " + val);

                    }
                }
            }
        }
        log.info("ldapValues : {}", uid2relation);

    }

    @Override
    public Collection<RelationEleveContact> allRelationEleves(String eleve) {

        if (eleve == null) {
            return Collections.emptyList();
        }
        log.info("eleve: {}", eleve);
        log.info("eleveRelation: {}", extUserHelper.getUserEleveRelationAttribute());

        // uid des personnes en relation avec l'eleve => la description de cette
        // relation
        Map<String, RelationEleveContact> uid2relation = new HashMap<>();
        String eleveRelation = extUserHelper.getUserEleveRelationAttribute();
        String eleveTuteurEntr = extUserHelper.getUserEleveTuteurAttribute();

        analyse(eleve, eleveRelation, uid2relation);
        analyseMaitre(eleve, eleveTuteurEntr, "Maitre", false, uid2relation);

        for (Entry<String, RelationEleveContact> entry : uid2relation.entrySet()) {
            try {
                IExternalUser u = personneService.getPersonLdap(entry.getKey());
                String displayName = u.getDisplayName();

                RelationEleveContact re = entry.getValue();
                re.setDisplayNameRelation(displayName);
            } catch (Exception e) {
                log.error("error allRelationEleves: {}", e);
            }
        }

        return uid2relation.values();
    }

    @Override
    public Collection<RelationEleveContact> allEleveEnRelation(Long parent) {
        Collection<PersonneDTO> allEnfant;
        Map<String, RelationEleveContact> uidEleve2relation = new HashMap<>();

        try {
            allEnfant = aPersonneRepository.findAllEnfantOf(parent);

            for (PersonneDTO enfant : allEnfant) {

                String uid = enfant.getUid();
                String typeRel = enfant.typeOfParent();
                String lienParente = enfant.lienParente();

                RelationEleveContact rep = uidEleve2relation.get(uid);

                if (rep == null) {
                    rep = new RelationEleveContact(SensRel.CONTACT2ELEVE);
                    rep.setContact(parent);
                    rep.setEleve(enfant);
                    rep.setUidRelation(uid);
                    rep.setDisplayNameRelation(enfant.getDisplayName());
                    uidEleve2relation.put(uid, rep);
                }
                if ("Autorite_parentale".equals(typeRel)) {
                    rep.setAutoriteParental(true);
                }
                if (lienParente != null) {
                    rep.setTypeRelation(lienParente);
                }
            }
        } catch (Exception e) {
            log.warn("cannot load enfant : " + parent, e);
        }

        return uidEleve2relation.values();

    }

    @Override
    public List<RelationEleveContact> allApprentiEnRelation(String maitre) {
        List<RelationEleveContact> allApprenti = null;
        IExternalUser personne = personneService.getPersonLdap(maitre);

        List<String> dnApprentis = personne.getAttribute(extUserHelper.getUserTuteurEleveAttribute());

        if (dnApprentis != null) {
            allApprenti = new ArrayList<>(dnApprentis.size());

            for (String val : dnApprentis) {
                if (val != null) {
                    Matcher m = pattern.matcher(val);
                    if (m.matches()) {
                        String uid = m.group(1);
                        try {
                            PersonneDTO eleve = aPersonneRepository.getPersonneByUid(uid);
                            RelationEleveContact re = new RelationEleveContact(SensRel.CONTACT2ELEVE);
                            // re.setContact(maitre);
                            re.setEleve(eleve);
                            re.setTypeRelation("Apprenti");
                            re.setDisplayNameRelation(eleve.getDisplayName());
                            re.setAutoriteParental(false);
                            allApprenti.add(re);
                        } catch (Exception e) {
                            log.warn("can't load apprenti : {}", uid);
                        }
                    } else {
                        log.debug("tutor attribute does not match {}", val);
                    }
                }
            }
        }

        return allApprenti;
    }

}
