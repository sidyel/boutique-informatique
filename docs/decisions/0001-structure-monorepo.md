# 0001 — Structure du monorepo et choix de la phase 0

- **Date** : 2026-11-24
- **Statut** : acceptée

## Contexte

Le projet comporte une API Java, une application Angular rendue côté serveur et une infrastructure Docker. Il est développé par une petite équipe et doit rester simple à lancer.

## Décisions

1. **Monorepo** (`backend/`, `frontend/`, `infra/`, `docs/`) : une seule CI, des modifications transverses (contrat d'API) dans un seul commit.
2. **Backend** : Spring Boot 4.2, Java 21, Maven avec wrapper. Package racine `sn.boutique`, organisé **par domaine** (`catalogue`, `commande`, `compte`…) avec un module transverse `noyau` (configuration, erreurs).
3. **JWT** : `spring-boot-starter-oauth2-resource-server` (Nimbus) plutôt qu'une bibliothèque tierce : encodage et décodage intégrés à Spring Security.
4. **Erreurs** : toutes les erreurs de l'API suivent la RFC 9457 (`ProblemDetail`), avec un champ `erreurs` pour la validation.
5. **Schéma** : uniquement via Flyway, `ddl-auto=validate`.
6. **Frontend** : Angular 22 (composants autonomes, signals, sans zone.js), SSR + hydratation, routes en chargement différé. Dossiers `noyau` (services uniques), `partage` (composants réutilisables), `fonctionnalites` (pages publiques), `admin` (rendu côté client uniquement).
7. **Design** : palette « Graphite & Orange signal », jetons en variables CSS, polices auto-hébergées (Space Grotesk, Inter, JetBrains Mono — licence OFL).
8. **Infra** : nginx en frontal ; `/api`, `/sitemap.xml`, `/robots.txt`, `/flux/` vers le backend, le reste vers le serveur SSR.
