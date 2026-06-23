# MCE
Mon Compte ENT


# Gestion du mail

## Tableau de référence

| Type utilisateur | Mail fixe (`email`) | Mail personnel (`emailPersonnel`) | Peut modifier le mail personnel ? |
|---|---|---|---|
| Élève | absent | absent | ✅ Oui |
| Élève | absent | présent | ✅ Oui |
| Élève | présent | absent | ✅ Oui |
| Élève | présent | présent | ✅ Oui |
| Non-élève | absent | absent | ✅ Oui |
| Non-élève | absent | présent | ✅ Oui |
| Non-élève | présent | absent | ❌ Non |
| Non-élève | présent | présent | ✅ Oui |

## Règle générale

Un utilisateur **ne peut pas** modifier son mail personnel uniquement dans le cas suivant :

- il est **non-élève**
- il a un **mail fixe présent**
- il n'a **pas de mail personnel**

Dans tous les autres cas, la modification est autorisée.

## Cas spécial : 

### Cas 1 — Non-élève avec mail fixe + mail personnel présents au départ

Si un non-élève arrive avec :
- mail fixe **présent**
- mail personnel **présent**

→ il peut modifier son mail personnel.  
Si pendant la même session il **vide ou supprime** son mail personnel, `mailEditable` reste à `true` : il peut encore corriger son mail dans la même session.

## Filtrage du mail affiché (mailFixeConfiance)

Quand le frontend récupère le profil utilisateur, le `email` (mail fixe) affiché dépend du niveau de confiance :

- **Domaines de confiance** (`mail.domainesConfiance`) : si le domaine du `mailFixe` est dans cette liste, le mail est considéré comme fiable (`mailFixeConfiance = true`) et affiché directement.
- **Personnel** (`EnumPublic.PERSONNEL`) : si l'utilisateur est un personnel, son mail est toujours considéré comme fiable.
- **Sinon** : le système va chercher un mail préalablement confirmé dans la table `cerbere_confirmation` (où `confirmation` n'est pas null). Si aucun mail confirmé n'existe, le `mailFixe` est utilisé par défaut.

| Condition | mailFixeConfiance | email affiché |
|-----------|-------------------|---------------|
| Domaine dans `domainesConfiance` | `true` | `mailFixe` |
| Utilisateur `PERSONNEL` | `true` | `mailFixe` |
| Domaine non fiable + mail confirmé en DB | `false` | Mail confirmé |
| Domaine non fiable + aucun mail confirmé | `false` | `mailFixe` (fallback) |

## Configuration (`application.yml`)

```yaml
mail:
  regexValideAddr: '[_A-Za-z0-9-]+(\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*(\.[A-Za-z-]{2,4})'
  regexsDomainesExclus: 'netocentre.fr touraine-eschool.fr chercan.fr colleges41.fr mon-e-college.loiret.fr e-college.indre.fr colleges-eureliens.fr'
  domainesConfiance: 'ac-orleans-tours.fr educagri.fr recia.fr'
```
 
# Gestion du réseau des établissements (domaine.gestion.recia)
 
Définit si le réseau d'un établissement est **géré par le GIP RECIA**, ce qui permet d'afficher le lien de changement de mot de passe établissement (`pwd.escolan.recia.fr`) et d'adapter le message ÉduConnect.
 
## Propriétés (`application.yml`)
 
```yaml
domaine:
  gestion-recia:
    - lycees.netocentre.fr
    - cfa.netocentre.fr
    - www.chercan.fr
  gestion-include:
    - "0370074E"
    - "0410860M"
    - "0280957N"
    - "0180037T"
    - "0180766K"
    - "0360718K"
  gestion-exclude: []
```
 
## Logique

La méthode `isReseauRecia(PersonneDTO)` itère sur chaque UAI de la personne (`ESCOUAI`), cherche la structure correspondante via `findStructureByUai(uai)`, puis délègue à `isReseauRecia(IExternalStructure)` :

```
                    PersonneDTO
                        │
                        ▼
            ┌───────────────────────┐
            │  Pour chaque UAI      │
            │  (ESCOUAI)            │
            └──────────┬────────────┘
                       │
                       ▼
            ┌───────────────────────┐
            │ findStructureByUai()  │
            └──────────┬────────────┘
                       │
                       ▼
                    Structure
                        │
                        ▼
            Domaine dans setDomaineEtabRecia ?
                        │
            ┌───────────┴───────────┐
            │ OUI                   │ NON
            ▼                       ▼
    UAI dans exclude ?        UAI dans include ?
            │                       │
    ┌───────┴───────┐       ┌───────┴───────┐
    │ OUI    │ NON  │       │ OUI    │ NON  │
    │ false  │ true │       │ true   │ false│
    └───────┴───────┘       └───────┴───────┘
```

Une seule UAI valide suffit à retourner `true` (comportement OR).

## Code

| Fichier | Rôle |
|---------|------|
| `DomaineProperties.java` | `@ConfigurationProperties(prefix = "domaine")` |
| `StructureServiceImpl.java` | `isReseauRecia(PersonneDTO)` → itère UAI → `findStructureByUai` → `isReseauRecia(IExternalStructure)` → `isDomaineRecia()` |
| `UserDTOFactoryImpl.java:327` | `passEtab = structureService.isReseauRecia(model)` |
 
## Résumé
 
| Propriété | Rôle |
|-----------|------|
| `domaine.gestion-recia` | Domaines des établissements gérés par RECIA |
| `domaine.gestion-include` | UAI/SIREN inclus en forcé (cités scolaires) |
| `domaine.gestion-exclude` | UAI/SIREN exclus même si domaine RECIA |
 
# Gestion des mots de passe

## Endpoint

`POST /api/personne/mce/{uid}/change-password`

L'authentification est gérée par **Soffit** (JWT) — il n'y a pas de login local. Le mot de passe n'est utilisé que pour le **changement** (vérification de l'ancien mot de passe).

## Algorithme de hachage

- **Argon2** (via Spring Security `Argon2PasswordEncoder`) — utilisé pour tous les nouveaux mots de passe
- **SSHA** (SHA-1 + sel 8 octets, Base64) — legacy, conservé pour la vérification des anciens mots de passe
- **Samba :** les hashs LM (DES) et NT (MD4) sont **regénérés à chaque changement de mot de passe** si l'utilisateur appartient aux groupes LDAP configurés (`regexGroupsWithSambaNt`). Sinon, les champs sont mis à `null` en base.

Stockage avec préfixes : `{ARGON2}...` ou `{SSHA}...`

## Règles de validation

| Règle | Détail |
|-------|--------|
| Longueur minimale | 12 caractères |
| Types de caractères | Au moins 3 types sur 4 (minuscules, majuscules, chiffres, symboles) |
| Confirmation | newPass == confirmPass |
| Historique | Ne doit pas être identique à un mot de passe déjà utilisé (table `cerbere_password`) |

## Flux de changement

1. Validation du DTO (oldPass, newPass, confirmPass)
2. Vérification de l'ancien mot de passe
3. Validation de la force du nouveau mot de passe
4. Vérification dans l'historique `cerbere_password`
5. Hachage du nouveau mot de passe (Argon2)
6. Fermeture de l'ancienne entrée d'historique + insertion de la nouvelle
7. Mise à jour en base : `apersonne.password`, `dateModification` (et `sambaLMPassword`/`sambaNTPassword` uniquement si l'utilisateur est dans les groupes Samba)
8. Mise à jour LDAP : remplacement de l'attribut `userPassword`

## Fichiers clés

| Fichier | Rôle |
|---------|------|
| `PasswordService.java` | Cœur : hash, verify, validation, change |
| `CerberePassword.java` | Entité d'historique |
| `CerberePasswordRepository.java` | Repository historique |
| `PersonneRestController.java:136` | Endpoint REST |
| `UserDTOFactoryImpl.java:466` | Factory qui orchestre le changement |
| `LdapUserDaoImp.java:118` | Synchro LDAP |
| `application.yml:77` | Configuration des groupes SSHA/Samba |

## Configuration (`application.yml`)

```yaml
app:
  service:
    custom-params:
      regex-groups-with-ssha-pass: '^coll:Collectivites:GIP-RECIA:Tous_GIP-RECIA$'
      regex-groups-with-samba-nt: '^coll:Applications:ESCOLAN:GCR:.*$'
```

## Journalisation

Un logger d'audit dédié (`MCE_SPECIAL_LOGGER`) enregistre toutes les opérations dans `mce-password.log` (rotation quotidienne).