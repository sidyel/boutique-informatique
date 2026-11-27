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
- [x] Panier et commande en `no-store` (règle permanente ci-dessus). — *décision (validation 3a)* (4a, 4b)
- [ ] Modèle de facture et d'avoir (mentions légales, HT / TVA / TTC ou « TVA non applicable ») **validé par le
  comptable** : modèle Thymeleaf isolé, facile à modifier. — *décision (validation du plan de la phase 4)*
- [ ] 4c — Reprise du paiement branchée sur PayDunya (la route et le modèle existent depuis 4b, avec un faux
  prestataire en test) ; carte bancaire proposée dans le tunnel seulement si le prestataire l'indique activée sur
  le compte ; avant d'annuler une commande échue, rappeler `confirm/{token}` (un paiement tardif ne se perd pas).
  — *décision (validation du plan de la phase 4, point 3), ADR 0008 §7*
- [ ] 4c — Paiement arrivé après l'expiration sans stock : alerte admin **et** information du client (e-mail s'il
  existe, message sur la page de suivi : « remboursement en cours »). Aujourd'hui, `ReceptionPaiementService`
  marque tout paiement reçu pour une commande annulée ou expirée « à rembourser » avec alerte : la 4c y ajoute la
  réactivation de la commande quand le stock le permet, et l'information du client. — *décision (validation du
  plan, point 15), ADR 0008 §7*
- [ ] 4c — Le webhook PayDunya appelle `ReceptionPaiementService.enregistrerPaiementRecu` **après** sa vérification
  serveur à serveur (seul chemin vers `PAYEE`). Un paiement reçu sur une facture déjà `ANNULE` (remplacée) doit
  être enregistré comme les autres : l'argent est là. — *corrections de la 4b, ADR 0008 §7*
- [ ] 4c — Client HTTP de PayDunya avec des **délais courts** (connexion et lecture) : le verrou de la commande est
  gardé pendant l'appel. Régler `boutique.commande.validite-facture` sur la validité réelle d'une facture PayDunya,
  et au moins sur `delai_paiement_mobile_minutes` (sinon deux factures payables peuvent coexister). — *corrections
  de la 4b, ADR 0008 §7*
- [ ] 4c — Adresses de retour et d'annulation données à PayDunya : **ne pas y mettre le jeton de suivi** (il est
  renouvelé quand le service client renvoie le lien ou qu'une requête est rejouée : l'adresse mènerait à
  « lien invalide »). Prévoir un jeton de retour dédié qui ne donne accès qu'au statut de la commande. — *corrections
  de la 4b, ADR 0008 §8*
- [ ] 4d — Version texte des e-mails de commande (`commande-recue.html` n'a que le HTML) : même règle que
  l'engagement « Sans phase fixée ». — *bilan 1*
- [ ] Statut `PAYEE` fixé **uniquement** par la vérification serveur à serveur du prestataire (jamais par la page
  de retour du navigateur ni par le seul corps d'une notification). — *décision (plan de la phase 4)*

## Phase 5 — compte client

- [ ] Un seul rafraîchissement de session à la fois entre onglets (Web Locks / BroadcastChannel) : deux
  rafraîchissements simultanés déconnectent l'utilisateur. — *ADR 0003 §4, bilan 1*
- [ ] Jeton d'accès gardé en mémoire uniquement ; rafraîchir après la vérification de l'e-mail (le JWT garde
  `emailVerifie=false` jusque-là). — *ADR 0003, bilan 1*
- [ ] Pages `compte/verifier-email` et `compte/nouveau-mot-de-passe` : renvoient le jeton en POST et servent
  `Referrer-Policy: no-referrer`. — *ADR 0003 §12, bilan 1*
- [ ] Le SSR ne relaie pas `X-Forwarded-For`. — *bilan 1*
- [ ] Suppression de compte = anonymisation partielle des commandes (garder la facture ; effacer IP, user agent,
  `fbp`, `fbc`, `gclid`, `fbclid`, `utm…`, `premiere_*`, `jeton_suivi`, `nom_client`, e-mail et téléphone selon
  la durée de conservation). Durée de conservation (10 ans OHADA ?) à confirmer avec le comptable.
  — *ADR 0004 §3, bilan 1, ADR 0008 §8*
- [ ] Rattacher à son compte les commandes passées en invité avec le même e-mail ou téléphone vérifié (le lien de
  suivi n'existe que pour l'invité). — *ADR 0008*
- [ ] Avis d'un compte supprimé signés « Client vérifié » si `achatVerifie`, « Client » sinon. — *ADR 0004 §3*

## Phase 6 — back-office

- [ ] Écrans du service client et du service financier pour les routes existantes : « Renvoyer le lien de suivi »
  (`POST /api/admin/commandes/{numero}/renvoyer-lien-suivi`), liste des paiements « à rembourser »
  (`GET /api/admin/paiements`) avec action « marquer remboursé » (statut `REMBOURSE`, à créer). — *corrections de la 4b*
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
- [ ] Back-office des commandes : liste et détail (`/api/admin/commandes` ne propose aujourd'hui que la file
  « à confirmer » et les routes confirmer/annuler), historique, filtres. — *ADR 0008 §5, bilan 4b*
- [ ] Saisie des réglages de commande dans `ParametresSite` : paiement à la livraison (activé, plafond,
  commandes non confirmées par numéro, délai d'annulation), délai du paiement mobile, quantité maximale par ligne
  (et `quantite_max` par variante). — *décisions 5 et 6 du plan de la phase 4, ADR 0008*

## Phase 7 — flux produits, Merchant Center, Meta

- [ ] Tarif de livraison à annoncer à Google (`shippingDetails` / Merchant Center) : décision commerciale
  (tarif national, livraison offerte au-delà d'un montant…). Google n'accepte `addressRegion` que pour
  US/AU/JP. — *ADR 0007 « Limites », décision (3d)*
- [ ] Flux sans produits retirés ni masqués ; `id` = SKU de la variante, identique aux `item_id` /
  `content_ids` des événements de suivi. — *ADR 0004 §1, ADR 0007, CLAUDE.md*
- [ ] Images des flux en URL transformée (règle permanente). — *ADR 0005 §4*
- [ ] `ConsentementService` (aujourd'hui : refuse toujours) branché sur le bandeau de consentement : sans lui,
  `gclid`, `fbclid`, `_fbp`, `_fbc` ne sont jamais enregistrés avec la commande. — *décision 7 du plan de la
  phase 4, ADR 0008 §9*
- [ ] Événement « achat » **côté serveur** (API Conversions Meta, Google Ads), partagé par `event_id`
  (`achat-CMD-…`) avec le navigateur : seule voie pour les commandes à la livraison confirmées par téléphone
  hors du site. Aujourd'hui, `SuiviService.achat` part du navigateur, une seule fois (`achat_suivi_le`).
  — *ADR 0008 §10*
- [ ] Remplir `SuiviService` (dataLayer GA4, pixel Meta) : `voirPanier`, `commencerCommande`, `choisirLivraison`,
  `choisirPaiement`, `achat` sont appelés aux bons endroits mais vides ; l'événement `achat` reçoit marque et
  catégorie vides (la page de suivi ne les connaît pas) : à compléter. — *ADR 0008 §10*
- [ ] Vrai prestataire WhatsApp/SMS derrière `NotificationClient` (journal seulement aujourd'hui).
  — *décision (validation 4b, point 5)*

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
- [ ] Vérifier la réception d'une **vraie notification PayDunya** (IPN) sur le premier serveur public : en local,
  pas de tunnel, l'IPN n'est testée qu'avec des notifications simulées. — *décision (validation du plan de la
  phase 4)*
- [x] Les journaux de nginx et du backend ne contiennent aucun jeton secret : format `confidentiel` de nginx,
  `MasqueJetons` pour le backend, contrôles automatiques (CI et tests). — *ADR 0008 §8* (corrections de la 4b)
- [ ] Journaux (nginx, backend) : conserver peu de temps et limiter l'accès, ils contiennent des adresses IP.
  Règle pour tout nouveau code : **toute adresse écrite dans un journal passe par `MasqueJetons.adresse`**, et tout
  nouveau jeton transmis dans une URL est ajouté au format de journal nginx et à `MasqueJetons`.
  — *corrections de la 4b*
- [ ] Journal d'erreurs de nginx réglé sur `crit` : il répète la ligne de requête (jeton compris) et ne peut pas
  être masqué. En production, surveiller le journal d'accès (statuts 502/504, champ « amont »). À reconsidérer
  avec un collecteur de journaux qui masque (phase 9). — *corrections de la 4b*
- [ ] Renseigner `EMAIL_ALERTES` (adresse de l'équipe) dans l'environnement de production, et brancher la
  supervision sur `ALERTE_ADMIN`. — *corrections de la 4b*
- [ ] Si le backend tourne en plusieurs instances : `ExpirationCommandes` est idempotente (verrous), mais une
  tâche qui ne doit tourner qu'une fois demandera ShedLock. — *ADR 0008 « Limites »*
## Sans phase fixée

- [ ] « Retrouver ma commande » en libre-service pour un invité qui a perdu son lien (numéro de commande +
  téléphone, nouveau lien envoyé). Aujourd'hui seul le service client peut renvoyer le lien. — *corrections de la 4b*
- [ ] Version texte des e-mails (HTML seulement aujourd'hui). — *bilan 1*
- [ ] Envoi vidéo découpé de plus de 50 minutes : Cloudinary peut refuser les derniers morceaux (signature
  valable 1 h au plus). À surveiller. — *bilan 2*
- [ ] Corriger ce que signalera le test des résultats enrichis de Google (risques : noms d'auteur des avis,
  variantes de même couleur). — *bilan 3*

## Fait

- [x] Corrections de la 4b : une seule facture active par commande (verrou `FOR UPDATE`, réutilisation de la
  facture valide), paiement en double « à rembourser » avec alerte (V18) ; journaux sans jeton (nginx et backend) ;
  jeton de suivi stocké sous forme d'empreinte SHA-256 (V19), renvoi du lien par un nouveau jeton. — *validation 4b*
- [x] Phase 4b : `/panier` (avec un panier de démonstration) et `/commande` ajoutés au workflow Lighthouse.
  — *décision (validation 4a, point 6)*
- [x] Phase 4b : attribution (UTM, page d'arrivée, référent ; gclid/fbclid/_fbp/_fbc seulement avec consentement,
  coquille « refusé ») ; réglages du paiement à la livraison dans `ParametresSite` ; idempotence ; reprise du
  paiement (route et modèle) ; lien de suivi par e-mail et notification. — *décisions 6, 7, 1, 3 et 5 du point 4b*
- [x] Phase 4a : message « La commande en ligne ouvre très bientôt… » retiré, l'ajout au panier fonctionne.
  — *décision (validation 3c), README* (commit du frontend du panier)
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
