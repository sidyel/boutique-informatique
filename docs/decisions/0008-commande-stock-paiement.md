# ADR 0008 — Commande : réservation du stock, idempotence, paiement à la livraison, suivi par lien

- **Statut** : accepté (parties 4a et 4b ; les paiements PayDunya et les factures complètent ce document en 4c et 4d)
- **Date** : 2026-11-26
- **Phase** : 4

## Contexte

Un client sur un téléphone Android d'entrée de gamme, avec une connexion qui coupe, commande du matériel à
plusieurs centaines de milliers de FCFA. Il faut qu'il ne puisse jamais (1) payer ce qui n'est plus en stock,
(2) commander deux fois par un double appui ou un réessai, (3) perdre sa commande en perdant l'onglet. Et qu'une
commande abandonnée ne bloque pas le stock indéfiniment.

## Décisions

### 1. Création de la commande : une seule transaction

`POST /api/commandes` fait, dans **une** transaction : clé d'idempotence, contrôles (panier, livraison, paiement),
réservation du code promo, lignes figées, réservation du stock, historique, vidage du panier. Une erreur, où
qu'elle survienne, annule tout : jamais de réservation partielle.

Le serveur ne reçoit **aucun prix** : il relit le panier. Le client envoie le `totalAttendu` qu'il a sous les
yeux ; s'il diffère (prix, frais de livraison, code promo), la commande est refusée (409 `TOTAL_MODIFIE`) et le
client revoit le nouveau total avant de payer. Un prix qui a changé depuis l'ajout au panier bloque la commande
(409 `PRIX_MODIFIES`) jusqu'à ce que le client l'accepte.

### 2. Anti-survente

Chaque ligne est réservée par `UPDATE variante_produit SET stock = stock - q WHERE id = ? AND actif AND stock >= q
RETURNING stock`. L'UPDATE est atomique et prend le verrou de la ligne : sur la dernière unité, le second client
attend la fin de la transaction du premier, ne trouve plus de stock et reçoit 409. Les variantes sont traitées par
identifiant croissant (pas d'interblocage entre deux commandes qui partagent plusieurs variantes). `version` est
incrémentée à la main (verrouillage optimiste du back-office), puis variantes et produits sont relus
(`stockTotal` vient d'un trigger, ADR 0004 §2). Chaque réservation écrit un `MouvementStock` `SORTIE_VENTE`,
chaque libération un `ANNULATION` (migration V17).

### 3. Réservation avec échéance

- **Paiement en ligne** : `EN_ATTENTE_PAIEMENT`, stock réservé 30 minutes (`delai_paiement_mobile_minutes`).
- **Paiement à la livraison** : `EN_ATTENTE_CONFIRMATION`, stock réservé 24 heures
  (`paiement_livraison_delai_annulation_heures`, et non 48 h : ne pas bloquer le stock).

`ExpirationCommandes` (toutes les minutes) annule les commandes échues : statut `ANNULEE`, stock remis en vente,
utilisation du code promo rendue, paiements en attente abandonnés. **Plusieurs instances** : chaque commande est
traitée dans sa propre transaction après `SELECT … FOR UPDATE`, puis le statut et l'échéance sont revérifiés ;
l'instance en retard ne trouve plus rien à faire. Le stock n'est libéré qu'une fois (un `ANNULATION` par commande,
contrôlé). Les mêmes règles servent à l'annulation manuelle du service client.

### 4. Idempotence de la création

Le frontend génère une clé (UUID) par tentative de commande, l'envoie en `Idempotency-Key` et la garde dans
`sessionStorage` jusqu'au succès. Table `cle_idempotence` (`portee`, `cle`, `empreinte`, `commande_id`,
`expire_le` après 24 h) :

- la clé est liée au **client** (`u:{id}` pour un compte, `i:{empreinte du cookie du panier}` pour un invité) ;
- `INSERT … ON CONFLICT DO NOTHING` : deux requêtes simultanées avec la même clé sont sérialisées par l'index
  unique. La seconde attend le commit de la première puis **relit la commande** (200, `rejouee: true`) ; si la
  première échoue (stock…), la seconde passe pour son compte ;
- l'`empreinte` du contenu de la demande est comparée : même clé, autre contenu → 409 `CLE_IDEMPOTENCE_REUTILISEE` ;
- la clé est obligatoire (422 sinon).

### 5. Paiement à la livraison

Réglable dans `ParametresSite` : plafond (500 000 FCFA), nombre de commandes non confirmées par numéro de
téléphone (2), délai d'annulation (24 h), désactivation. La commande est `EN_ATTENTE_CONFIRMATION` ;
`POST /api/admin/commandes/{numero}/confirmer` (service client) la passe à `CONFIRMEE`, `…/annuler` (motif
obligatoire) l'annule. Une commande déjà payée ne s'annule pas par cette route : elle se rembourse (avoir, 4d).

### 6. Code promo réservé à la commande

L'utilisation est comptée à la création (UPDATE conditionnel sur `nombre_utilisations < utilisations_max`) et
rendue si la commande est annulée ou expire. Deux clients ne peuvent pas prendre en même temps la dernière
utilisation ; le plafond par client se vérifie par compte, ou par numéro de téléphone pour un invité, après que
l'UPDATE a verrouillé la ligne du code.

### 7. Le paiement en ligne démarre après le commit

L'appel au prestataire est un appel réseau : il n'est jamais fait dans la transaction de la commande (aucun
verrou ni connexion gardés pendant l'attente). Si le prestataire ne répond pas, la commande existe, le stock est
réservé et la réponse porte `paiementAReprendre` : le client **reprend le paiement** depuis sa page de suivi
(`POST /api/commandes/suivi/{jeton}/reprise-paiement`), ce qui crée une nouvelle facture chez le prestataire pour
la même commande, sans nouvelle réservation, tant que la commande n'a pas expiré. Interface
`FournisseurPaiement` (4b), implémentation PayDunya (4c). Mobile money et carte ne sont proposés que si un
prestataire est configuré (`GET /api/commandes/options`).

### 8. Suivi par lien, sans compte

`/suivi/{jeton}` : jeton de 256 bits (base64url) créé avec la commande, **gardé en clair** (`commande.jeton_suivi`,
unique) : il faut pouvoir renvoyer le même lien (réponse rejouée, e-mail, relance). Le jeton ne protège que des
données masquées : prénom, téléphone partiellement masqué (`+221 77 ••• •• 67`), quartier et ville sans la rue, ni
e-mail, ni adresse IP, ni auteurs des changements de statut, ni attribution marketing. Un compromis de la base
exposerait de toute façon les données en clair ; le hachage n'apporterait rien ici.

Protections : 404 sans distinguer format invalide et inconnu, limite de débit par IP, `no-store`, `noindex`,
`<meta name="referrer" content="no-referrer">` et `Referrer-Policy: no-referrer` posé par nginx sur `/suivi/`
(le jeton ne part dans aucun en-tête Referer), `Disallow: /suivi` dans `robots.txt`. Le jeton apparaît dans les
journaux d'accès de nginx : ces journaux sont à protéger comme des données personnelles (phase 9).

À la création : le lien est affiché sur la page de confirmation, envoyé par e-mail s'il existe, et une
notification (`NotificationClient`, journal seulement pour l'instant : WhatsApp/SMS) est émise.

### 9. Sécurité de l'API

- **CSRF** : `VerificationOrigineFiltre` exige un en-tête `Origin` du site sur POST, PUT, PATCH et DELETE de
  `/api/panier/**` et `/api/commandes/**` (le panier invité repose sur un cookie). Les lectures et le webhook
  de paiement ne sont pas concernés.
- **Limites de débit** de `POST /api/commandes` : par IP (valeur large, 20/h, à cause du CGNAT des opérateurs
  mobiles) et par numéro de téléphone (8/h, haché dans Redis). Réglables dans `boutique.commande`.
- **Attribution** : UTM, page d'arrivée et référent sont enregistrés (nettoyés, tronqués), premier et dernier
  contact. `gclid`, `fbclid`, `_fbp`, `_fbc` ne le sont qu'avec le consentement : `ConsentementService` est une
  coquille qui refuse toujours jusqu'à la phase 7. L'adresse IP est celle vue par le serveur (valve RemoteIp).

### 10. Événement « achat » des régies

Émis par le navigateur **une seule fois par commande** et seulement quand la vente est acquise (statut `PAYEE` ou
`CONFIRMEE`…) : la page de suivi appelle `POST …/achat-suivi`, qui fait un `UPDATE … WHERE achat_suivi_le IS NULL` ;
seul l'appelant qui reçoit `emettre: true` pousse l'événement. Une commande à la livraison **non confirmée n'est
pas une vente** : pas d'événement avant l'appel du service client. Les conversions côté serveur (API Conversions
Meta, Google Ads), qui couvriront les confirmations faites hors du site, arrivent en phase 7.

## Conséquences

- Le stock vendable est exact à chaque instant : les commandes en attente le réservent, l'expiration le rend.
- Un client peut recommencer sans risque après une coupure : même clé, même commande.
- Des commandes à la livraison non confirmées peuvent réserver du stock jusqu'à 24 h : c'est le prix de la
  confiance accordée au paiement à la livraison ; le plafond de montant et de commandes par numéro limite l'abus.

## Limites et points ouverts

- `ExpirationCommandes` tourne sur chaque instance (idempotente grâce aux verrous). Si une tâche ne doit tourner
  qu'une fois, il faudra ShedLock.
- Deux requêtes rejouées qui démarrent le paiement exactement en même temps peuvent créer deux factures chez le
  prestataire ; les deux restent valables, le doublon de paiement est traité en 4c.
- La carte bancaire n'est pas proposée dans le tunnel tant que le prestataire n'indique pas qu'elle est activée
  sur le compte (4c).
- Le modèle de facture (mentions légales, HT/TVA/TTC) sera validé par le comptable (4d).
