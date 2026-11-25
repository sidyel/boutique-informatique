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

- **`redis`** (principale) : compteurs de limitation du débit, qui ont tous une expiration. `maxmemory 256mb`,
  `volatile-ttl` : pleine, elle supprime les compteurs les plus proches de leur fin au lieu de refuser les
  écritures. Si Redis est malgré tout en erreur (arrêté, injoignable, plein), les routes d'authentification
  restent limitées par un **limiteur de secours en mémoire locale** (10 000 compteurs au plus) et la panne
  est journalisée au niveau ERROR avec le marqueur `ALERTE_REDIS_PLEIN` ou `ALERTE_REDIS_INDISPONIBLE`, à
  brancher sur une alerte (testé dans `LimiteurRedisPleinTest`).
- **`redis-cache`** : cache des lectures publiques (10 min, vidé après chaque modification du catalogue).
  `maxmemory 256mb`, `allkeys-lru`, sans persistance. Une seule instance ne suffirait pas : `maxmemory` et la
  politique d'éviction valent pour toute l'instance, toutes bases confondues. Si le cache tombe, le site lit
  la base (erreur journalisée, testé dans `CacheCatalogueTest`).

## Médias (Cloudinary)

Règles complètes : [ADR 0005](docs/decisions/0005-medias-cloudinary.md).

- **Clé** : `CLOUDINARY_URL` dans `.env` (jamais dans Git). Sans clé, l'application démarre et les envois
  répondent `503`. Les tests n'appellent jamais Cloudinary (faux stockage en mémoire).
- **Dossiers** : `dev/boutique` en développement, `boutique` en production (`boutique.medias.dossier-racine`).
- **Photos produit** : JPEG, PNG ou WebP, 5 Mo, 800 px au moins sur le plus petit côté, 8 000 px au plus,
  15 par produit, texte alternatif descriptif obligatoire.
- **Vidéo produit** : une par produit, MP4 ou WebM, 100 Mo au plus, envoyée directement du navigateur à
  Cloudinary (signature valable 50 min, pour les connexions mobiles lentes). **Conseillé : 20 à 40 secondes
  et moins de 30 Mo** — la fiche se charge plus vite sur mobile et la vidéo est regardée jusqu'au bout. Ou un
  lien YouTube public dont l'intégration est autorisée.
- **Identité du site** : logo et logo sombre (SVG accepté, nettoyé), favicon carré de 512 px au moins
  (décliné en 32, 180, 192 et 512 px), image de partage de 600 × 315 px au moins (1200 × 630 conseillé).
- **Bannières** : ordinateur 1920 × 600 px, mobile 800 × 1000 px conseillés (avertissement si l'image s'en
  éloigne, sans blocage).
- **Fichiers à nettoyer** (tâche planifiée en phase 9) : table `media_orphelin` (suppressions en échec) et
  signatures `signature_envoi_video` expirées jamais utilisées (vidéos envoyées mais jamais déclarées).

## Limitation du débit (nginx)

nginx limite les requêtes par IP (réponse `429`), en plus des limites de l'authentification dans le backend.
Valeurs généreuses à cause du CGNAT des opérateurs mobiles (beaucoup d'abonnés derrière une même IP),
réglables par variables d'environnement du service `nginx` (`infra/docker-compose.yml`) :

| Zone | Routes | Débit / rafale par défaut |
|---|---|---|
| `api` | `/api/`, `/flux/` | `NGINX_DEBIT_API=20r/s`, `NGINX_RAFALE_API=100` |
| `recherche` | `/api/recherche` (dont l'autocomplétion) | `NGINX_DEBIT_RECHERCHE=5r/s`, `NGINX_RAFALE_RECHERCHE=40` |
| `pages` | pages HTML rendues par le SSR (les fichiers statiques ne sont pas comptés) | `NGINX_DEBIT_PAGES=5r/s`, `NGINX_RAFALE_PAGES=40` |

La zone `pages` existe parce que le SSR appelle le backend directement, sans repasser par nginx : sans
elle, des pages de liste aux filtres aléatoires feraient travailler le backend sans limite. Si un CDN est
placé devant nginx, la clé doit devenir l'IP réelle du visiteur (module `real_ip`).
