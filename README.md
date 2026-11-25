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
- Node.js 20.19 ou plus (Angular 21 LTS, voir `docs/decisions/0002-angular-21-node-20.md`) ; Node 24 en CI et dans Docker
- Docker et Docker Compose

## Développement : deux modes de lancement, un seul `.env`

Le fichier `.env` (copié depuis `.env.exemple`) est écrit pour le **mode 1**. En **mode 2**, le fichier
`infra/docker-compose.dev.yml` surcharge seulement ce qui change quand tout tourne dans Docker
(`URL_SITE` et `CORS_ORIGINES` = `http://localhost`, `SMTP_HOTE` = `mailpit`) et ajoute Mailpit.
Il s'ajoute **toujours après** `infra/docker-compose.yml` ; sans lui, on obtient la configuration de production.

```bash
cp .env.exemple .env          # une seule fois, puis adapter (JWT_SECRET : openssl rand -base64 48)
```

### Mode 1 — backend et frontend lancés à la main (rechargement à chaud)

```bash
# Services : PostgreSQL, Redis (principal + cache), Mailpit (e-mails : http://localhost:8025)
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d postgres redis redis-cache mailpit

# Backend (profil dev) → http://localhost:8080/swagger-ui.html
cd backend && ./mvnw spring-boot:run

# Frontend → http://localhost:4200
cd frontend && npm install && npm start
```

### Mode 2 — tout dans Docker, derrière nginx

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d --build
# Site : http://localhost   API : http://localhost/api   E-mails : http://localhost:8025
```

Après la reconstruction d'un seul service (`... up -d --build backend`), nginx le retrouve tout seul
(résolution DNS Docker toutes les 10 s) : pas besoin de le redémarrer.

## Tests

```bash
cd backend && ./mvnw verify     # nécessite Docker (Testcontainers)
cd frontend && npm test
```

## Ports déjà utilisés sur la machine

Les ports par défaut (5432, 6379, 8080, 4200) peuvent être pris par d'autres projets. Ils sont tous configurables :

| Service | Variable | Défaut |
|---|---|---|
| PostgreSQL exposé par docker-compose | `POSTGRES_PORT_HOTE` (shell) + `BASE_DONNEES_PORT` (`.env`) | 5432 |
| Redis exposé par docker-compose | `REDIS_PORT_HOTE` (shell) + `REDIS_PORT` (`.env`) | 6379 |
| Redis du cache exposé par docker-compose | `REDIS_CACHE_PORT_HOTE` (shell) + `REDIS_CACHE_PORT` (`.env`) | 6380 |
| Backend en dev | `PORT_BACKEND` (`.env`) | 8080 |
| Frontend en dev | `npm start -- --port 4300` | 4200 |
| nginx (docker-compose) | `NGINX_PORT_HOTE` + `URL_SITE_DOCKER` (shell), ex. 8088 et `http://localhost:8088` | 80 |
| Mailpit (mode dev) | `MAILPIT_PORT_WEB_HOTE`, `MAILPIT_PORT_SMTP_HOTE` (shell) | 8025, 1025 |

## À traiter avant la mise en production (phase 9)

- **Images de démonstration** : `frontend/public/demo/images/` (SVG du profil `demo`) ne doit pas se
  retrouver dans l'image Docker de production. Angular copie tout `public/` dans `dist/` : exclure le
  dossier à la compilation de production (par exemple `rm -rf dist/frontend/browser/demo` dans le
  Dockerfile, ou une configuration `assets` dédiée) et vérifier qu'aucune donnée en base ne pointe
  vers `/demo/images/`.

## Redis : deux instances

- **`redis`** (principale) : compteurs de limitation du débit. `maxmemory 64mb`, `noeviction` : un compteur
  n'est jamais évincé avant la fin de sa fenêtre. Pleine, elle refuse les écritures ; le limiteur laisse alors
  passer la requête et journalise l'erreur (testé dans `LimiteurRedisPleinTest`).
- **`redis-cache`** : cache des lectures publiques (10 min, vidé après chaque modification du catalogue).
  `maxmemory 256mb`, `allkeys-lru`, sans persistance. Une seule instance ne suffirait pas : `maxmemory` et la
  politique d'éviction valent pour toute l'instance, toutes bases confondues. Si le cache tombe, le site lit
  la base (erreur journalisée, testé dans `CacheCatalogueTest`).
