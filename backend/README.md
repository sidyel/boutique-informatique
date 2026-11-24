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

## Données de démonstration (profil `demo`)

```bash
# Dans .env : DEMO_ADMIN_MOT_DE_PASSE=<12 caractères minimum> (sinon pas de compte admin)
SPRING_PROFILES_ACTIVE=dev,demo ./mvnw spring-boot:run
```

Au démarrage, `ChargeurDonneesDemo` charge `src/main/resources/demo/catalogue.json` et `site.json` :
16 catégories avec leurs caractéristiques, 19 marques, 36 produits (44 variantes, prix indicatifs en FCFA),
zones de livraison du Sénégal, pages légales à compléter, bannières, codes promo `BIENVENUE10` et
`LIVRAISONOFFERTE`, un guide d'achat, une collection « Petits prix » et un compte SUPER_ADMIN.

- **Idempotent** : chaque objet est retrouvé par sa clé naturelle (slug, SKU, code…) et créé seulement s'il
  manque ; ce qui a été modifié dans l'admin n'est pas écrasé.
- **Images** : SVG neutres servis par le frontend (`frontend/public/demo/images/`), aucune photo de
  fabricant (droits d'auteur). Les noms de modèles sont réels, les prix et stocks sont indicatifs.
- Jamais en production.

## Authentification (ADR 0003)

Jeton d'accès JWT (15 min, en-tête `Authorization: Bearer`) et jeton de rafraîchissement dans un cookie
HttpOnly `SameSite=Strict` limité à `/api/auth` (30 jours pour un client, 12 h pour un rôle interne).
`/api/auth/rafraichir` et `/api/auth/deconnexion` exigent l'en-tête `Origin` du site : avec curl,
l'ajouter explicitement.

```bash
API=http://localhost:8080; ORIGINE=http://localhost:4200
curl -i -X POST $API/api/auth/inscription -H 'Content-Type: application/json'   -d '{"email":"awa.diop@exemple.sn","motDePasse":"Thieboudienne-2026","prenom":"Awa","nom":"Diop"}'
curl -i -c cookies.txt -X POST $API/api/auth/connexion -H 'Content-Type: application/json'   -d '{"email":"awa.diop@exemple.sn","motDePasse":"Thieboudienne-2026"}'
curl -i $API/api/compte/profil -H "Authorization: Bearer <jetonAcces>"
curl -i -b cookies.txt -c cookies.txt -X POST $API/api/auth/rafraichir -H "Origin: $ORIGINE"
curl -i -b cookies.txt -c cookies.txt -X POST $API/api/auth/deconnexion -H "Origin: $ORIGINE"
```

### E-mails en développement (Mailpit)

```bash
docker compose -f ../infra/docker-compose.yml --profile dev up -d mailpit   # interface : http://localhost:8025
```

Vérification de l'adresse : ouvrir l'e-mail dans Mailpit, copier le jeton du lien (`…?jeton=XXXX`), puis :

```bash
curl -i -X POST $API/api/auth/verification-email -H 'Content-Type: application/json' -d '{"jeton":"XXXX"}'
curl -i -X POST $API/api/auth/mot-de-passe-oublie -H 'Content-Type: application/json' -d '{"email":"awa.diop@exemple.sn"}'
curl -i -X POST $API/api/auth/reinitialiser -H 'Content-Type: application/json'   -d '{"jeton":"YYYY","motDePasse":"Nouveau-mot-de-passe-2026"}'
```

En dev, la sonde `/actuator/health` passe à DOWN si Mailpit n'est pas lancé.

`JWT_SECRET` (`openssl rand -base64 48`) est obligatoire en production ; en dev, une clé jetable est
générée s'il est absent ou invalide (les sessions sont alors perdues au redémarrage).
