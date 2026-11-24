# Backend — API Spring Boot

Spring Boot 4.2 · Java 21 · Maven (wrapper `./mvnw`) · package racine `sn.boutique`.

## Lancer

```bash
docker compose -f ../infra/docker-compose.yml up -d postgres redis
./mvnw spring-boot:run            # profil dev par défaut
```

- Swagger UI (dev uniquement) : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health

Sans docker-compose, la classe de test `LancementLocalAvecConteneurs` démarre l'application avec PostgreSQL et Redis dans des conteneurs jetables.

## Profils

| Profil | Usage |
|---|---|
| `dev` (défaut) | valeurs par défaut locales, Swagger actif, logs SQL |
| `prod` | aucun secret par défaut, Swagger désactivé, logs JSON (ECS) |

Les variables d'environnement sont décrites dans `../.env.exemple`. En dev, le fichier `../.env` est lu automatiquement.

## Organisation

```
sn.boutique
├── noyau/config   configuration transverse (sécurité, OpenAPI, cache, propriétés)
├── noyau/erreur   exceptions métier + GestionnaireGlobalErreurs (ProblemDetail)
└── <domaine>/     catalogue, commande, compte… (à partir de la phase 1)
```

## Dépendances et justification

| Dépendance | Pourquoi |
|---|---|
| webmvc, validation | API REST, validation des entrées (Bean Validation) |
| data-jpa, postgresql, flyway | persistance ; schéma géré **uniquement** par migrations Flyway |
| data-redis, cache | cache des lectures du catalogue |
| security, oauth2-resource-server | sécurité ; JWT encodé/décodé avec Nimbus, intégré à Spring (pas de lib tierce) |
| mail, thymeleaf | e-mails transactionnels mis en forme |
| actuator | `/actuator/health` pour les healthchecks Docker |
| springdoc-openapi | documentation Swagger de l'API |
| mapstruct (+ lombok-mapstruct-binding) | conversion entités ↔ DTO générée à la compilation, sans réflexion |
| lombok | réduction du code répétitif (journalisation, constructeurs) |
| cloudinary-http5 | stockage des images et vidéos (phase 2) |
| tika-core | vérification du type réel des fichiers envoyés (phase 2) |
| testcontainers | tests d'intégration sur un vrai PostgreSQL et un vrai Redis |

## Erreurs

Toutes les erreurs suivent la RFC 9457 (`application/problem+json`) :

```json
{
  "type": "about:blank",
  "title": "Données invalides",
  "status": 400,
  "detail": "La requête contient des champs invalides.",
  "instance": "/api/auth/inscription",
  "code": "VALIDATION",
  "horodatage": "2026-11-24T10:45:15Z",
  "erreurs": [{ "champ": "email", "message": "L'e-mail n'est pas valide" }]
}
```
