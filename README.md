# Boutique informatique

Site e-commerce de matériel informatique pour le Sénégal (prix en FCFA).

- Conventions de code : [`CLAUDE.md`](CLAUDE.md)
- Cahier des charges : [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md)
- Décisions d'architecture : [`docs/decisions/`](docs/decisions/)

## Structure

| Dossier | Contenu |
|---|---|
| `backend/` | API Spring Boot (Java 21, Maven) |
| `frontend/` | Application Angular avec rendu serveur (SSR) |
| `infra/` | docker-compose, configuration nginx |
| `docs/` | Documentation fonctionnelle et technique |

## Prérequis

- Java 21
- Node.js 24 LTS (Angular 22 exige Node ≥ 22.22 ou ≥ 24.15)
- Docker et Docker Compose

## Démarrage rapide (développement)

```bash
# 1. Variables d'environnement
cp .env.exemple .env          # puis adapter les valeurs

# 2. Base de données et cache
docker compose -f infra/docker-compose.yml up -d postgres redis

# 3. Backend (profil dev) → http://localhost:8080/swagger-ui.html
cd backend && ./mvnw spring-boot:run

# 4. Frontend → http://localhost:4200
cd frontend && npm install && npm start
```

## Environnement complet (Docker)

```bash
docker compose -f infra/docker-compose.yml up -d --build
# Site : http://localhost   API : http://localhost/api
```

## Tests

```bash
cd backend && ./mvnw verify     # nécessite Docker (Testcontainers)
cd frontend && npm test
```
