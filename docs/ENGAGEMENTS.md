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

- [x] Factures `FAC-2026-000001` : numérotation chronologique continue **sans trou**, attribuée au paiement
  confirmé, via une table `compteur_facture` verrouillée (pas une séquence) ; tests de concurrence et
  d'annulation. Les numéros de commande peuvent avoir des trous. — *ADR 0004 §4, bilan 1*
- [ ] Décrément du stock à la commande : relire le produit si on le renvoie (règle permanente ci-dessus).
  — *ADR 0004 §2*
- [x] Panier et commande en `no-store` (règle permanente ci-dessus). — *décision (validation 3a)* (4a, 4b)
- [ ] Modèle de facture et d'avoir (mentions légales, HT / TVA / TTC ou « TVA non applicable ») **validé par le
  comptable** : modèle Thymeleaf isolé, facile à modifier. — *décision (validation du plan de la phase 4)*
- [x] 4c — `PaydunyaFournisseur`, notification (webhook) et tests de contrat (`MockRestServiceServer`), d'après la
  documentation HTTP/JSON résumée par le propriétaire. Mobile money masqué sans les trois clés ; paiement à la
  livraison inchangé. Points supposés listés dans l'ADR 0008 §7. — *ADR 0008 §7*
- [ ] 4c — **Option carte bancaire** : non proposée. Seul l'identifiant `card` est documenté ; on ne sait pas
  savoir si le compte l'a activée. À reprendre après le premier test réel. — *décision (plan, point 3)*
- [ ] Wave Business direct : ajouter un `FournisseurPaiement` quand le compte est ouvert. — *ADR 0008 §7*
- [x] 4c — Vérification serveur à serveur (`ConfirmationPaiementService`), avant toute écriture, hors transaction ;
  avant d'annuler une commande échue, la tâche d'expiration interroge le prestataire (injoignable : annulation
  reportée jusqu'à `grace-verification-paiement`). Paiement tardif : commande réactivée si le stock le permet,
  sinon « à rembourser » avec alerte, e-mail et message au client et mention sur la page de suivi.
  — *validation du plan, points 3 et 15, ADR 0008 §7*
- [x] 4c — Page de retour `/commande/retour/:jeton` (noindex, no-referrer) : le statut vient du serveur, jamais de
  l'adresse. — *ADR 0008 §7*
- [ ] 4c — Le webhook PayDunya appellera `ConfirmationPaiementService.confirmer` (qui vérifie auprès du
  prestataire puis appelle `ReceptionPaiementService`, seul chemin vers `PAYEE`), y compris pour une facture déjà
  `ANNULE` (remplacée) : l'argent est là. Le webhook répond vite et ne fait aucun appel réseau avant d'avoir
  répondu si le prestataire l'exige. — *corrections de la 4b, ADR 0008 §7*
- [x] 4c — Client HTTP du prestataire : délais courts (connexion 3 s, lecture 10 s), aucun réessai à la création,
  2 réessais sur la lecture seulement. À régler à la réception de la documentation : `validite-facture` sur la
  validité réelle d'une facture PayDunya (au moins `delai_paiement_mobile_minutes`). — *validation 4b, ADR 0008 §7*
- [x] 4c — Adresse de retour du prestataire indépendante du jeton de suivi (jeton de retour dédié, empreinte
  stockée, masqué dans les journaux). — *validation 4b, ADR 0008 §7* 
- [x] 4d — Version texte des e-mails de commande (`commande-recue.html` n'a que le HTML) : même règle que
  l'engagement « Sans phase fixée ». — *bilan 1*
- [ ] Statut `PAYEE` fixé **uniquement** par la vérification serveur à serveur du prestataire (jamais par la page
  de retour du navigateur ni par le seul corps d'une notification). — *décision (plan de la phase 4)*
- [ ] Phase 4d — **Comptable** : valider le régime de TVA (défaut « TVA non applicable »), la méthode d'arrondi (ADR 0008 §11), les mentions et le gabarit PDF ; passer le régime à « assujetti » le cas échéant (le « TTC » du site apparaît alors). — *décision 2 de la 4d*
- [ ] Phase 4d — Renseigner raison sociale, NINEA, RCCM et siège (`PUT /api/admin/parametres/facturation`) avant le lancement : le démarrage en production est refusé sinon. — *décision 1 de la 4d*
- [ ] Phase 5 — Écran « Mes factures » du compte client (les routes `/api/compte/commandes/{numero}/facture` et `/avoirs/{n}` existent). — *4d*
- [ ] Phase 6 — Écrans admin : mentions de facturation, liste des factures et avoirs, bouton « Livrer et encaisser » (montant, moyen, motif d'écart), émission d'un avoir. — *4d*
- [ ] Phase 6 — Geste « **retour client** » : crée l'avoir ET la remise en stock en une seule action (aujourd'hui l'avoir est une pièce comptable seule). — *décision 3 de la 4d*
- [ ] Phase 6 — Écran de régularisation des **écarts d'encaissement** (`commande.ecart_encaissement`) : encaissement complémentaire ou avoir, puis `ecart_regularise_le`. — *décision 4 de la 4d*
- [x] Fin de la phase 4 — Captures mobile (360/390/768/1440) du panier, du tunnel, de la confirmation, du suivi avec facture et du retour de paiement (`docs/captures/phase-4/`, hors Git) ; accueil mobile 364 Ko ; Lighthouse lu. — *CLAUDE.md* (bilan 4)
- [x] Phase 4 (bilan) — Pied de page : les pastilles Wave, Orange Money, Free Money et Carte bancaire s'affichent
  même quand le mode n'est pas activé. Corrigé : `modesPaiement` dans les paramètres publics (mobile money seulement
  avec les clés, carte jamais tant qu'elle n'est pas activée), pied de page et bandeau de confiance dynamiques.
  — *captures de recette*
- [x] Phase 4 (bilan) — Le bouton WhatsApp flottant recouvre le lien de suivi (page de suivi, 360 px) : le décaler
  ou le masquer sur cette page. Corrigé : le bouton reste visible sur mobile (canal de vente majeur) avec espace réservé
  sous le pied de page (`--reserve-whatsapp`), absent des pages à barre fixe (fiche, panier, commande), effacé quand un
  champ a le focus et pendant le défilement vers le bas. Critère : chaque élément reste atteignable (audit Playwright
  à 360/390/768/1440 px, 0 inatteignable). — *captures de recette, validation des correctifs*
- [ ] Phase 4 (bilan) — Ajouter `/suivi/:jeton` et `/commande/retour/:jeton` au workflow Lighthouse (aujourd'hui :
  accueil, catégorie, fiche, panier, commande). Le SEO à 69 (accueil) et 54 (panier, commande) vient de
  `noindex` en CI (`INDEXATION_AUTORISEE=false`) : à re-mesurer en production. — *Lighthouse, bilan 4*
- [ ] Phase 4 (bilan) — Noms en arabe (et autres écritures à mise en forme complexe) : écrits « ? » sur le PDF. Si la
  clientèle en demande, ajouter un moteur de mise en forme (HarfBuzz) et une police arabe en sous-ensemble. Le nom
  exact reste dans la facture archivée. — *test de la police, bilan 4*
- [ ] Rappel de méthode — Tout champ ajouté à un DTO mis en cache impose de monter `CachesPublics.VERSION_FORMAT`
  et d'ajouter l'empreinte dans `FormatCachesTest` (la CI l'a rappelé : commit « afficherTtc »). — *bilan 4*
- [ ] Phase 6 — Textes saisis par l'admin (bannières, éléments de confiance, pages) : avertir quand un texte cite Wave,
  Orange Money, Free Money ou la carte alors que ce moyen n'est pas actif (aujourd'hui seul l'élément « paiement » du
  bandeau est recalculé automatiquement ; les bannières et pages sont du texte libre). — *correctifs post-phase 4*

## Phase 5 — compte client

- [x] Un seul rafraîchissement de session à la fois entre onglets (Web Locks / BroadcastChannel) : deux
  rafraîchissements simultanés déconnectent l'utilisateur. — *ADR 0003 §4, bilan 1*
- [x] Jeton d'accès gardé en mémoire uniquement ; rafraîchir après la vérification de l'e-mail (le JWT garde
  `emailVerifie=false` jusque-là). — *ADR 0003, bilan 1*
- [x] Pages `compte/verifier-email` et `compte/nouveau-mot-de-passe` : renvoient le jeton en POST et servent
  `Referrer-Policy: no-referrer`. — *ADR 0003 §12, bilan 1*
- [x] Le SSR ne relaie pas `X-Forwarded-For`. — *bilan 1*
- [ ] Suppression de compte = anonymisation partielle des commandes (garder la facture ; effacer IP, user agent,
  `fbp`, `fbc`, `gclid`, `fbclid`, `utm…`, `premiere_*`, `jeton_suivi`, `nom_client`, e-mail et téléphone selon
  la durée de conservation). Durée de conservation (10 ans OHADA ?) à confirmer avec le comptable.
  — *ADR 0004 §3, bilan 1, ADR 0008 §8*
- [x] Rattacher à son compte les commandes passées en invité : **e-mail identique (sans casse) ET vérifié, jamais par
  le téléphone seul**, idempotent, journalisé (`rattachement_commande`), e-mail récapitulatif ; déclenché à la
  vérification de l'e-mail, à la réinitialisation, au changement d'e-mail confirmé et à la connexion. Le lien de suivi
  invité reste valable. Risque résiduel accepté (décision 1). — *ADR 0008, 5b*
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

## Avant le lancement

À faire avant d'ouvrir le site au public (la liste détaillée de la mise en production est dans la phase 9).

- [ ] **Test de paiement de bout en bout avec les clés de test PayDunya** (compte en attente des documents de
  l'entreprise) : paiement réussi, annulé, échoué, paiement après expiration ; page de retour ; commande
  réglée. — *décision (4c)*
- [ ] **Vérifier les points supposés de l'ADR 0008 §7** au premier test réel (format d'un refus de création,
  devise, `custom_data`, jeton dans l'adresse `confirm`, renvoi de notification après un 503, comportement après
  24 h, canal carte). Corriger `PaydunyaFournisseur` et ses tests de contrat selon les réponses réelles.
- [ ] **Vérifier une vraie notification PayDunya sur le serveur public** (pas de tunnel en local) : adresse de
  notification joignable, contrôle d'authenticité, réponse rapide ; puis un paiement réel de faible montant en
  production. — *décision (4c)*
- [ ] **Renseigner les mentions légales des factures** : raison sociale, NINEA, RCCM, adresse du siège, via
  `PUT /api/admin/parametres/facturation` (elles sont en base, plus en variables d'environnement ; écran en phase 6).
  Vides, les factures se génèrent quand même avec un avertissement dans les journaux en développement ; **le démarrage
  est refusé** si `INDEXATION_AUTORISEE=true` ou en profil `prod` (sauf démonstration) tant qu'une mention manque.
  À faire valider par le comptable. — *décision (4c), déplacé en base (4d)*
- [ ] **Confirmer le régime de TVA avec le comptable** : par défaut « TVA non applicable » (le site n'affiche pas
  « TTC »). Passer à « assujetti » (18 %) via la même route, après validation de la méthode d'arrondi (ADR 0008).
  — *décision (4d)*
- [ ] Renseigner `PAYDUNYA_*` (clés de production), `EMAIL_ALERTES`, activer le mobile money dans les réglages.

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
- [x] Version texte des e-mails : dérivée du HTML (`HtmlVersTexte`), tous les e-mails en multipart/alternative. — *bilan 1* (4d)
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

### Ajoutés pendant la phase 5

- [ ] **Sans phase fixée** : « Retrouver ma commande » par code envoyé sur WhatsApp, avec SMS en secours, après la
  vérification Meta Business (bloquée tant que les documents de l'entreprise ne sont pas prêts). Connexion complète par
  téléphone (e-mail facultatif, téléphone unique et vérifié, limites anti-fraude au SMS) seulement si l'usage le justifie.
  — *décision (validation du plan de la phase 5)*
- [x] Lighthouse authentifié (espace client) : une connexion par passage avec un compte de test dédié
  (`DEMO_CLIENT_*`, mot de passe jetable généré par le job) ; limitation du débit coupée **en CI seulement**
  (`infra/docker-compose.ci.yml`), aucun échec de connexion donc aucun verrouillage. Pages mesurées : `/compte` et
  `/commande` connecté. Les listes et le détail de commande s'ajouteront en 5c. — *5b*
- [ ] Scripts de capture : `infra/scripts/captures/` (playwright-core installé à la demande, jamais dans les
  `package.json`). `parcours-auth.mjs` couvre l'authentification (5a) ; étendre à chaque partie. — *décision (plan 5)*
- [x] Les pages `/compte/**` sont rendues dans le navigateur (`RenderMode.Client`) ; nginx pose `no-store`,
  `noindex` et `no-referrer` sur `/compte*`. — *5a*
- [x] Le panier attend la restauration de la session avant de lire ou modifier (sinon un client connecté qui recharge
  la page voyait un panier de visiteur vide) ; la fusion du panier invité est faite par le serveur à la première lecture
  du client connecté, vérifiée de bout en bout. — *5a*
- [x] Changement de mot de passe : ancien exigé (essais limités par compte, réponse 422 jamais 401), autres sessions
  révoquées via la revendication `sid` du jeton, session courante conservée, e-mail d'alerte. — *5b*
- [x] Changement d'e-mail : mot de passe exigé, lien vers la nouvelle adresse (24 h), alerte à l'ancienne avec « Ce
  n'est pas moi » (annule, ou rétablit l'ancienne adresse dans les 7 jours, révoque les sessions, envoie un lien de
  réinitialisation) ; réponse identique si l'adresse est déjà prise. — *5b*
- [x] Consentement marketing : date + source (`INSCRIPTION`, `COMPTE`, `ADMIN`) sur le compte et historique en ajout
  seul ; retrait en un clic, sans mot de passe. Téléphone du profil et des adresses normalisé en E.164. — *5b*
- [ ] **Avant le lancement** : un utilisateur supprimé doit aussi disparaître de `historique_consentement` /
  `changement_email` (anonymisation, partie 5d) ; vérifier avec le juriste la durée de conservation de la preuve de
  consentement. — *5b*
- [ ] Limite du carnet d'adresses fixée à 10 (`AdresseService.MAXIMUM`) : à revoir si des clients professionnels en
  demandent davantage. — *5b*
