# ADR 0006 — HTTPS + HTTP/2 dans nginx, et mesure des performances

- **Statut** : accepté
- **Date** : 2026-11-25
- **Phase** : 3b (validation de la page catégorie par le workflow Lighthouse)

## Contexte

Le workflow Lighthouse mesurait le site en HTTP/1.1 sur `http://localhost`. Après le passage des images de
démonstration en WebP (qui ressemblent aux photos Cloudinary de production), le LCP simulé dépassait 2,5 s
sur l'accueil et la catégorie. Lighthouse ne mesure pas l'affichage réel : il rejoue le chargement dans un
réseau lent simulé (150 ms de latence, 1,6 Mbit/s). En HTTP/1.1, au plus 6 connexions, chacune à ouvrir,
servent une trentaine de fichiers (25 fichiers JS, polices, image) : l'image principale attend derrière le
JS. Le site de production sera servi en HTTPS avec HTTP/2 (une connexion multiplexée).

Mesures locales (3 passages, médiane du LCP) avec le même build :

| | Accueil | Catégorie |
|---|---|---|
| HTTP/1.1 | 3,17 s | 3,62 s |
| HTTPS + HTTP/2 | 2,19 s | 2,27 s |

Un démarrage d'Angular retardé jusqu'à l'affichage de l'image principale a été essayé : l'image était bien
peinte dès le premier rendu, mais le LCP simulé ne baissait presque pas. Il n'a pas été retenu.

## Décision

1. **Une seule configuration nginx de production, en HTTPS** (`infra/nginx/modes/https.conf.template`) :
   `listen 443 ssl`, `http2 on`, redirection permanente 80 → 443 (sauf le défi Let's Encrypt
   `/.well-known/acme-challenge/`), profil TLS « intermediate » de Mozilla (TLS 1.2 et 1.3, suites AEAD à
   confidentialité persistante ; TLS 1.2 reste nécessaire pour les Android d'avant la version 10), cache de
   session, HSTS (`max-age=31536000; includeSubDomains`, voir ADR 0003). Les routes et en-têtes communs sont
   dans `infra/nginx/commun/emplacements.conf.template`, les réglages globaux (limitation du débit) dans
   `infra/nginx/default.conf.template`.
2. **Seuls les chemins du certificat changent** selon l'environnement : `NGINX_DOSSIER_CERTIFICATS`
   (dossier de l'hôte monté sur `/etc/nginx/certificats`, défaut `infra/certificats/`), `NGINX_CERTIFICAT`
   et `NGINX_CLE_CERTIFICAT` (défauts `fullchain.pem` et `privkey.pem`, noms de Let's Encrypt).
   Autosigné et généré pendant le job Lighthouse ; Let's Encrypt en production (phase 9). Certificats et
   clés ne sont **jamais commités** (`infra/certificats/`, `*.pem`, `*.key` dans `.gitignore`).
3. **Développement local en HTTP simple** : `docker-compose.dev.yml` choisit `NGINX_MODE=http` par défaut
   (`infra/nginx/modes/http.conf.template`, mêmes routes, port 80, aucun certificat). `NGINX_MODE=https`
   reproduit la production en local (voir README).
4. **Workflow Lighthouse** : mode HTTPS, `URL_SITE_DOCKER=https://localhost`, Chrome lancé avec
   `--ignore-certificate-errors` (option `--certificat-autosigne` du script, uniquement dans ce job),
   **7 passages par page** (la CI varie d'environ 0,5 s d'un passage à l'autre ; médiane retenue).
5. Le job « Infra » de la CI vérifie `nginx -t` dans les deux modes.

## À traiter en phase 9

- **Let's Encrypt** : certbot (défi HTTP-01 servi depuis `/var/www/acme`), renouvellement automatique et
  rechargement de nginx ; monter le dossier `/etc/letsencrypt` entier (les fichiers de `live/` sont des
  liens vers `archive/`). Si le port HTTPS publié n'est pas 443, la redirection 80 → 443 doit en tenir compte.
- **Nombre de fichiers JS au démarrage** : 25 aujourd'hui (morceaux partagés produits par la compilation
  Angular). Avec HTTP/2, leur nombre coûte peu ; les réduire (regroupement des petits morceaux partagés)
  seulement si les mesures réelles sur mobile le justifient.
- **Mesure sur de vrais visiteurs** : après la mise en ligne, suivre PageSpeed Insights (données de terrain
  CrUX : LCP, INP, CLS au 75e centile des vrais visiteurs, mobile), et pas seulement Lighthouse, qui simule
  un téléphone et un réseau. Les seuils du projet s'appliquent d'abord aux données de terrain.
- En-têtes restants de l'ADR 0003 : CSP complète (d'abord en `Report-Only`), `Cross-Origin-Opener-Policy`,
  puis `preload` de HSTS après vérification des sous-domaines.
