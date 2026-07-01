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
package fr.recia.mce.api.escomceapi.configuration.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fr.recia.mce.api.escomceapi.configuration.bean.CustomLdapProperties.BranchProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.CustomLdapProperties.StructureBranchProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties.ClasseCalculatorProperties;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties.CustomParams;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties.GrpPedagoCalculator;
import fr.recia.mce.api.escomceapi.configuration.bean.ServiceProperties.RelationProperties;

class ConfigBeanPropertiesTest {

    @Test
    void avatarProperties() {
        AvatarProperties a = new AvatarProperties();
        a.setMaxSize(1024L);
        a.setAllowedTypes(List.of("image/png"));
        a.setBaseUrl("http://base");
        a.setStoragePath("/tmp");
        a.setFilename("a.jpg");
        a.setFilenameBackup("b.jpg");

        assertThat(a.getMaxSize()).isEqualTo(1024L);
        assertThat(a.getAllowedTypes()).containsExactly("image/png");
        assertThat(a.getBaseUrl()).isEqualTo("http://base");
        assertThat(a.getStoragePath()).isEqualTo("/tmp");
        assertThat(a.getFilename()).isEqualTo("a.jpg");
        assertThat(a.getFilenameBackup()).isEqualTo("b.jpg");

        AvatarProperties b = new AvatarProperties();
        b.setMaxSize(1024L);
        b.setAllowedTypes(List.of("image/png"));
        b.setBaseUrl("http://base");
        b.setStoragePath("/tmp");
        b.setFilename("a.jpg");
        b.setFilenameBackup("b.jpg");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(null);
        assertThat(a.toString()).isNotNull();

        b.setBaseUrl("other");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void corsProperties() {
        CorsProperties c = new CorsProperties();
        c.setEnable(true);
        c.setAllowCredentials(true);
        c.setAllowedOrigins(List.of("o1", "o2"));
        c.setExposedHeaders(List.of("h1"));
        c.setAllowedHeaders(List.of("h2"));
        c.setAllowedMethods(List.of("GET", "POST"));

        assertThat(c.isEnable()).isTrue();
        assertThat(c.isAllowCredentials()).isTrue();
        assertThat(c.getAllowedOrigins()).containsExactly("o1", "o2");
        assertThat(c.getExposedHeaders()).containsExactly("h1");
        assertThat(c.getAllowedHeaders()).containsExactly("h2");
        assertThat(c.getAllowedMethods()).containsExactly("GET", "POST");
        assertThat(c.toString()).contains("CorsProperties").contains("o1");

        CorsProperties c2 = new CorsProperties();
        c2.setEnable(true);
        c2.setAllowCredentials(true);
        c2.setAllowedOrigins(List.of("o1", "o2"));
        c2.setExposedHeaders(List.of("h1"));
        c2.setAllowedHeaders(List.of("h2"));
        c2.setAllowedMethods(List.of("GET", "POST"));
        assertThat(c).isEqualTo(c2).hasSameHashCodeAs(c2);
        assertThat(c).isNotEqualTo(null);

        c2.setEnable(false);
        assertThat(c).isNotEqualTo(c2);
    }

    @Test
    void domaineProperties() {
        DomaineProperties d = new DomaineProperties();
        d.setGestionRecia(List.of("r1"));
        d.setGestionInclude(List.of("i1"));
        d.setGestionExclude(List.of("e1"));

        assertThat(d.getGestionRecia()).containsExactly("r1");
        assertThat(d.getGestionInclude()).containsExactly("i1");
        assertThat(d.getGestionExclude()).containsExactly("e1");

        DomaineProperties d2 = new DomaineProperties();
        d2.setGestionRecia(List.of("r1"));
        d2.setGestionInclude(List.of("i1"));
        d2.setGestionExclude(List.of("e1"));
        assertThat(d).isEqualTo(d2).hasSameHashCodeAs(d2);
        assertThat(d.toString()).isNotNull();
        d2.setGestionRecia(List.of("other"));
        assertThat(d).isNotEqualTo(d2);
    }

    @Test
    void mailProperties() {
        MailProperties m = new MailProperties();
        m.setRegexValideAddr("regex");
        m.setRegexsDomainesExclus("excl");
        m.setDomainesConfiance("conf");

        assertThat(m.getRegexValideAddr()).isEqualTo("regex");
        assertThat(m.getRegexsDomainesExclus()).isEqualTo("excl");
        assertThat(m.getDomainesConfiance()).isEqualTo("conf");

        MailProperties m2 = new MailProperties();
        m2.setRegexValideAddr("regex");
        m2.setRegexsDomainesExclus("excl");
        m2.setDomainesConfiance("conf");
        assertThat(m).isEqualTo(m2).hasSameHashCodeAs(m2);
        assertThat(m.toString()).isNotNull();
        m2.setDomainesConfiance("diff");
        assertThat(m).isNotEqualTo(m2);
    }

    @Test
    void soffitProperties() {
        SoffitProperties s = new SoffitProperties();
        s.setJwtSignatureKey("key");
        assertThat(s.getJwtSignatureKey()).isEqualTo("key");
        assertThat(s.toString()).contains("SoffitProperties").contains("key");

        SoffitProperties s2 = new SoffitProperties();
        s2.setJwtSignatureKey("key");
        assertThat(s).isEqualTo(s2).hasSameHashCodeAs(s2);
        s2.setJwtSignatureKey("other");
        assertThat(s).isNotEqualTo(s2);
    }

    @Test
    void serviceProperties() {
        ClasseCalculatorProperties classe = new ClasseCalculatorProperties();
        classe.setLdapAttributsClasse("attr");
        classe.setRegexSirenAndClasse("regex");
        classe.setGroupSiren(1);
        classe.setGroupClasse(2);
        classe.setGroupMatiere(3);
        assertThat(classe.getLdapAttributsClasse()).isEqualTo("attr");
        assertThat(classe.getRegexSirenAndClasse()).isEqualTo("regex");
        assertThat(classe.getGroupSiren()).isEqualTo(1);
        assertThat(classe.getGroupClasse()).isEqualTo(2);
        assertThat(classe.getGroupMatiere()).isEqualTo(3);

        ClasseCalculatorProperties classe2 = new ClasseCalculatorProperties();
        classe2.setLdapAttributsClasse("attr");
        classe2.setRegexSirenAndClasse("regex");
        classe2.setGroupSiren(1);
        classe2.setGroupClasse(2);
        classe2.setGroupMatiere(3);
        assertThat(classe).isEqualTo(classe2).hasSameHashCodeAs(classe2);
        assertThat(classe.toString()).isNotNull();
        classe2.setGroupSiren(9);
        assertThat(classe).isNotEqualTo(classe2);

        GrpPedagoCalculator grp = new GrpPedagoCalculator();
        grp.setLdapAttributsClasse("g");
        assertThat(grp.getLdapAttributsClasse()).isEqualTo("g");

        RelationProperties rel = new RelationProperties();
        rel.setRegexUid("uid");
        rel.setRegexRelation("rel");
        rel.setGroupUid(1);
        rel.setGroupTypRel(2);
        rel.setGroupRespFinance(3);
        rel.setGroupRespLegal(4);
        rel.setGroupCodeContact(5);
        rel.setGroupCodePaiement(6);
        assertThat(rel.getRegexUid()).isEqualTo("uid");
        assertThat(rel.getRegexRelation()).isEqualTo("rel");
        assertThat(rel.getGroupUid()).isEqualTo(1);
        assertThat(rel.getGroupTypRel()).isEqualTo(2);
        assertThat(rel.getGroupRespFinance()).isEqualTo(3);
        assertThat(rel.getGroupRespLegal()).isEqualTo(4);
        assertThat(rel.getGroupCodeContact()).isEqualTo(5);
        assertThat(rel.getGroupCodePaiement()).isEqualTo(6);
        RelationProperties rel2 = new RelationProperties();
        rel2.setRegexUid("uid");
        rel2.setRegexRelation("rel");
        rel2.setGroupUid(1);
        rel2.setGroupTypRel(2);
        rel2.setGroupRespFinance(3);
        rel2.setGroupRespLegal(4);
        rel2.setGroupCodeContact(5);
        rel2.setGroupCodePaiement(6);
        assertThat(rel).isEqualTo(rel2).hasSameHashCodeAs(rel2);
        assertThat(rel.toString()).isNotNull();
        rel2.setRegexUid("other");
        assertThat(rel).isNotEqualTo(rel2);

        CustomParams params = new CustomParams();
        params.setLienEdu("edu");
        params.setLienPassEtab("pass");
        params.setDomaineEtabRecia("dom");
        params.setRegexGroupsWithSshaPass("ssha");
        params.setRegexGroupsWithSambaNt("samba");
        assertThat(params.getLienEdu()).isEqualTo("edu");
        assertThat(params.getLienPassEtab()).isEqualTo("pass");
        assertThat(params.getDomaineEtabRecia()).isEqualTo("dom");
        assertThat(params.getRegexGroupsWithSshaPass()).isEqualTo("ssha");
        assertThat(params.getRegexGroupsWithSambaNt()).isEqualTo("samba");
        CustomParams params2 = new CustomParams();
        params2.setLienEdu("edu");
        params2.setLienPassEtab("pass");
        params2.setDomaineEtabRecia("dom");
        params2.setRegexGroupsWithSshaPass("ssha");
        params2.setRegexGroupsWithSambaNt("samba");
        assertThat(params).isEqualTo(params2).hasSameHashCodeAs(params2);
        assertThat(params.toString()).isNotNull();
        params2.setLienEdu("other");
        assertThat(params).isNotEqualTo(params2);

        ServiceProperties sp = new ServiceProperties();
        sp.setClasseProperties(classe);
        sp.setGrpPedagoProperties(grp);
        sp.setRelationProperties(rel);
        sp.setCustomParams(params);
        assertThat(sp.getClasseProperties()).isSameAs(classe);
        assertThat(sp.getGrpPedagoProperties()).isSameAs(grp);
        assertThat(sp.getRelationProperties()).isSameAs(rel);
        assertThat(sp.getCustomParams()).isSameAs(params);
        assertThat(sp.toString()).contains("ServiceProperties");

        ServiceProperties sp2 = new ServiceProperties();
        sp2.setClasseProperties(classe);
        sp2.setGrpPedagoProperties(grp);
        sp2.setRelationProperties(rel);
        sp2.setCustomParams(params);
        assertThat(sp).isEqualTo(sp2).hasSameHashCodeAs(sp2);
        assertThat(sp).isNotEqualTo(null);
    }

    @Test
    void customLdapProperties() {
        BranchProperties branch = new BranchProperties();
        branch.setBaseDN("ou=people");
        branch.setIdAttribute("uid");
        branch.setDisplayNameAttribute("displayName");
        branch.setMailAttribute("mail");
        branch.setSearchAttribute("cn");
        branch.setGroupAttribute("isMemberOf");
        branch.setAvatarAttribute("photo");
        branch.setEleveRelation("er");
        branch.setEleveTuteurEntr("ete");
        branch.setTuteurEleves("te");
        branch.setEleveEnseignements("ee");
        branch.setCodeMatiereEnseignement("cme");
        Set<String> displayed = Set.of("d1");
        Set<String> backend = Set.of("b1");
        branch.setOtherDisplayedAttributes(displayed);
        branch.setOtherBackendAttributes(backend);

        assertThat(branch.getBaseDN()).isEqualTo("ou=people");
        assertThat(branch.getIdAttribute()).isEqualTo("uid");
        assertThat(branch.getDisplayNameAttribute()).isEqualTo("displayName");
        assertThat(branch.getMailAttribute()).isEqualTo("mail");
        assertThat(branch.getSearchAttribute()).isEqualTo("cn");
        assertThat(branch.getGroupAttribute()).isEqualTo("isMemberOf");
        assertThat(branch.getAvatarAttribute()).isEqualTo("photo");
        assertThat(branch.getEleveRelation()).isEqualTo("er");
        assertThat(branch.getEleveTuteurEntr()).isEqualTo("ete");
        assertThat(branch.getTuteurEleves()).isEqualTo("te");
        assertThat(branch.getEleveEnseignements()).isEqualTo("ee");
        assertThat(branch.getCodeMatiereEnseignement()).isEqualTo("cme");
        assertThat(branch.getOtherDisplayedAttributes()).isEqualTo(displayed);
        assertThat(branch.getOtherBackendAttributes()).isEqualTo(backend);
        assertThat(branch.toString()).isNotNull();

        BranchProperties branch2 = new BranchProperties();
        branch2.setOtherDisplayedAttributes(Set.of("d1"));
        branch2.setOtherBackendAttributes(Set.of("b1"));
        branch2.setBaseDN("ou=people");
        branch2.setIdAttribute("uid");
        branch2.setDisplayNameAttribute("displayName");
        branch2.setMailAttribute("mail");
        branch2.setSearchAttribute("cn");
        branch2.setGroupAttribute("isMemberOf");
        branch2.setAvatarAttribute("photo");
        branch2.setEleveRelation("er");
        branch2.setEleveTuteurEntr("ete");
        branch2.setTuteurEleves("te");
        branch2.setEleveEnseignements("ee");
        branch2.setCodeMatiereEnseignement("cme");
        assertThat(branch).isEqualTo(branch2).hasSameHashCodeAs(branch2);
        assertThat(branch).isNotEqualTo(null);
        branch2.setIdAttribute("other");
        assertThat(branch).isNotEqualTo(branch2);

        StructureBranchProperties struct = new StructureBranchProperties();
        struct.setDomaines("dom");
        struct.setNameStruct("name");
        struct.setStructureJointure("jointure");
        struct.setSkin("skin");
        struct.setTypeStruct("type");
        struct.setUai("uai");
        struct.setVille("ville");
        assertThat(struct.getDomaines()).isEqualTo("dom");
        assertThat(struct.getNameStruct()).isEqualTo("name");
        assertThat(struct.getStructureJointure()).isEqualTo("jointure");
        assertThat(struct.getSkin()).isEqualTo("skin");
        assertThat(struct.getTypeStruct()).isEqualTo("type");
        assertThat(struct.getUai()).isEqualTo("uai");
        assertThat(struct.getVille()).isEqualTo("ville");
        assertThat(struct.getBaseDN()).isEqualTo("ou=structures");
        assertThat(struct.getGroupAttribute()).isEqualTo("member");
        assertThat(struct.getIdAttribute()).isEqualTo("ENTStructureSIREN");
        assertThat(struct.getDisplayNameAttribute()).isEqualTo("ESCOStructureNomCourt");
        assertThat(struct.toString()).contains("StructureBranchProperties");

        CustomLdapProperties ldap = new CustomLdapProperties();
        ldap.setUserBranch(branch);
        ldap.setStructBranch(struct);
        assertThat(ldap.getUserBranch()).isSameAs(branch);
        assertThat(ldap.getStructBranch()).isSameAs(struct);
        assertThat(ldap.toString()).contains("CustomLdapProperties");

        CustomLdapProperties ldap2 = new CustomLdapProperties();
        ldap2.setUserBranch(branch);
        ldap2.setStructBranch(struct);
        assertThat(ldap).isEqualTo(ldap2).hasSameHashCodeAs(ldap2);
        assertThat(ldap).isNotEqualTo(null);
    }

    @Test
    void mceProperties() {
        fr.recia.mce.api.escomceapi.configuration.MCEProperties props =
                new fr.recia.mce.api.escomceapi.configuration.MCEProperties();

        CorsProperties cors = new CorsProperties();
        List<String> one = List.of("v");
        cors.setAllowedOrigins(one);
        cors.setExposedHeaders(one);
        cors.setAllowedHeaders(one);
        cors.setAllowedMethods(one);

        AvatarProperties avatar = new AvatarProperties();
        CustomLdapProperties ldap = new CustomLdapProperties();
        ServiceProperties service = new ServiceProperties();
        SoffitProperties soffit = new SoffitProperties();

        props.setCors(cors);
        props.setAvatar(avatar);
        props.setLdap(ldap);
        props.setService(service);
        props.setSoffit(soffit);

        assertThat(props.getCors()).isSameAs(cors);
        assertThat(props.getAvatar()).isSameAs(avatar);
        assertThat(props.getLdap()).isSameAs(ldap);
        assertThat(props.getService()).isSameAs(service);
        assertThat(props.getSoffit()).isSameAs(soffit);
        assertThat(props.toString()).contains("MCEProperties");

        fr.recia.mce.api.escomceapi.configuration.MCEProperties props2 =
                new fr.recia.mce.api.escomceapi.configuration.MCEProperties();
        props2.setCors(cors);
        props2.setAvatar(avatar);
        props2.setLdap(ldap);
        props2.setService(service);
        props2.setSoffit(soffit);
        assertThat(props).isEqualTo(props2).hasSameHashCodeAs(props2);
        assertThat(props).isNotEqualTo(null);
    }
}
