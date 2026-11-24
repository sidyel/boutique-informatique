# Prompts à donner à Claude Code, phase par phase

## Avant de commencer
1. Crée un dossier vide pour le projet et lance `git init`.
2. Place `CLAUDE.md` à la racine et `CAHIER_DES_CHARGES.md` dans `docs/`.
3. Lance `claude` dans ce dossier.
4. Donne les prompts **un par un**. Ne passe au suivant que lorsque la phase compile, que les tests passent et que tu as vérifié le résultat dans le navigateur.

> Conseil : pour les grosses phases, active le **mode plan** (Shift+Tab) pour que Claude Code présente son plan avant d'écrire du code.

---

## Prompt 0 : prise de connaissance et initialisation

```
Lis attentivement CLAUDE.md et docs/CAHIER_DES_CHARGES.md. C'est un site e-commerce de matériel informatique pour le Sénégal (FCFA), en Spring Boot + Angular SSR, avec une forte exigence de SEO et de suivi publicitaire Meta Ads / Google Ads. Tout le code doit être en français, selon les conventions de CLAUDE.md.

Phase 0 uniquement :
1. Initialise le monorepo (/backend, /frontend, /infra, /docs).
2. Backend : projet Spring Boot (Java 21+, Maven) avec les dépendances prévues, configuration par profils (dev, prod), .env.exemple, gestion globale des erreurs au format ProblemDetail, Swagger.
3. Frontend : projet Angular récent avec SSR (@angular/ssr) et hydratation, routes en chargement différé, structure en dossiers (noyau, partage, fonctionnalites, admin).
4. Système de design dans frontend/src/styles : variables CSS (couleurs, typographie, espacements, rayons, ombres), polices hébergées sur le site (Space Grotesk pour les titres, Inter pour le texte, JetBrains Mono pour les caractéristiques), et composants de base (bouton, champ, badge, prix au format "450 000 FCFA", squelette). Propose-moi 2 directions de palette sobres et professionnelles, sans dégradés violets ni style "généré par IA", et attends mon choix.
5. infra/docker-compose.yml : postgres, redis, backend, frontend SSR, nginx.
6. GitHub Actions : compilation et tests du backend et du frontend.

Commence par me présenter ton plan et la structure des dossiers, puis attends ma validation avant de coder.
```

---

## Prompt 1 : modèle de données et authentification

```
Phase 1 (voir la section 2 du cahier des charges).
1. Crée toutes les entités JPA en français avec leurs relations, les enums, l'audit (dateCreation/dateModification) et @Version sur VarianteProduit.
2. Écris les migrations Flyway correspondantes (tables en snake_case français), avec les index utiles (slug, sku, statut de commande, recherche plein texte avec pg_trgm).
3. Crée le jeu de données de démonstration réaliste (section 11) avec un profil "demo".
4. Authentification : inscription, connexion, JWT d'accès + jeton de rafraîchissement en cookie HttpOnly avec rotation, vérification de l'e-mail, mot de passe oublié, rôles (CLIENT, SERVICE_CLIENT, GESTIONNAIRE_STOCK, ADMIN, SUPER_ADMIN), limitation du débit sur les routes sensibles.
5. Tests d'intégration avec Testcontainers.

Présente d'abord le diagramme des entités (en Mermaid) et la liste des fichiers que tu vas créer.
```

---

## Prompt 2 : API du catalogue et médias

```
Phase 2.
1. API publique du catalogue (section 10) : catégories en arbre, produits avec filtres dynamiques issus de DefinitionCaracteristique (filtrable = true), tri, pagination, fiche produit complète (variantes, médias triés, caractéristiques groupées, note), produits similaires, marques.
2. Recherche : plein texte PostgreSQL + pg_trgm (tolérance aux fautes), route de suggestions rapide. Prévois une interface MoteurRecherche pour pouvoir passer à Elasticsearch plus tard.
3. Médias avec Cloudinary (section 5) : MediaProduit (IMAGE, VIDEO_FICHIER, VIDEO_YOUTUBE), envoi multiple, réorganisation, photo principale, texte alternatif, suppression en base et sur Cloudinary, signature pour l'envoi direct depuis le navigateur, vérification du type réel avec Apache Tika, limites de taille. ParametresSite (logo, favicon…) et Banniere (images ordinateur et mobile, programmation par dates).
4. HistoriqueSlug : quand un slug change, enregistre l'ancien. Crée la route /api/redirections?chemin=.
5. Cache Redis sur les lectures du catalogue, avec invalidation à chaque modification.
6. Tests.
```

---

## Prompt 3 : frontend public et SEO

```
Phase 3 : la vitrine. C'est la phase la plus importante pour le design et le SEO.
1. Mise en page : en-tête (logo venant de ParametresSite, méga-menu, recherche avec suggestions, compte, panier), pied de page riche, fil d'Ariane, bouton WhatsApp flottant. Mobile d'abord.
2. Accueil : carrousel de bannières (image mobile sur téléphone), tuiles de catégories, nouveautés, meilleures ventes, promotions, marques, bandeau de confiance.
3. Page catégorie : filtres dynamiques (panneau latéral sur ordinateur, tiroir sur mobile), filtres reflétés dans l'URL, tri, pagination, texte SEO en bas de page.
4. Fiche produit : galerie (miniatures, vidéo avec icône lecture, zoom, balayage, plein écran, façade YouTube), choix de variante (prix, stock, photos, ?variante=), tableau des caractéristiques en JetBrains Mono, avis, similaires, barre d'achat collante sur mobile, bouton "Commander sur WhatsApp".
5. SEO (section 6 du cahier des charges, à respecter entièrement) :
   - SeoService central : title, meta description, canonique, robots, Open Graph, Twitter Card ;
   - JSON-LD rendu côté serveur : Product/ProductGroup avec offers en XOF, BreadcrumbList, Organization, WebSite + SearchAction, ItemList, Article ;
   - vrais codes 404 et redirections 301 depuis le serveur SSR (server.ts + /api/redirections) ;
   - sitemap.xml dynamique (backend, servi via le SSR/nginx) et robots.txt ;
   - noindex,follow + canonique sur les combinaisons de filtres ;
   - balises de vérification Google et Meta issues de ParametresSite.
6. Performance : NgOptimizedImage avec transformations Cloudinary et srcset, image LCP en priority, squelettes, aucun décalage de mise en page. Objectifs : LCP < 2,5 s, CLS < 0,1 sur mobile.

Avant de coder les pages, montre-moi la page d'accueil et la fiche produit avec les données de démonstration, et vérifie que le HTML rendu par le serveur contient bien les titres, les meta et le JSON-LD (montre-moi un curl de la page).
```

---

## Prompt 4 : panier, commande et paiements

```
Phase 4.
1. Panier : invité (jeton en cookie) et connecté, fusion à la connexion, contrôle du stock, panier latéral et page panier, codes promo, estimation de la livraison par ZoneLivraison.
2. Tunnel de commande sur une page avec étapes repliables, commande en invité possible : coordonnées, livraison, paiement, confirmation. Adresse avec quartier et point de repère.
3. Paiements via une interface FournisseurPaiement, avec plusieurs implémentations :
   - un agrégateur mobile money (Wave, Orange Money, Free Money) : commence par PayDunya, avec un mode bac à sable ;
   - carte bancaire ;
   - paiement à la livraison (statut CONFIRMEE).
   Le statut PAYEE n'est fixé QUE par le webhook vérifié (signature, idempotence, montant revérifié). Les prix et totaux sont toujours recalculés côté serveur.
4. À la création de la commande, enregistre les données d'attribution (UTM, gclid, fbclid, fbp, fbc, page d'arrivée, référent, IP, user agent). Ce point est préparé pour la phase 7.
5. Réservation et décrément du stock (MouvementStock), numéro de commande lisible, historique des statuts.
6. E-mails Thymeleaf soignés (confirmation, changement de statut) et facture PDF.
7. Tests des cas limites : stock insuffisant, code promo expiré, webhook en double, montant modifié.
```

---

## Prompt 5 : espace client

```
Phase 5 : espace client complet (section 3, « Compte client ») : tableau de bord, commandes avec suivi et historique des statuts, factures, adresses, liste de souhaits, alertes stock et prix, avis, demandes SAV avec photos et messages, préférences de consentement marketing, suppression du compte. Routes protégées par un guard, rendues côté client (pas d'indexation : noindex).
```

---

## Prompt 6 : back-office

```
Phase 6 : back-office /admin (section 4), protégé par rôles, avec un design sobre et dense (tableaux, filtres, actions groupées).
- Tableau de bord avec graphiques : chiffre d'affaires, commandes, panier moyen, meilleures ventes, stocks faibles, ventes par source (utm_source / Google Ads / Meta).
- Produits : formulaire complet (variantes, caractéristiques selon la catégorie, onglet SEO avec aperçu du résultat Google et compteur de caractères), médias par glisser-déposer avec progression et réorganisation (CDK DragDrop), duplication, import et export CSV.
- Catégories, marques, définitions de caractéristiques, stock, commandes (changement de statut avec e-mail automatique), clients, avis, SAV, codes promo, bannières, collections, articles, pages, zones de livraison.
- Paramètres du site : logo, favicon, coordonnées, réseaux sociaux, identifiants GTM, GA4, Google Ads et pixel Meta, codes de vérification.
- Journal des actions d'administration.
```

---

## Prompt 7 : suivi publicitaire Meta Ads et Google Ads

```
Phase 7 : suivi publicitaire (section 7 du cahier des charges, à suivre à la lettre).
1. Bandeau de consentement (nécessaires, mesure d'audience, publicité) + Google Consent Mode v2 (tout refusé par défaut, puis mise à jour) + consentement du pixel Meta. Choix modifiable à tout moment.
2. Chargement de Google Tag Manager depuis l'identifiant de ParametresSite, uniquement dans le navigateur, sans bloquer le rendu.
3. SuiviService unique côté Angular : tous les événements du tableau 7.2 au format e-commerce GA4 dans le dataLayer, et les événements correspondants du pixel Meta avec eventID. Un page_view à chaque navigation. Les item_id et content_ids sont les SKU des variantes.
4. Événement purchase/Purchase déclenché une seule fois par commande (vérification côté API), avec transaction_id = numéro de commande et event_id = "achat-{numero}". Ajoute user_data (email et téléphone) pour les conversions améliorées de Google Ads.
5. Attribution : capture des UTM, gclid et fbclid à l'arrivée, cookie premier-partie de 90 jours (premier et dernier clic), construction de _fbc, transmission lors de la création de la commande.
6. Backend :
   - ConversionMetaService (API Conversions) : envoi de Purchase avec le même event_id, user_data normalisé puis haché en SHA-256, fbp, fbc, IP, user agent, en asynchrone avec plusieurs tentatives, mode test (test_event_code), historique dans JournalConversion, respect du consentement ;
   - pour le paiement à la livraison : la conversion Purchase côté serveur part au passage du statut à LIVREE.
7. Flux produits : /flux/google-merchant.xml et /flux/meta-catalogue.xml, une ligne par variante (champs de la section 7.5), régénérés toutes les heures et mis en cache.
8. Documente dans docs/SUIVI.md : la configuration à faire dans GTM (balises, déclencheurs, variables à importer), dans Google Ads, dans GA4, dans le Gestionnaire d'événements Meta et dans Merchant Center, ainsi que la procédure de test (Tag Assistant, Meta Pixel Helper, outil de test des événements de Meta).
```

---

## Prompt 8 : fonctionnalités d'engagement

```
Phase 8 : avis avec photos, modération et "achat vérifié" (mise à jour de noteMoyenne et nombreAvis) ; liste de souhaits ; alertes de retour en stock et de baisse de prix (tâche planifiée + e-mail) ; comparateur de 2 à 4 produits avec les différences mises en évidence ; blog et guides d'achat reliés aux produits (JSON-LD Article, présents dans le sitemap) ; pages promo /promo/{slug} pour les campagnes publicitaires (rapides, avec compte à rebours, appel à l'action et WhatsApp, indexation configurable) ; produits récemment consultés.
```

---

## Prompt 9 : finitions et mise en production

```
Phase 9.
1. Configurateur de PC (section 3) : étapes, vérification de compatibilité à partir des caractéristiques (socket, type de mémoire, format, puissance de l'alimentation), ajout de la configuration entière au panier.
2. Import des conversions hors ligne dans Google Ads via le gclid pour les commandes livrées (API Google Ads), désactivable.
3. Audit complet : Lighthouse sur mobile (accueil, catégorie, produit) avec un objectif ≥ 90, correction des problèmes ; audit d'accessibilité ; validation des données structurées.
4. Tests de bout en bout avec Playwright : parcours d'achat complet en invité et connecté.
5. Production : images Docker optimisées, nginx (HTTPS, compression brotli/gzip, cache des fichiers statiques, en-têtes de sécurité, CSP compatible avec GTM et le pixel), sauvegardes PostgreSQL, journaux, guide de déploiement docs/DEPLOIEMENT.md.
```

---

## Prompts utiles à tout moment

```
Revois le code de la dernière phase : respect des conventions en français de CLAUDE.md, failles de sécurité, problèmes N+1 en JPA, tests manquants. Corrige ce qui doit l'être.
```

```
Regarde la page [nom de la page] : est-ce qu'elle a l'air "générée par une IA" ? Améliore-la selon les règles de design de CLAUDE.md, en prenant pour référence la densité et le sérieux des grands sites spécialisés en informatique.
```

```
Vérifie le SEO de [page] : affiche le HTML rendu par le serveur avec curl et contrôle le titre, la meta description, la canonique, l'Open Graph, le JSON-LD et le code HTTP.
```
