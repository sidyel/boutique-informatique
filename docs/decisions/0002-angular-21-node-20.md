# 0002 — Angular 21 (LTS) avec Node 20.19 en développement

- **Date** : 2026-11-24
- **Statut** : acceptée
- **Remplace** : le point 6 de la décision 0001 (« Angular 22 »)

## Contexte

La dernière version stable d'Angular (22.x) exige Node `^22.22.3 || ^24.15.0 || >=26`.
Les postes de développement utilisent **Node 20.19**, qu'on ne souhaite pas changer
(autres projets sur la même machine).

**Angular 21** est en support à long terme (LTS) et accepte Node `^20.19.0 || ^22.12.0 || >=24.0.0`.

## Décision

- Le frontend utilise **Angular 21** (21.2.x au moment de la création) : composants autonomes,
  signals, sans zone.js, SSR avec `@angular/ssr` et hydratation, tests avec Vitest.
- `frontend/package.json` déclare `"engines": { "node": "^20.19.0 || ^22.12.0 || ^24.0.0" }`.
- `frontend/.nvmrc` contient `20.19` (version des postes de développement).
- **L'image Docker du frontend et la CI utilisent Node 24 LTS**, également supporté par Angular 21.
  Cela prépare le passage à Angular 22 sans rien changer côté infrastructure.

## Conséquences

- Aucune fonctionnalité exclusive à Angular 22 n'est utilisée.
- Fin du support LTS d'Angular 21 : **juin 2027**
  (source : https://angular.dev/reference/releases). La migration doit être faite avant cette date.
- Deux versions de Node coexistent (20 en local, 24 en CI et en production) : la CI détecte
  tout écart de comportement.

## Marche à suivre pour passer à Angular 22

1. Installer **Node 24 LTS** (ou ≥ 22.22.3) sur les postes, puis mettre `.nvmrc` à `24`.
2. Partir d'une branche propre, avec les tests au vert :
   ```bash
   cd frontend
   git switch -c chore/angular-22
   npx @angular/cli@22 update @angular/core@22 @angular/cli@22
   ```
   `ng update` met à jour les dépendances et applique automatiquement les migrations de code
   (schematics). Lire attentivement sa sortie.
3. Mettre à jour les autres paquets Angular éventuels (`@angular/cdk`…) avec `npx ng update`
   (la commande sans argument liste ce qui reste à faire).
4. Mettre à jour `engines` dans `package.json` selon les versions de Node exigées par Angular 22.
5. Consulter le guide officiel : https://angular.dev/update-guide (de 21 vers 22).
6. Vérifier :
   ```bash
   npm test -- --watch=false
   npm run build && npm run serve:ssr
   curl -i http://localhost:4000/page-qui-n-existe-pas   # doit répondre 404
   ```
7. Ouvrir une pull request ; la CI (Node 24) doit passer avant la fusion.
8. Mettre à jour cette décision (statut « remplacée ») et la décision 0001.
