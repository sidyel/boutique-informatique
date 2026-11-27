# CLAUDE.md — Boutique en ligne de matériel informatique

> Ce fichier est lu automatiquement par Claude Code à chaque session.
> Le cahier des charges complet se trouve dans `docs/CAHIER_DES_CHARGES.md` : le lire avant toute nouvelle fonctionnalité.

## Le projet
Site e-commerce complet de vente de matériel informatique (PC portables, PC de bureau, composants, écrans, périphériques, réseau, accessoires).
- Marché principal : **Sénégal**, devise **FCFA (code ISO `XOF`, sans décimales)**, langue **français**.
- Priorités : site **professionnel et crédible**, **très bien référencé sur Google (SEO)**, **prêt pour les campagnes Meta Ads et Google Ads** (suivi des conversions fiable).
- Le design ne doit **pas** ressembler à un site généré par une IA (voir section Design).

## Stack technique
- **Backend** : Java 21+ (LTS), Spring Boot (dernière version stable), Spring Security (JWT d'accès + jeton de rafraîchissement), Spring Data JPA, PostgreSQL, Flyway, Redis, Cloudinary (images et vidéos), Spring Mail + Thymeleaf (e-mails), springdoc-openapi, MapStruct, Lombok, Apache Tika (vérification des fichiers).
- **Frontend** : Angular (dernière version stable), composants autonomes, signals, **SSR obligatoire (`@angular/ssr`) avec hydratation**, chargement différé des routes, SCSS avec variables de design (design tokens), `NgOptimizedImage`.
- **Infra** : Docker, docker-compose (postgres, redis, backend, frontend SSR, nginx), GitHub Actions.

## Structure du dépôt
```
/backend      → API Spring Boot
/frontend     → application Angular (SSR)
/docs         → cahier des charges, décisions d'architecture
/infra        → docker-compose, nginx, scripts
```

## Langue du code : TOUT EN FRANÇAIS
- **Classes, entités, champs, méthodes, variables, DTO, enums, routes API, tables et colonnes SQL, composants et services Angular : en français.**
- Pas d'accents ni de caractères spéciaux dans les identifiants (`Categorie`, `prixBarre`, `dateCreation`).
- Conventions de nommage backend :
  - Entités : `Produit`, `VarianteProduit`, `MediaProduit`, `Categorie`, `Commande`, `LigneCommande`…
  - Contrôleurs : `ProduitControleur`, `CommandeControleur`, `AdminProduitControleur`
  - Services : `ProduitService`, `PanierService`, `PaiementService`
  - Dépôts : `ProduitDepot extends JpaRepository<Produit, Long>`
  - DTO : `ProduitDto`, `CreationProduitRequete`, `ProduitResumeReponse`
  - Convertisseurs MapStruct : `ProduitConvertisseur`
  - Exceptions : `RessourceIntrouvableException`, `StockInsuffisantException`
  - Enums en MAJUSCULES : `StatutCommande.EN_PREPARATION`
- Méthodes : verbes français (`trouverParSlug`, `ajouterAuPanier`, `calculerTotal`, `confirmerPaiement`).
- **Exception imposée par le framework** : les mots-clés de Spring Data restent en anglais car ils sont obligatoires (`findBySlugAndActifTrue`, `existsByEmail`). Si une requête devient longue, utiliser `@Query` avec un nom de méthode français.
- Routes API en français, au pluriel, en kebab-case : `/api/produits`, `/api/admin/codes-promo`, `/api/compte/commandes`.
- Tables SQL en snake_case français : `produit`, `variante_produit`, `ligne_commande`.
- Frontend : fichiers en kebab-case français (`fiche-produit.ts`, `panier.service.ts`), classes en PascalCase français (`FicheProduit`, `PanierService`), en suivant le guide de style Angular actuel.
- Commentaires, messages d'erreur, messages de commit, documentation : en français.

## Règles de travail
1. **Toujours proposer un plan avant de coder** une fonctionnalité de plus de quelques fichiers, et attendre la validation.
2. Avancer par petites étapes qui compilent et passent les tests. Un commit par étape logique (message en français, style « feat: ajout du panier invité »).
3. Écrire les tests en même temps que le code : JUnit 5 + Mockito, Testcontainers (PostgreSQL) pour les dépôts et l'intégration, et des tests de composants Angular pour la logique critique (panier, tunnel de commande).
4. Ne jamais mettre de secret dans le code ni dans Git : utiliser `.env` (ignoré par Git) et fournir un `.env.exemple`.
5. Ne pas ajouter de dépendance sans expliquer pourquoi.
6. Toute modification de schéma passe par une **migration Flyway** (`V{numero}__description.sql`), jamais par `ddl-auto=update`.
7. Les montants sont des **entiers en FCFA** (`Long`), jamais des `double`.
8. Valider toutes les entrées côté serveur (Bean Validation) et renvoyer les erreurs au format `ProblemDetail`.
9. **Heure : un seul bean `Clock` (`horloge`)**, en UTC et tronqué à la microseconde (précision de `timestamptz` dans PostgreSQL). Tout le code l'injecte et appelle `horloge.instant()` : jamais `Instant.now()`, `LocalDate.now()`, `System.currentTimeMillis()`… (l'audit JPA l'utilise aussi). Dans les tests, `HorlogeReglable` le remplace pour avancer le temps. Le test `RegleHorlogeTest` fait échouer la compilation en cas d'écart.
10. **Engagements** : au début de chaque phase, lire `docs/ENGAGEMENTS.md` ; à la fin, cocher ce qui est fait et ajouter les nouveaux engagements.
11. **Tests indépendants de l'ordre** : un test laisse la base dans l'état où il l'a trouvée ; aucun test ne dépend de l'ordre d'exécution. Les classes et méthodes tournent dans un ordre **aléatoire** dont la graine est affichée (`GRAINE_ORDRE_TESTS=…`, `surefire.runOrder.random.seed`) : pour rejouer un échec, relancer avec `-Dsurefire.runOrder.random.seed=N -Djunit.jupiter.execution.order.random.seed=N`. La ligne partagée `parametres_site` est restaurée automatiquement après chaque test (`RestaurationParametresSite`) ; toute autre donnée partagée (compteurs, ventes, stock global) se remet ou se mesure au moment du test, jamais suppose.

## Règles SEO (non négociables)
- Toute page publique doit être **rendue côté serveur** avec un titre, une meta description, une URL canonique, les balises Open Graph et des données structurées JSON-LD adaptés.
- Une page introuvable renvoie un **vrai code HTTP 404** depuis le serveur SSR, et un ancien slug renvoie une **redirection 301**.
- Les URL utilisent des slugs lisibles : `/categorie/pc-portables`, `/produit/asus-tuf-gaming-a15-fa507`.
- Aucun code navigateur (`window`, `localStorage`, scripts de suivi) ne s'exécute pendant le rendu serveur : utiliser `isPlatformBrowser` ou `afterNextRender`.

## Règles de suivi publicitaire (Meta Ads / Google Ads)
- **Un seul point d'entrée** côté Angular : `SuiviService`, qui pousse les événements dans le `dataLayer` (format e-commerce GA4) et appelle le pixel Meta. Aucun composant n'appelle `gtag`/`fbq` directement.
- Chaque événement porte un `event_id` unique, partagé entre le pixel Meta et l'API Conversions (dédoublonnage).
- Les identifiants produits envoyés dans les événements (`item_id`, `content_ids`) sont **exactement les mêmes** que l'`id` des flux produits (= SKU de la variante).
- Le suivi respecte le consentement (Google Consent Mode v2 + consentement du pixel Meta).

## Design : éviter l'aspect « généré par IA »
- Interdit : dégradés violet-bleu, effet verre dépoli généralisé, émojis servant d'icônes, illustrations 3D génériques, slogans creux (« Élevez votre expérience »), cartes très arrondies avec de grandes ombres partout.
- À faire : une couleur d'accent forte et une base neutre, une typographie avec du caractère (titres en *Space Grotesk* ou *Inter Tight*, caractéristiques techniques en *JetBrains Mono*), des polices **hébergées sur le site**, une forte densité d'information comme sur les vrais sites d'informatique, des textes concrets et locaux (« Livraison à Dakar en 24 h »), des icônes cohérentes (Lucide ou Phosphor), des animations discrètes et des squelettes de chargement.
- Toutes les couleurs, tailles et espacements passent par des variables CSS (`--couleur-accent`, `--espace-4`…). Aucune couleur écrite en dur dans les composants.
- **Mobile d'abord** : la majorité du trafic publicitaire arrivera sur téléphone, souvent des Android d'entrée de gamme avec un forfait data limité. Pour toutes les pages (vitrine, panier, commande, compte) :
  - **Largeurs testées** : 360 px (Android courant au Sénégal, le plus contraignant), 390 px, 768 px et 1440 px. Les captures de chaque livraison incluent 360 px. Aucun défilement horizontal parasite, aucun texte coupé.
  - **Tactile** : zones cliquables d'au moins 44 × 44 px, espacées. Aucune fonction accessible uniquement au survol (le méga-menu a son tiroir, le zoom de galerie son équivalent au toucher). Actions principales accessibles au pouce, en bas de l'écran sur mobile.
  - **Formulaires** : champs en 16 px minimum (sinon l'iPhone zoome), bons claviers (`inputmode="tel"`, `type="email"`, `inputmode="search"`…), autocomplétion du navigateur activée (`autocomplete`).
  - **Poids** : premier chargement de l'accueil sur mobile < 1 Mo au total (HTML, JS, CSS, polices, images visibles). Images en largeur adaptée à l'écran (`srcset`), rien de plus lourd que nécessaire. Le poids mesuré figure dans chaque rapport.
  - **Appareils modestes** : JavaScript initial le plus léger possible (taille des bundles vérifiée à chaque livraison), animations sobres, aucun effet coûteux au défilement.
  - **Liens directs** : téléphone en `tel:`, WhatsApp en `https://wa.me/…`, pour qu'un toucher ouvre directement l'application.

## Commandes utiles
```
# Backend
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw test

# Frontend
cd frontend && npm start
cd frontend && npm run build && npm run serve:ssr
cd frontend && npm test

# Tout l'environnement
docker compose -f infra/docker-compose.yml up -d
```
