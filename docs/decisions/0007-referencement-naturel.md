# ADR 0007 — Référencement naturel : canoniques, codes 301/410, sitemap, données structurées

- **Statut** : accepté
- **Date** : 2026-11-26
- **Phase** : 3d

## Contexte

Les pages publiques sont rendues côté serveur (Angular SSR). Il reste à dire à Google quelles adresses
indexer, quoi faire des adresses qui ont changé ou disparu, et quelles informations marchand afficher dans
les résultats (prix, disponibilité, avis, politique de retour). Les campagnes Google Ads et les fiches
gratuites Google Shopping s'appuient sur ces mêmes informations.

## Décisions

### 1. Indexation explicite : `INDEXATION_AUTORISEE`

Une variable d'environnement du backend, `INDEXATION_AUTORISEE` (**false par défaut**, `true` uniquement en
production). On ne se fie pas au nom d'hôte (`localhost`) : une préproduction publique serait indexée.

- `false` : `robots.txt` répond `Disallow: /` et **toutes** les pages reçoivent `<meta name="robots"
  content="noindex,follow">` ;
- `true` : `robots.txt` liste les pages privées et le sitemap ; chaque page décide de son indexation.

Source unique : le backend (`ProprietesBoutique`) expose `site: {url, indexationAutorisee}` dans
`/api/parametres`, lu par le rendu serveur. Le frontend n'a aucune variable de plus. `docker-compose.dev.yml`
force `false`.

### 2. robots.txt

`Disallow` : `/admin`, `/compte`, `/panier`, `/commande`, `/api/`, `/recherche`. Les URL filtrées ou triées
des listes ne sont **pas** bloquées : Google doit pouvoir les visiter pour lire leur `noindex` (une URL
bloquée par robots.txt peut être indexée sans contenu à partir des liens qui y mènent).

### 3. Canoniques et indexation des listes

| Page | robots | canonique |
|---|---|---|
| Catégorie, marque sans filtre ni tri | `index,follow` | elle-même |
| Page 2, 3… sans filtre (`?page=2`) | `index,follow` | elle-même (avec `page`) |
| Filtrée ou triée | `noindex,follow` | elle-même, paramètres normalisés (ordre fixe, tri par défaut retiré) |
| Recherche | `noindex,follow` | `/recherche?q=…` |
| Fiche produit | `index,follow` | `/produit/{slug}` (sans `?variante=`) |

On n'associe jamais `noindex` à une canonique qui pointe ailleurs : Google déconseille ces signaux
contradictoires. Toutes les canoniques et `og:url` sont absolues (`URL_SITE`).

### 4. Adresses qui changent ou disparaissent

Réponse calculée par `/api/redirections?chemin=…`, demandée par le rendu serveur quand l'API répond 404 :

| Situation | Réponse |
|---|---|
| Ancien slug (produit, catégorie, marque) | **301** vers l'adresse actuelle (l'historique pointe vers l'entité : jamais de chaîne) |
| Produit masqué temporairement (`actif = false`, sans `date_retrait`) | **404** : Google réessaiera et réindexera au retour |
| Produit retiré (`date_retrait`) avec successeur publié | **301** vers le premier successeur publié de la chaîne (A → B retiré → C : A va directement vers C) |
| Produit retiré sans successeur | **410** et page « Ce produit n'est plus vendu » : produits du même rayon, lien vers le rayon |
| Catégorie désactivée | **301** vers la catégorie parente active la plus proche, sinon **410** |

En SSR, la 301 est faite par une navigation du routeur avec le statut 301 dans `RESPONSE_INIT` : Angular SSR
répond « 301 + Location » sans corps, en gardant les paramètres de la requête. Dans le navigateur, la même
navigation remplace l'entrée de l'historique. Une boucle de remplaçants saisie par erreur est coupée après
10 étapes (410). La base garantit qu'un produit retiré n'est pas publié (`ck_produit_retrait_inactif`).

### 5. Sitemap

Index `/sitemap.xml` et fichiers `sitemap-pages.xml`, `sitemap-categories.xml`, `sitemap-marques.xml`,
`sitemap-produits-N.xml` (50 000 URL par fichier). Seulement des URL canoniques publiées : les produits
masqués et retirés en sont exclus, les marques sans produit publié aussi. `lastmod` tient compte des
variantes (un changement de prix change la page), photo principale en `image:image`. Mis en cache dans Redis
et vidé avec le catalogue. Le sitemap reste servi quand l'indexation est interdite (inoffensif : robots.txt
ne l'annonce pas).

### 6. Données structurées (JSON-LD)

Un seul `<script type="application/ld+json" id="donnees-structurees">` par page, sous forme de `@graph` :

- toutes les pages : `Organization` (nom, logo, téléphone, e-mail, adresse, profils sociaux, politique de
  retour) et, **seulement si une boutique physique est renseignée**, `Store` (adresse, coordonnées,
  horaires) ;
- accueil : `WebSite` ;
- catégorie et marque : `BreadcrumbList` et `ItemList` des produits affichés ;
- fiche : `Product` (une seule variante) ou `ProductGroup` + `hasVariant` (une offre par variante : prix en
  XOF, disponibilité, SKU, URL `?variante=`, état neuf/reconditionné/occasion, prix barré en
  `StrikethroughPrice`), `aggregateRating` et `review` **seulement si des avis sont affichés**,
  `BreadcrumbList`.

La politique de retour (`MerchantReturnPolicy`, `applicableCountry: SN`) est réglée dans `ParametresSite`
(acceptés ou non, délai, frais, retour en boutique et/ou par livreur).

## Limites de Google acceptées

- **`shippingDetails` absent.** Google ne prend `addressRegion` que pour les États-Unis, l'Australie et le
  Japon : on ne peut pas annoncer un tarif par région du Sénégal. Un tarif national unique serait faux
  (le plus haut, 10 000 F en démo, pour un client de Dakar qui paie 2 000 F ; le plus bas, pour les régions).
  Le tarif à annoncer est une décision commerciale reportée à la **phase 7** (Merchant Center : tarif
  national, livraison offerte au-delà d'un montant…). L'avertissement « shippingDetails manquant » du test
  des résultats enrichis est accepté. Les zones et tarifs réels restent affichés sur la fiche.
- **Retour par livreur** : Google n'a pas de valeur « enlèvement à domicile » ; il est déclaré
  `ReturnByMail`, la plus proche.
- **`variesBy`** : Google n'accepte que couleur, taille, matière, motif, âge, genre. La mémoire, le
  stockage ou le processeur ne peuvent pas y figurer ; `variesBy` ne contient la couleur que si les variantes
  en diffèrent. Les variantes restent distinctes par leur nom, leur SKU et leur URL.
- **Champ de recherche des sitelinks** (`SearchAction`) : retiré par Google, non déclaré.

## Conséquences

- Mettre `INDEXATION_AUTORISEE=true` en production est une étape du lancement (README, phase 9).
- Lighthouse en CI signale « la page est bloquée de l'indexation » : c'est attendu (`false` en CI).
- Retirer un produit (`Produit.retirer`) au lieu de le supprimer conserve ses liens entrants (301 ou 410).
