# MCE

# Prérequis / Installation

## Prérequis

- **Java** 11 (JDK)
- **Maven** 3.8+
- **MariaDB** 10.x
- **LDAP** (OpenLDAP ou équivalent)

## Build

```bash
# Compiler (sans tests)
./mvnw clean package -DskipTests

# Compiler avec tests
./mvnw clean package

# Dev
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=dev

# Test
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=test

# Prod
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=prod

```

## Configuration

```bash
cp src/main/resources/application.example.yml src/main/resources/application.yml
```

Éditer `application.yml` avec les valeurs de l'environnement cible (base MariaDB, connexion LDAP, clé JWT Soffit, stockage avatars).

## Exécution

```bash
./mvnw clean spring-boot:run
```

L'API est accessible sur `https://lycees.test.recia.dev` (port et context-path configurables dans `application.yml`). Documentation Swagger sur `/swagger-ui.html`.

## Tests

```bash
# Lancer tous les tests
./mvnw test

# Rapport de couverture JaCoCo (après les tests)
./mvnw jacoco:report
# → ouvrir target/site/jacoco/index.html

# Vérification du formatage
./mvnw spotless:check

# Appliquer le formatage automatiquement
./mvnw spotless:apply
```

## Structure du projet

| Répertoire | Rôle |
|------------|------|
| `src/main/java` | Code source |
| `src/main/resources` | Configuration |
| `src/test/java` | Tests unitaires |
| `docs/` | Documentation des flux métiers |
| `etc/` | Config formateur Eclipse, templates licence |

## Endpoints principaux

### Authentifiés

| Méthode | URL | Description |
|---------|-----|-------------|
| `GET` | `/api/personne/mce/` | Profil complet de l'utilisateur connecté |
| `GET` | `/api/personne/mce/{id}` | Profil d'un enfant par son identifiant |
| `GET` | `/api/personne/mce/getuser` | Informations complètes (`PersonneDTO`) |
| `GET` | `/api/personne/mce/ldap` | Données LDAP brutes |
| `POST` | `/api/personne/mce/{uid}/change-password` | Changement de mot de passe |
| `PUT` | `/api/personne/mce/{uid}/update-email` | Demande de vérification email |
| `POST` | `/api/personne/mce/{uid}/avatar` | Upload d'avatar |
| `GET` | `/api/personne/mce/{uid}/avatar` | Récupération d'avatar |
| `GET` | `/health-check` | Health check (public) |

### Publics (sans authentification)

| Méthode | URL | Description |
|---------|-----|-------------|
| `POST` | `/api/personne/mce/verify-email` | Vérification d'email par code |
| `POST` | `/api/personne/mce/forgot-password` | Envoi d'un code de réinitialisation après saisi de l'uid et de l'email |
| `POST` | `/api/personne/mce/recover-uid` | Récupération du mot de passe sans uid (identité complète requise, réponse générique, aucun uid renvoyé) |
| `POST` | `/api/personne/mce/reset-password` | Réinitialisation de mot de passe |

## Journalisation

Un logger d'audit dédié (`MCE_SPECIAL_LOGGER`) enregistre toutes les opérations dans `mce-password.log` (rotation quotidienne).
