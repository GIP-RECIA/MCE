# MCE
Mon Compte ENT


# Règles de modification du mail personnel

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

# Gestion du mail

## Endpoint

`PUT /api/personne/mce/{uid}/update-email`

L'authentification est gérée par **Soffit** (JWT). Le `uid` du path est comparé au `sub` du token pour autorisation.

## Flux de modification

```
PUT /{uid}/update-email
  │
  ├─ [1] PersonneRestController.updateEmail()       (PersonneRestController.java:159)
  │     ├─ Vérification uid == currentUser            (lignes 164-169)
  │     ├─ Validation email == confirmEmail           (lignes 171-174)
  │     └─ Appel personneService.updateEmail()        (ligne 176)
  │
  ├─ [2] PersonneService.updateEmail()               (PersonneService.java:277)
  │     ├─ Récupération PersonneDTO                   (ligne 281)
  │     ├─ Vérification canEditPersonalEmail()        (lignes 287-290)
  │     ├─ setEmailPersonnel(newEmail)                (ligne 298)
  │     ├─ setDateModification(new Date())            (ligne 299)
  │     ├─ saveAndFlush()                             (ligne 301)
  │     ├─ Mise à jour LDAP : updateEmail()           (ligne 306)
  │     ├─ Éviction du cache                          (ligne 314)
  │     └─ Audit log                                  (ligne 316)
  │
  └─ [3] Return 204 No Content
```

## Règles de validation

**DTO (`EmailUpdateRequestDTO.java`)** : `@NotBlank` + `@Email` sur `email` et `confirmEmail`

**Controller** : `email == confirmEmail` → 400 si différent

**Service** : `canEditPersonalEmail()` (PersonneService.java:319-330)

| Type | Mail fixe (`email`) | Mail perso (`emailPersonnel`) | Peut modifier ? |
|------|---------------------|-------------------------------|-----------------|
| Élève | any | any | ✅ (`isEleve()`) |
| Non-élève | absent | absent | ✅ (`isBlank(email)`) |
| Non-élève | absent | présent | ✅ (`isBlank(email)`) |
| Non-élève | présent | absent | ❌ |
| Non-élève | présent | présent | ✅ (`isNotBlank(emailPersonnel)`) |

## Persistance

- **Base de données** : colonne `emailPersonnel` de la table `apersonne` — la colonne `email` n'est **pas** modifiée
- **LDAP** : attribut `mail` (par défaut, configurable via `mail-attribute`) — remplacement via `REPLACE_ATTRIBUTE`
- La DB et LDAP sont dans la même transaction `@Transactional` : si LDAP échoue, la DB est rollbackée

## Fichiers clés

| Fichier | Rôle |
|---------|------|
| `PersonneRestController.java:159` | Endpoint REST |
| `EmailUpdateRequestDTO.java` | DTO d'entrée avec validation |
| `PersonneService.java:277` | Service : `updateEmail()` |
| `PersonneService.java:319` | Service : `canEditPersonalEmail()` |
| `UserDTOFactoryImpl.java:308` | Calcul de `canEditEmail` pour l'UI |
| `APersonne.java:103-107` | Entité : colonnes `email` / `emailPersonnel` |
| `LdapUserDaoImp.java:161` | Synchro LDAP : attribut `mail` |
| `CustomLdapProperties.java:52` | Configuration défaut `mailAttribute` |

## Configuration (`application.yml`)

```yaml
app:
  ldap:
    user-branch:
      mail-attribute: 'mail'         # Attribut LDAP cible (défaut)
```

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