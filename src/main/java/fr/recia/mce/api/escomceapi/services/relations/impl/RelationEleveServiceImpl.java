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




/**
 * Service de gestion des relations entre élèves et contacts.
 *
 * <p>Ce service agrège les relations issues :
 * <ul>
 *   <li>de l'annuaire LDAP </li>
 *   <li>de la base de données en fallback si LDAP est vide</li>
 * </ul>
 *
 * <p>Il permet notamment :
 * <ul>
 *   <li>de récupérer les relations d'un élève vers ses contacts</li>
 *   <li>de récupérer les élèves liés à un parent/contact</li>
 *   <li>de récupérer les apprentis d'un maître</li>
 * </ul>
 */
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

    private void analyseMaitre(final IExternalUser personne, final String ldapAttr,
            final String type,
            final boolean autorite,
            final Map<String, RelationEleveContact> uid2relation) {

        List<String> listAttrs = personne.getAttribute(ldapAttr);

        if (listAttrs == null) {
            log.warn("Analyse LDAP : Aucun attribut trouvé pour l'attribut '{}' sur l'ID élève : {}", ldapAttr, personne.getId());
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
                        log.warn("Analyse LDAP : La valeur d'attribut '{}' pour l'ID élève {} ne correspond pas au modèle UID attendu : {}", val, personne.getId(), pattern.pattern());
                    }
                }
            }
        }

    }



    /**
     * Analyse les relations LDAP élève/contact et alimente la map des relations.
     *
     * @param ldapAttr attribut LDAP contenant les relations
     * @param uid2relation map des relations
     */
    private void analyse(final IExternalUser personne, final String ldapAttr,
            final Map<String, RelationEleveContact> uid2relation) {

        List<String> listAttrs = personne.getAttribute(ldapAttr);

        if (listAttrs == null) {
            log.warn("Analyse LDAP : Aucun attribut trouvé pour l'attribut '{}' sur l'ID élève : {}", ldapAttr, personne.getId());
        } else {
            for (String val : listAttrs) {

                if (val != null) {
                    Matcher m = patternRelation.matcher(val);
                    if (!m.matches()) {
                        log.debug("Analyse LDAP : La valeur '{}' ne correspond pas au modèle de relation : {}", val, patternRelation.pattern());
                    } else {
                        String uid = m.group(grpUid);
                        RelationEleveContact re = uid2relation.get(uid);
                        log.debug("re : {}", re);

                        if (re == null) {
                            re = new RelationEleveContact(SensRel.ELEVE2CONTACT);
                            // re.setEleve(eleve);
                            re.setUidRelation(uid);
                            uid2relation.put(uid, re);
                            log.debug("compteur");
                        }
                        re.setAutoriteParental(true);
                        String code = m.group(grpTypRel);
                        re.setTypeRelation(code);
                        if (log.isDebugEnabled()) {
                            log.debug("code lien famille = " + code + " " + val);
                        }

                    }
                }
            }
        }
        log.debug("valeursLDAP : {}", uid2relation);

    }


    /**
     * Retourne toutes les relations d'un élève.
     * Ordre :
     *
     *   LDAP
     *   base de données si LDAP vide
     *
     * @param eleve UID de l'élève
     * @return collection de relations
     */
    @Override
    public Collection<RelationEleveContact> allRelationEleves(String eleve) {
        if (eleve == null || eleve.trim().isEmpty()) return Collections.emptyList();

        IExternalUser personne = personneService.retrievePersonLdap(eleve);
        if (personne == null) {
            log.warn("Aucune donnée LDAP trouvée pour l'élève {}", eleve);
            return Collections.emptyList();
        }

        return allRelationEleves(personne);
    }

    @Override
    public Collection<RelationEleveContact> allRelationEleves(IExternalUser personne) {
        Map<String, RelationEleveContact> uid2relation = new HashMap<>();

        String eleveRelation = extUserHelper.getUserEleveRelationAttribute();
        String eleveTuteurEntr = extUserHelper.getUserEleveTuteurAttribute();

        boolean hasRelation = personne.getAttribute(eleveRelation) != null;
        boolean hasTuteur = personne.getAttribute(eleveTuteurEntr) != null;

        if (!hasRelation && !hasTuteur) {
            log.debug("Aucun attribut de relation LDAP présent pour l'utilisateur {}", personne.getId());
            return Collections.emptyList();
        }

        if (hasRelation) analyse(personne, eleveRelation, uid2relation);
        if (hasTuteur) analyseMaitre(personne, eleveTuteurEntr, "Maitre", false, uid2relation);

        // Remplissage des noms via LDAP
        if (!uid2relation.isEmpty()) {
            try {
                List<IExternalUser> users = personneService.getExtDao().getByUids(uid2relation.keySet());
                for (IExternalUser u : users) {
                    RelationEleveContact rel = uid2relation.get(u.getId());
                    if (rel != null) {
                        rel.setDisplayNameRelation(u.getDisplayName());
                    }
                }
            } catch (Exception e) {
                log.error("Erreur lors de la récupération batch LDAP : {}", e.getMessage());
            }
        }
        //BASE DE DONNÉES SI LDAP VIDE
        if (uid2relation.isEmpty()) {
            log.warn("LDAP vide pour l'utilisateur {}. Tentative de secours en base de données.", personne.getId());

            try {
                List<RelationEleveContact> dbRelations = aPersonneRepository.findAllParentOfEleve(personne.getId());

                if (dbRelations != null && !dbRelations.isEmpty()) {
                    log.info("Secours DB réussi : {} relation(s) trouvée(s) pour l'utilisateur {}",
                            dbRelations.size(), personne.getId());

                    for (RelationEleveContact r : dbRelations) {
                        log.debug("  → Relation DB : uidRelation={} | displayName={} | type={} | lienParente={}",
                                r.getUidRelation(),
                                r.getDisplayNameRelation(),
                                r.getTypeRelation(),
                                r.getLienParente());
                    }

                    return dbRelations;
                } else {
                    log.warn("Aucune relation trouvée en base non plus pour l'utilisateur {}", personne.getId());
                }
            } catch (Exception e) {
                log.error("Erreur lors du secours DB pour l'utilisateur {} : {}", personne.getId(), e.getMessage());
            }
        }

        if (!uid2relation.isEmpty()) {
            log.debug("LDAP a trouvé {} relation(s) pour l'utilisateur {}", uid2relation.size(), personne.getId());
        }

        return uid2relation.isEmpty() ? Collections.emptyList() : uid2relation.values();
    }


    /**
     * Retourne tous les élèves associés à un parent.
     *
     * @param parent identifiant du parent
     * @return relations parent → élèves
     */
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
            log.error("Échec du chargement des enfants pour l'ID parent [{}] : Erreur technique - Détail : {}", parent, e.getMessage());
        }

        return uidEleve2relation.values();

    }

    @Override
    public List<RelationEleveContact> allApprentiEnRelation(String maitre) {
        List<RelationEleveContact> allApprenti = null;
        IExternalUser personne = personneService.retrievePersonLdap(maitre);

        List<String> dnApprentis = personne.getAttribute(extUserHelper.getUserTuteurEleveAttribute());

        if (dnApprentis != null) {
            allApprenti = new ArrayList<>(dnApprentis.size());

            for (String val : dnApprentis) {
                if (val != null) {
                    Matcher m = pattern.matcher(val);
                    if (m.matches()) {
                        String uid = m.group(1);
                        try {
                            PersonneDTO eleve = personneService.retrievePersonnebyUid(uid);
                            RelationEleveContact re = new RelationEleveContact(SensRel.CONTACT2ELEVE);
                            // re.setContact(maitre);
                            re.setEleve(eleve);
                            re.setTypeRelation("Apprenti");
                            re.setDisplayNameRelation(eleve.getDisplayName());
                            re.setAutoriteParental(false);
                            allApprenti.add(re);
                        } catch (Exception e) {
                            log.warn("Échec du chargement des détails de l'apprenti pour l'UID [{}] : Détail : {}", uid, e.getMessage());
                        }
                    } else {
                        log.debug("Analyse LDAP : Le DN de l'apprenti '{}' ne correspond pas au modèle UID : {}", val, pattern.pattern());
                    }
                }
            }
        }

        return allApprenti;
    }

}
