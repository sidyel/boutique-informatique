# Engagements pour les phases suivantes

> **Règle (CLAUDE.md)** : au début de chaque phase, lire ce fichier ; à la fin, cocher ce qui est fait (avec
> le commit) et ajouter les nouveaux engagements, avec leur source.

Sources : **ADR** = `docs/decisions/` ; **bilan N** = bilan de fin de phase validé ; **décision** = choix
exprimé lors d'une validation ; **README** = section « À traiter avant la mise en production ».

Mis à jour à la fin de la phase 3 (2026-11-26).

## Règles permanentes (toutes les phases)

- [ ] Un service qui modifie des variantes puis renvoie le produit le **relit** (`saveAndFlush` puis
  `entityManager.refresh`) : `prixMinimum` et `stockTotal` viennent d'un trigger. Un test de service le
  vérifie dans chaque service concerné. — *ADR 0004 §2, bilan 1*
- [ ] Un DTO mis en cache qui change de forme → incrémenter `CachesPublics.VERSION_FORMAT` et ajouter
  l'empreinte dans `FormatCachesTest`. — *bilan 3, décision (validation du bilan 3)*
- [ ] Aucune réponse personnalisée (compte, admin, panier, commande) n'est marquée `public` ni mise en cache :
  `no-store`. — *décision (validation 3a)*
- [ ] Toute image diffusée (pages, JSON-LD, Open Graph, flux) passe par une URL Cloudinary transformée, jamais
  l'original. — *ADR 0005 §4*

## Phase 4 — panier et commande

- [ ] Factures `FAC-2026-000001` : numérotation chronologique continue **sans trou**, attribuée au paiement
  confirmé, via une table `compteur_facture` verrouillée (pas une séquence) ; tests de concurrence et
  d'annulation. Les numéros de commande peuvent avoir des trous. — *ADR 0004 §4, bilan 1*
- [ ] Décrément du stock à la commande : relire le produit si on le renvoie (règle permanente ci-dessus).
  — *ADR 0004 §2*
- [ ] Retirer le message « La commande en ligne ouvre très bientôt… » (`BlocAchat.ajouterAuPanier`) dès que le
  panier fonctionne. — *décision (validation 3c), README*
- [ ] Panier et commande en `no-store` (règle permanente ci-dessus). — *décision (validation 3a)*

## Phase 5 — compte client

- [ ] Un seul rafraîchissement de session à la fois entre onglets (Web Locks / BroadcastChannel) : deux
  rafraîchissements simultanés déconnectent l'utilisateur. — *ADR 0003 §4, bilan 1*
- [ ] Jeton d'accès gardé en mémoire uniquement ; rafraîchir après la vérification de l'e-mail (le JWT garde
  `emailVerifie=false` jusque-là). — *ADR 0003, bilan 1*
- [ ] Pages `compte/verifier-email` et `compte/nouveau-mot-de-passe` : renvoient le jeton en POST et servent
  `Referrer-Policy: no-referrer`. — *ADR 0003 §12, bilan 1*
- [ ] Le SSR ne relaie pas `X-Forwarded-For`. — *bilan 1*
- [ ] Suppression de compte = anonymisation partielle des commandes (garder la facture ; effacer IP, user agent,
  `fbp`, `fbc`, `gclid`, `fbclid`, `utm…`). Durée de conservation (10 ans OHADA ?) à confirmer avec le
  comptable. — *ADR 0004 §3, bilan 1*
- [ ] Avis d'un compte supprimé signés « Client vérifié » si `achatVerifie`, « Client » sinon. — *ADR 0004 §3*

## Phase 6 — back-office

- [ ] Double authentification TOTP **obligatoire** pour SERVICE_CLIENT, GESTIONNAIRE_STOCK, ADMIN et
  SUPER_ADMIN : connexion en deux temps, enrôlement imposé, secret chiffré, codes de secours, limitation et
  anti-rejeu, réinitialisation par SUPER_ADMIN seulement. — *ADR 0003 « À traiter », section Phase 6*
- [ ] Supprimer un produit déjà commandé = **désactiver** (`200 {"action":"DESACTIVE"}`, sinon `204`) ; même
  règle pour une variante (`existsByVarianteId`, attention à `orphanRemoval`) ; test des deux chemins.
  — *ADR 0004 §1, bilan 1*
- [ ] « Retirer un produit » (`Produit.retirer`, choix du remplaçant) distinct de « masquer » (`actif=false`) :
  retiré = 301 vers le successeur ou 410, masqué = 404. — *ADR 0007 §4, bilan 3*
- [ ] Saisie de la politique de retour et de l'adresse de la boutique physique (`ParametresSite`, V15).
  — *ADR 0007 §6, bilan 3*
- [ ] Formulaires : texte alternatif prérempli par la suggestion, avertissements de format des bannières,
  rattachement des images aux bannières, suppression d'une variante. — *ADR 0005, bilan 2*
- [ ] Formulaires des promesses commerciales (`messageBarreSuperieure`, `elementsConfiance`). — *ADR 0004 §6*
- [ ] Renommer `CatalogueModifie` (il vide aussi le cache des paramètres). — *bilan 2*

## Phase 7 — flux produits, Merchant Center, Meta

- [ ] Tarif de livraison à annoncer à Google (`shippingDetails` / Merchant Center) : décision commerciale
  (tarif national, livraison offerte au-delà d'un montant…). Google n'accepte `addressRegion` que pour
  US/AU/JP. — *ADR 0007 « Limites », décision (3d)*
- [ ] Flux sans produits retirés ni masqués ; `id` = SKU de la variante, identique aux `item_id` /
  `content_ids` des événements de suivi. — *ADR 0004 §1, ADR 0007, CLAUDE.md*
- [ ] Images des flux en URL transformée (règle permanente). — *ADR 0005 §4*

## Phase 8 — avis

- [ ] Avis réservés aux clients ayant une commande LIVRÉE contenant le produit (`achatVerifie = true`) si
  `ParametresSite.avisReservesAuxAcheteurs` (vrai par défaut) ; sinon ouverts, `achatVerifie` calculé.
  — *ADR 0004 §5, bilan 1*
- [ ] Publier un avis exige un e-mail vérifié. — *ADR 0003 §7*

## Phase 9 — mise en production

- [ ] `INDEXATION_AUTORISEE=true` en production **uniquement** ; vérifier `/robots.txt`, soumettre
  `/sitemap.xml` dans la Search Console. — *ADR 0007 §1, décision (3d), README*
- [ ] Exclure `public/demo` de l'image Docker de production ; aucune donnée en base ne pointe vers
  `/demo/images/`. — *bilans 1 et 2, README*
- [ ] Ne jamais exposer le port du backend. — *bilan 1*
- [ ] Let's Encrypt (certbot, renouvellement, rechargement de nginx). — *ADR 0006*
- [ ] Mesures sur de vrais visiteurs (PageSpeed Insights, CrUX) ; ne réduire le nombre de fichiers JS du
  démarrage que si elles le justifient. — *ADR 0006*
- [ ] En-têtes : HSTS (puis `preload`), CSP complète (d'abord `Report-Only`), `Cross-Origin-Opener-Policy`.
  — *ADR 0003 « À traiter », ADR 0006*
- [ ] IPv6 : clé de limitation par préfixe /64 (backend et nginx), tests avec deux adresses du même /64.
  — *ADR 0003 « À traiter »*
- [ ] Brancher sur la supervision `ALERTE_REDIS_PLEIN`, `ALERTE_REDIS_INDISPONIBLE`,
  `ALERTE_CONNEXION_CIBLEE`. — *ADR 0003, README*
- [ ] Tâche de nettoyage Cloudinary : `media_orphelin`, signatures vidéo expirées jamais utilisées, images de
  bannières jamais rattachées. — *ADR 0005, bilan 2*
- [ ] Revoir les valeurs de la zone nginx `pages` (`5r/s`, rafale `40`). — *README (limitation du débit)*

## Sans phase fixée

- [ ] Version texte des e-mails (HTML seulement aujourd'hui). — *bilan 1*
- [ ] Envoi vidéo découpé de plus de 50 minutes : Cloudinary peut refuser les derniers morceaux (signature
  valable 1 h au plus). À surveiller. — *bilan 2*
- [ ] Corriger ce que signalera le test des résultats enrichis de Google (risques : noms d'auteur des avis,
  variantes de même couleur). — *bilan 3*

## Fait

- [x] Phase 3 : produit désactivé → 404 (masqué), 301 vers le successeur ou 410 (retiré) ; catégorie
  désactivée → 301 vers la parente ou 410. — *ADR 0004 « À trancher », ADR 0007* (`85c8bac`, `18b1b46`)
- [x] Phase 3 : chargeur `NgOptimizedImage` qui complète `f_auto,q_auto` avec la largeur. — *ADR 0005, bilan 2*
  (`9d1a99f`)
- [x] Phase 3 : lecteur YouTube `youtube-nocookie.com`, chargé au clic. — *ADR 0005, bilan 2* (`cf166cb`)
- [x] Phase 3 : favicons et image Open Graph dans le `<head>` rendu côté serveur. — *bilan 2* (`9d1a99f`,
  `5e3ebd3`)
- [x] Phase 1 : purge des jetons expirés, `resolver` de nginx, `docker-compose.dev.yml`. — *bilan 1*
  (`571e066`)
- [x] Phase 2 : relecture du produit après modification des variantes dans le back-office. — *ADR 0004 §2*
  (`6d6899b`)
