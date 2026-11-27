# ADR 0008 — Commande : réservation du stock, idempotence, paiement à la livraison, suivi par lien

- **Statut** : accepté (parties 4a à 4d : commande, paiements PayDunya, factures et avoirs)
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
(`POST /api/commandes/suivi/{jeton}/reprise-paiement`), sans nouvelle réservation, tant que la commande n'a pas
expiré. Interface `FournisseurPaiement` (4b), implémentation PayDunya (4c). Mobile money et carte ne sont proposés
que si un prestataire est configuré (`GET /api/commandes/options`).

**Une commande n'a jamais deux factures payables** (deux factures permettraient de payer deux fois). Le démarrage
et la reprise du paiement verrouillent la commande (`SELECT ... FOR UPDATE`) pendant toute la démarche : la facture
encore valide (en attente, créée depuis moins de `boutique.commande.validite-facture`) est **réutilisée** ; une
nouvelle n'est créée que si la précédente est expirée, annulée ou en échec, et les anciennes factures en attente
sont alors abandonnées. La validité de la facture doit être réglée sur celle du prestataire, et au moins sur le
délai de paiement de la commande : une facture réputée expirée par la boutique mais encore ouverte chez le
prestataire laisserait deux factures payables.

**Jamais de verrou ni de transaction pendant un appel réseau.** Trois temps, chacun dans sa propre transaction
courte : (1) *réservation* : verrou bref sur la commande, qui est marquée « facture en cours de création » jusqu'à
une échéance courte (`commande.paiement_en_cours_jusqua`, `boutique.commande.reservation-paiement`, 30 s), puis
commit ; (2) *appel au prestataire*, hors transaction ; (3) *enregistrement* de la facture dans une nouvelle
transaction, qui lève la réservation (levée aussi en cas d'échec de l'appel). Une requête qui trouve la commande
réservée attend (`pause-attente-paiement`) puis réutilise la facture de la première : 4 appels simultanés = une
seule demande au prestataire. Une réservation périmée (serveur arrêté pendant l'appel) est reprise par la requête
suivante ; la durée de réservation doit donc dépasser les délais du client HTTP. Tests : aucun appel fait dans une
transaction, aucun verrou tenu pendant l'appel (`FOR UPDATE NOWAIT` d'une autre connexion), un prestataire lent ne
ralentit pas le reste du site. Le même principe vaut pour les appels `confirm` (notification, page de retour, tâche
d'expiration).

**Adresse de retour** donnée au prestataire : `/commande/retour/{jeton}` avec un jeton de retour propre à chaque
facture (seule son empreinte est stockée, `paiement.empreinte_jeton_retour`), jamais le jeton de suivi, qui peut être
renouvelé. Ce jeton ne donne accès qu'au statut du paiement. Il est masqué dans les journaux comme les autres.

**Dernier recours** : si un second paiement arrive quand même pour une commande déjà réglée,
`ReceptionPaiementService` le marque `A_REMBOURSER` (jamais de seconde vente, la commande reste intacte), journalise
`ALERTE_ADMIN` et envoie un e-mail à `EMAIL_ALERTES` s'il est configuré ; le paiement est listé dans
`GET /api/admin/paiements`. C'est aussi le sort par défaut d'un paiement reçu pour une commande annulée ou expirée :
la réactivation quand le stock le permet est traitée en 4c.

**Vérification et paiement tardif.** `ConfirmationPaiementService.confirmer` lit le paiement, interroge le
prestataire **hors transaction**, recontrôle montant, devise et numéro de commande (sinon alerte, rien n'est
enregistré), puis appelle `ReceptionPaiementService` (transaction courte, commande verrouillée). Un paiement reçu
pour une commande annulée **par l'expiration** réactive la commande si le stock le permet (stock et code promo
réservés de nouveau, la libération du stock se compte par réservation) ; sinon, ou si la commande est déjà réglée
ou annulée par une personne, il est « à rembourser » : alerte, e-mail et message au client, `remboursementEnCours`
sur la page de suivi. La tâche d'expiration vérifie d'abord le prestataire ; s'il est injoignable, l'annulation est
reportée jusqu'à `grace-verification-paiement` (2 h). La page de retour `GET /api/commandes/retour/{jeton}` n'ouvre
que le statut et ne croit jamais le navigateur.

**PayDunya** (`PaydunyaFournisseur`, d'après developers.paydunya.com/doc/FR/http_json, lue par le propriétaire du
projet : résumé, pas le texte intégral). Adresses : test `https://app.paydunya.com/sandbox-api/v1/`, production
`.../api/v1/` ; en-têtes `PAYDUNYA-MASTER-KEY`, `-PRIVATE-KEY`, `-TOKEN`. Création `POST checkout-invoice/create`
(`invoice.total_amount`, `store.name`, `custom_data`, `actions.*_url` ; aucun canal imposé : le client choisit sur la
page PayDunya, aucune donnée personnelle du client envoyée) ; réponse : `response_code "00"`, `response_text` =
adresse de paiement, `token`. Vérification `GET checkout-invoice/confirm/{token}` : `status` parmi `pending`,
`completed`, `cancelled`, `failed` (casse ignorée), `invoice.total_amount`, `custom_data`. Les clés ne sont jamais
dans un journal ni un `toString`. Sans les trois clés, `disponible()` est faux : **le mobile money est masqué** et le
site fonctionne avec le paiement à la livraison (testé).

*Notification (IPN)* : `POST /api/paiements/paydunya/webhook`, `application/x-www-form-urlencoded`, champs
`data[...]`. `data[hash]` = SHA-512 de la clé principale, comparé en temps constant ; il est **fixe** et ne signe pas
le contenu. La notification n'est donc qu'un signal : après le hash, `confirm/{token}` est **toujours** rappelé, et
seuls son statut, son montant et son numéro de commande comptent (un corps « completed » contredit par `confirm` ne
règle rien : testé). Réponses : 401 hash invalide, 400 jeton absent, 503 PayDunya injoignable, 200 sinon.

*Validité d'une facture* : une facture `pending` peut rester payable 24 h chez PayDunya, alors que la commande
expire après `delai_paiement_mobile_minutes` (30 min) ; aucun appel pour l'annuler n'est documenté. Un paiement entre
30 minutes et 24 h suit donc le chemin « paiement tardif » (réactivation si le stock le permet, sinon « à
rembourser », alerte et information du client ; testé à 22 h et 20 h). Le client est averti que le paiement doit être
fait dans le délai (tunnel de commande et page de retour). `boutique.commande.validite-facture` (30 min) est la
durée pendant laquelle *nous* réutilisons une facture ; elle n'annule rien chez PayDunya.

**Points supposés, à vérifier au premier test réel** (marqués `SUPPOSE` dans `PaydunyaFournisseur`) :
1. format d'une réponse de refus à la création (seul `response_code "00"` = succès est documenté) : tout autre code ou
   HTTP non 2xx est un échec, le texte est journalisé sans les clés ;
2. la devise n'est pas dans `confirm` : XOF supposée ;
3. le numéro de commande est relu dans `custom_data.numero_commande` ;
4. le jeton est un segment de chemin de l'adresse `confirm` ;
5. comportement de PayDunya face à un 503 sur la notification (renvoie-t-il ?) et délai maximal qu'il tolère pour la
   réponse ;
6. une facture déjà payée puis réinterrogée après 24 h, et une facture `cancelled` qui serait payée ensuite ;
7. option carte : seul l'identifiant `card` est documenté (`invoice.channels`) ; savoir si le compte l'a activée
   n'est pas documenté. La carte reste donc **non proposée** tant que ce point n'est pas éclairci.
Identifiants de canaux de Wave Sénégal et Free Money : non documentés sur la page lue (liste externe) : sans objet
tant qu'aucun canal n'est imposé.

**Évolution prévue** : un fournisseur **Wave Business** direct (demande de compte en cours) pourra être ajouté plus
tard comme une autre implémentation de `FournisseurPaiement` (nouvelle valeur de `TypeFournisseurPaiement`, sa propre
vérification serveur à serveur) sans toucher au tunnel, à `ConfirmationPaiementService` ni à `ReceptionPaiementService`.

### 8. Suivi par lien, sans compte

`/suivi/{jeton}` : jeton de 256 bits (base64url) créé avec la commande. **Seule son empreinte SHA-256 est
stockée** (`commande.empreinte_jeton_suivi`, unique), comme pour tous les autres jetons de l'application : une fuite
de la base ne donne aucun lien utilisable. Le jeton n'existe qu'au moment où il est généré (réponse de création,
e-mail, message au client). Conséquences :

- **Renvoyer le lien = générer un nouveau jeton**, qui remplace l'ancien (l'ancien lien cesse de fonctionner) :
  `POST /api/admin/commandes/{numero}/renvoyer-lien-suivi` (service client). Le lien part par e-mail et par message
  au client ; le service client ne le voit jamais.
- **Requête rejouée** (même `Idempotency-Key`, réponse perdue) : le jeton ne pouvant être redonné, un nouveau jeton
  est généré, renvoyé dans la réponse et envoyé au client ; le lien du premier message est mort. Deux rejeux
  simultanés se suivent (verrou) : le dernier jeton est le valable.
- Les liens déjà émis avant la migration V19 restent valables : leur empreinte y est calculée.
- L'adresse de retour du paiement ne dépend pas du jeton de suivi (voir §7) : renouveler le jeton ne la casse pas.

Le jeton ne protège que des données masquées : prénom, téléphone partiellement masqué (`+221 77 ••• •• 67`), quartier
et ville sans la rue, ni e-mail, ni adresse IP, ni auteurs des changements de statut, ni attribution marketing.

Protections : 404 sans distinguer format invalide et inconnu, limite de débit par IP, `no-store`, `noindex`,
`<meta name="referrer" content="no-referrer">` et `Referrer-Policy: no-referrer` posé par nginx sur `/suivi/`
(le jeton ne part dans aucun en-tête Referer), `Disallow: /suivi` dans `robots.txt`.

**Aucun jeton dans les journaux** (suivi, vérification d'e-mail, réinitialisation, déverrouillage) :

- nginx : format `confidentiel` (`default.conf.template`) utilisé par tous les serveurs. Le chemin `/suivi/{jeton}` et
  `/api/commandes/suivi/{jeton}/…` devient `…/[jeton]/…`, les paramètres des pages à jeton sont supprimés, tout paramètre
  `jeton` ou `token` est masqué, et le `Referer` est réduit au site d'origine. Le journal d'erreurs de nginx, qui répète la
  ligne de requête sans qu'on puisse la masquer, est limité aux erreurs critiques ; les échecs restent visibles dans le
  journal d'accès (statut, champ « amont »). Contrôlé par `infra/scripts/verifier_journaux_nginx.sh` (CI, job infra).
- Backend : toute adresse écrite dans un journal passe par `MasqueJetons.adresse` (filtre d'origine, gestionnaire
  d'erreurs). Les jetons de vérification, de réinitialisation et de déverrouillage voyagent dans le corps des requêtes
  de l'API, jamais dans son adresse. Contrôlé par des tests qui capturent la sortie du backend pendant les parcours
  complets et vérifient qu'aucun jeton n'y figure.

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

### 11. Factures, avoirs et encaissement à la livraison (4d)

**Pièces immuables.** À l'émission, tout est copié dans la facture : vendeur (raison sociale, NINEA, RCCM, adresse du
siège), régime et taux de TVA, client (nom, téléphone, e-mail, adresse), lignes, montants, et **le PDF lui-même** (octets
conservés avec leur SHA-256). Modifier ensuite `parametres_site`, le catalogue ou le client ne change rien : la page de
suivi et l'e-mail servent le PDF stocké, on ne le régénère jamais. La base l'impose : un déclencheur
(`interdire_modification_piece`) refuse tout `UPDATE` et `DELETE` sur `facture`, `ligne_facture`, `avoir`, `ligne_avoir`
et `encaissement`. Une erreur se corrige par un avoir. Test : changer raison sociale, NINEA, adresse et régime après
l'émission laisse le PDF de l'ancienne facture identique octet pour octet.

**Mentions légales en base, plus en variables d'environnement.** Raison sociale, NINEA, RCCM, adresse du siège, régime de
TVA, taux (points de base, 1800 = 18 %) et mention « TVA non applicable » sont des colonnes de `parametres_site`,
réglables par `PUT /api/admin/parametres/facturation` (administration ; écran en phase 6). Le contrôle de démarrage lit la
base : mentions vides = avertissement en développement, démarrage refusé en production (indexation autorisée, ou profil
`prod` hors démonstration). Les variables `FACTURE_*` disparaissent, sauf `FACTURE_MENTIONS_OBLIGATOIRES`.

**Numérotation sans trou.** Table `compteur_document(type, annee, dernier_numero)`, `FAC` et `AV` séparés, incrément
`UPDATE … RETURNING` dans la transaction d'émission (ADR 0004 §4) : annulée avec elle, aucun numéro perdu. Appelée hors
transaction, l'attribution est refusée (`Propagation.MANDATORY`) : un numéro validé seul pourrait laisser un trou.

**Quand une facture est émise** (une par commande, idempotent) :
- paiement en ligne confirmé : **dans la transaction de `ReceptionPaiementService`**, y compris la réactivation après
  paiement tardif. Pas de commande payée sans facture, pas de numéro perdu. Contrepartie assumée : un bogue de rendu
  empêcherait d'enregistrer le paiement (le prestataire réessaie, rien n'est perdu, l'alerte est dans les journaux) ; le
  rendu est donc volontairement simple et testé, et les caractères impossibles à écrire sont remplacés par « ? » ;
- paiement à la livraison : au passage à `LIVREE` par `POST /api/admin/commandes/{numero}/livrer`, avec l'encaissement.

**TVA : les prix du site sont TTC.** Régime réglable : « assujetti » (TVA extraite du TTC) ou « TVA non applicable »
(défaut, en attendant la confirmation du comptable ; la mention « TTC » du site ne s'affiche alors pas). **Méthode
d'arrondi, à faire valider par le comptable** :
1. la TVA du document est calculée **une fois sur le total TTC** : `TTC × taux / (1 + taux)`, arrondie au franc le plus
   proche (demi vers le haut) ;
2. elle est répartie sur les lignes **au plus fort reste** ; la somme des TVA des lignes égale donc exactement la TVA du
   total, et HT + TVA = TTC sur chaque ligne et sur le total ; une ligne ne s'écarte jamais de plus d'un franc de sa TVA
   exacte ;
3. la remise d'un code promo se répartit de la même façon sur les articles (au prorata de leur montant brut), l'excédent
   éventuel sur la livraison ; chaque ligne garde son brut, sa remise et son net.

Exemple : 12 000 FCFA TTC à 18 % → TVA 1 831 (1 830,51 arrondi), HT 10 169.

**Encaissement à la livraison.** `encaissement` : montant reçu, moyen (espèces, Wave au livreur, Orange Money au livreur,
autre + précision obligatoire), personne qui a encaissé (le livreur, ou l'agent par défaut), agent qui a enregistré, date.
La facture est émise **pour le total de la commande**, l'encaissement réel est enregistré tel quel.
- montant supérieur au total : refusé (422) ;
- montant inférieur : accepté avec un **motif obligatoire** (monnaie manquante, article contesté, remise accordée, autre
  + précision) ; la commande porte alors un **écart d'encaissement à régulariser** (`commande.ecart_encaissement`) et
  l'administration est alertée. Régularisation plus tard par un avoir ou un encaissement complémentaire (écran en
  phase 6) ;
- `LIVREE` depuis `CONFIRMEE`, `PAYEE`, `EN_PREPARATION`, `EXPEDIEE` ; une commande déjà payée en ligne se livre sans
  encaissement (corps refusé).

**Avoirs.** `POST /api/admin/factures/{numero}/avoirs` (administration seulement) : tout (`total`) ou des lignes
(`rang`, `quantite`), plafonnés au restant de chaque ligne. Numéro `AV-2026-000001`, identité figée de la facture (jamais
celle du site d'aujourd'hui). Le dernier avoir d'une ligne reprend **exactement le reste** (montant et TVA) : le cumul
rejoint la facture au franc près, jamais au-dessus. La commande passe à `REMBOURSEE` quand les avoirs couvrent toute la
facture. Deux avoirs simultanés : verrou de la commande, un seul passe. **Pièce comptable seule : pas de remise en stock,
pas de remboursement automatique** (geste « retour client » en phase 6).

**PDF.** `openhtmltopdf` (rendu d'un gabarit XHTML Thymeleaf, `templates/pdf/piece.html`, isolé pour que le comptable le
fasse modifier). Polices **standard du PDF** (Helvetica, Courier) : rien à incorporer, une facture de 20 lignes pèse
environ 5 Ko, très en dessous de la limite des pièces jointes. Contrepartie : jeu Windows-1252 (tout le français).

**Accès, toujours en `no-store`.** Lien de suivi (`/api/commandes/suivi/{jeton}/facture`, `…/avoirs/{numero}`, même
limitation de débit que le suivi) : fonctionne même sans e-mail. Compte connecté (`/api/compte/commandes/{numero}/facture`,
sa commande seulement ; écran en phase 5). Personnel (`/api/admin/factures/{numero}/pdf`, `/api/admin/avoirs/{numero}/pdf`).
Une pièce inexistante ou qui n'appartient pas au demandeur donne la même 404.

**E-mails.** Tous en `multipart/alternative` : la version texte est **dérivée du HTML** (`HtmlVersTexte`, liens conservés),
une seule source par e-mail. Facture et avoir partent en pièce jointe PDF si le client a donné un e-mail (au-delà de
1 Mo, sans pièce jointe, journalisé).

**Ventes.** `produit.nombre_ventes` augmente à l'émission de la facture (unités des lignes « article »), diminue des
unités créditées par un avoir, jamais sous zéro. `UPDATE` relatif en SQL dans la transaction de la pièce ; la colonne
n'est plus modifiable par l'entité (une modification admin ne réécrit pas un compteur périmé). Une commande non payée ou
non livrée ne compte pas. Les caches publics expirent d'eux-mêmes (10 min).

## Conséquences

- Le stock vendable est exact à chaque instant : les commandes en attente le réservent, l'expiration le rend.
- Un client peut recommencer sans risque après une coupure : même clé, même commande.
- Des commandes à la livraison non confirmées peuvent réserver du stock jusqu'à 24 h : c'est le prix de la
  confiance accordée au paiement à la livraison ; le plafond de montant et de commandes par numéro limite l'abus.

## Limites et points ouverts

- `ExpirationCommandes` tourne sur chaque instance (idempotente grâce aux verrous). Si une tâche ne doit tourner
  qu'une fois, il faudra ShedLock.
- Si le serveur s'arrête pendant l'appel au prestataire, la facture peut exister chez lui sans être enregistrée
  chez nous : la réservation expire, une nouvelle facture est créée, et si l'ancienne est payée quand même, le
  paiement est reconnu par sa référence ou marqué « à rembourser ».
- La carte bancaire n'est pas proposée dans le tunnel tant que le prestataire n'indique pas qu'elle est activée
  sur le compte (4c).
- Le modèle de facture (mentions légales, régime de TVA, méthode d'arrondi du §11) reste à faire valider par le comptable ; le gabarit est isolé dans `templates/pdf/piece.html`.
- Un avoir ne remet pas en stock et ne rembourse pas : le geste « retour client » (avoir + remise en stock) est prévu en phase 6.
