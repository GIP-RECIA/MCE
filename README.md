# MCE

# Prérequis / Installation

## Prérequis

- **Java** 11 (JDK)
- **Maven** 3.8+
- **MariaDB** 10.x
- **LDAP** (OpenLDAP ou équivalent)
- **Tomcat** 9+ (déploiement WAR)

## Build

```bash
# Compiler (sans tests)
./mvnw clean package -DskipTests

# Compiler avec tests
./mvnw clean package
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

L'API est accessible sur `http://localhost:8090` (port configurable dans `application.yml`). Documentation Swagger sur `/swagger-ui.html`.

## Tests

```bash
# Lancer tous les tests
mvn test

# Rapport de couverture JaCoCo
mvn jacoco:report
# → ouvrir target/site/jacoco/index.html

# Vérification du formatage
mvn spotless:check

# Appliquer le formatage automatiquement
mvn spotless:apply
```

## Structure du projet

| Répertoire | Rôle |
|------------|------|
| `src/main/java` | Code source |
| `src/main/resources` | Configuration |
| `src/test/java` | Tests unitaires |
| `etc/` | Config formateur Eclipse, templates licence |

## Journalisation

Un logger d'audit dédié (`MCE_SPECIAL_LOGGER`) enregistre toutes les opérations dans `mce-password.log` (rotation quotidienne).
