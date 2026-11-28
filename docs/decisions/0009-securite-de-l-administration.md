# ADR 0009 — Sécurité de l'administration : second facteur TOTP, réauthentification, journal des actions

- **Statut** : accepté (partie 6a)
- **Date** : 2026-11-28
- **Phase** : 6

## Contexte

Un compte de l'équipe voit les coordonnées de tous les clients, change les prix, confirme et annule les commandes, émet
des avoirs. Un mot de passe seul ne suffit pas : il peut être hameçonné ou réutilisé ailleurs. Il faut aussi pouvoir
répondre, des mois plus tard, à « qui a fait quoi, quand, avec quelle valeur avant et après ». Cet ADR complète l'ADR 0003
(section « À traiter, phase 6 ») et en fixe les détails.

## Décisions

### 1. Connexion des rôles internes en deux temps

`SERVICE_CLIENT`, `GESTIONNAIRE_STOCK`, `ADMIN` et `SUPER_ADMIN` n'obtiennent **jamais** de jeton d'accès après le seul
mot de passe. `POST /api/auth/connexion` répond alors `{ "etape": …, "jetonEtape": … }` au lieu d'une session :

| `etape` | Situation |
|---|---|
| `ENROLEMENT_REQUIS` | le compte n'a pas encore de second facteur actif |
| `CODE_REQUIS` | un second facteur est actif : saisir un code à 6 chiffres (ou un code de secours) |
| `CODES_SECOURS_A_CONFIRMER` | le code est bon mais les codes de secours n'ont pas été confirmés (voir §5) |

Le **jeton d'étape** est un jeton opaque (256 bits), stocké sous forme d'empreinte SHA-256 (table `etape_connexion`),
valable 5 minutes et renouvelé à chaque étape réussie, 30 minutes au plus depuis le mot de passe, **5 essais** au plus. Ce
n'est pas un JWT : il ne peut pas être présenté en `Authorization: Bearer` et n'ouvre aucune route. Le mot de passe est
vérifié avec les mêmes protections qu'une connexion normale (verrouillage par couple e-mail/IP, leurre BCrypt).

**Invariant** : `SessionService.ouvrir` refuse (exception) d'ouvrir une session pour un rôle interne si le second facteur
n'a pas été vérifié ; `rafraichir` ne fait que prolonger une famille de jetons déjà ouverte. Aucun autre chemin
n'émet de jeton d'accès (un test le vérifie pour la connexion, la réinitialisation du mot de passe et le rafraîchissement).
Les sessions internes durent 12 h, absolues (ADR 0003).

### 2. TOTP (RFC 6238), sans bibliothèque

SHA-1, 6 chiffres, pas de 30 s, fenêtre de ±1 pas (dérive d'horloge du téléphone). Le code tient en une cinquantaine de
lignes du JDK (`HmacSHA1`) et est **testé avec les vecteurs de la RFC 6238** (annexe B : secret `12345678901234567890`,
instants 59, 1111111109, 1111111111, 1234567890, 2000000000 et 20000000000, codes à 8 chiffres). Le secret fait 160 bits,
encodé en Base32 (RFC 4648) pour l'application d'authentification. Le QR code (`otpauth://totp/…`) est généré par le
backend avec `zxing-core` et renvoyé en image SVG (`data:`) : le secret n'est jamais traité par le navigateur autrement
que pour l'afficher une fois (saisie manuelle en secours).

**Anti-rejeu.** On mémorise le dernier pas accepté (`utilisateur.totp_dernier_pas`) ; un code est accepté seulement si son
pas est **strictement supérieur**, par `UPDATE … WHERE totp_dernier_pas IS NULL OR totp_dernier_pas < :pas` (atomique : deux
requêtes simultanées avec le même code, une seule passe).

**Limitation.** Par compte : 10 saisies par 10 minutes ; par IP : 30 par 15 minutes (plusieurs abonnés mobiles partagent une IP) ; par jeton d'étape : 5 essais, puis
il faut refaire le mot de passe. Un dépassement répond 429 sans consommer le code. Un échec est journalisé (compte, IP) sans
le code saisi.

### 3. Secret TOTP chiffré, clé hors de la base

Le secret est chiffré en **AES-256-GCM** avant d'être écrit (`utilisateur.totp_secret_chiffre`). Format :
`v1.<identifiant de clé>.<IV>.<texte chiffré>` (Base64 URL) ; l'identifiant de clé est le début du SHA-256 de la clé ; les
**données associées** (AAD) contiennent l'identifiant du compte : un secret copié d'un compte à l'autre ne se déchiffre pas.

La clé vient de la variable d'environnement **`ADMIN_CLE_CHIFFREMENT`** (Base64, 32 octets minimum une fois décodée) :
jamais de la base, jamais du code, jamais d'une valeur par défaut. **Le démarrage est refusé si elle est absente ou trop
courte**, dans tous les profils (y compris les tests, qui fournissent une clé de test dans `application.properties`).
Génération : `openssl rand -base64 32`.

**Rotation de la clé** (documentée ici, testée) :
1. générer une nouvelle clé ;
2. déployer avec `ADMIN_CLE_CHIFFREMENT=<nouvelle>` et `ADMIN_CLE_CHIFFREMENT_PRECEDENTE=<ancienne>` ;
3. au démarrage, `ReChiffrementSecretsTotp` déchiffre chaque secret avec la clé dont l'identifiant figure dans sa valeur et
   le réécrit avec la clé courante (idempotent, une ligne par compte, journalisé) ; le nombre de secrets restant sous
   l'ancienne clé est écrit dans le journal ;
4. quand ce nombre est zéro, retirer `ADMIN_CLE_CHIFFREMENT_PRECEDENTE` au déploiement suivant.
Si une clé manque pour un secret (ni la courante ni la précédente), la connexion de ce compte est refusée et son
second facteur doit être réinitialisé (§7). Une clé compromise = rotation immédiate **et** réinitialisation de tous les
seconds facteurs : un secret déchiffré par un attaquant reste valable tant qu'il n'est pas remplacé.

### 4. Enrôlement imposé

À la première connexion d'un rôle interne (ou après une réinitialisation), l'écran propose le QR code et la saisie manuelle
du secret, puis exige un **premier code valide** avant d'activer le second facteur (`totp_active_le`). Tant que ce code n'est
pas confirmé, rien n'est activé et le secret en attente (chiffré dans `etape_connexion`) est jetable. Les comptes internes
existants (dont le compte de démonstration) y passent à leur prochaine connexion.

### 5. Codes de secours

Dix codes à usage unique (`XXXXX-XXXXX`, 50 bits chacun), générés à l'enrôlement, **affichés une seule fois** ; seule leur
empreinte SHA-256 est stockée (`code_secours`). L'utilisateur doit **cocher « Je les ai imprimés ou enregistrés »** pour
terminer l'enrôlement : tant que ce n'est pas fait (`codes_secours_confirmes_le`), la connexion suivante lui présente de
nouveaux codes (les précédents sont invalidés). Un code de secours remplace un code TOTP à la connexion, une seule fois ;
au-delà de 3 utilisations, un avertissement permanent invite à en générer de nouveaux (« Mon compte » : mot de passe et code
TOTP exigés ; les anciens sont invalidés). Un code de secours ne remplace **pas** le code TOTP pour la réauthentification
(§8) ni pour régénérer les codes.

### 6. Rôles et accès

| Rôle | Accès |
|---|---|
| `SERVICE_CLIENT` | commandes (confirmer, annuler, préparer, expédier, livrer, lien de suivi), clients, avis, SAV |
| `GESTIONNAIRE_STOCK` | produits, médias, stock, lecture du catalogue |
| `ADMIN` | tout, sauf utilisateurs et journal |
| `SUPER_ADMIN` | tout, dont utilisateurs, rôles, réinitialisation du second facteur et journal |

Avoirs, retours clients, mentions de facturation, paramètres du site, bannières et prix : `ADMIN` et `SUPER_ADMIN`
seulement (règle déjà posée dans `SecuriteConfig`, conservée). Un test parcourt **toutes** les routes `/api/admin/**`
et vérifie, pour chaque rôle, qu'elle répond comme la matrice l'annonce ; une route ajoutée sans règle explicite échoue.

### 7. Utilisateurs internes

Création **par invitation** d'un `SUPER_ADMIN` : l'invité reçoit un lien (jeton à usage unique, 48 h, empreinte stockée) vers
`/admin/invitation`, choisit son mot de passe (12 caractères minimum), puis s'enrôle (§4). Les comptes internes **ne
servent pas de comptes clients** : leur connexion passe par `/admin/connexion`. Un `SUPER_ADMIN` peut changer un rôle,
désactiver ou réactiver un compte (jamais le sien, jamais le dernier `SUPER_ADMIN` actif), et **réinitialiser le second
facteur** d'un compte (efface secret et codes, révoque les sessions, e-mail au titulaire). Un changement de rôle ou une
désactivation révoque les sessions du compte. **La réinitialisation ne peut pas se faire par variable d'environnement**
au démarrage : une variable oubliée dans un déploiement ouvrirait une porte permanente.

**Un seul `SUPER_ADMIN`** est autorisé (au démarrage de la boutique) ; un **avertissement permanent** s'affiche dans
l'administration tant qu'il n'y en a qu'un. Perte du téléphone *et* des codes de secours du seul `SUPER_ADMIN` : procédure
hors application, avec accès à la base, dans `docs/exploitation/runbook-acces-administrateur.md`.

### 8. Réauthentification (confirmation par un nouveau code TOTP)

Les actions les plus sensibles exigent un code TOTP **récent** : `POST /api/admin/reauthentification` avec un code TOTP
(pas un code de secours) ouvre une fenêtre de **10 minutes** liée à la session (table `reauthentification`, clé : famille du
jeton de rafraîchissement, revendication `sid`). Les routes concernées répondent **403** `REAUTHENTIFICATION_REQUISE` sans
cette fenêtre ; le frontend ouvre alors une fenêtre de saisie du code et rejoue la requête. Un code déjà utilisé pour se
connecter ne peut pas servir (anti-rejeu) : il faut attendre le pas suivant (30 s).

Routes concernées : utilisateurs (créer, changer le rôle, désactiver, réinitialiser le second facteur), mentions légales de
facturation et régime de TVA, réglages de paiement, **émission d'un avoir et retour client**, **marquage « remboursé »**
d'un paiement, et **tout export de données clients** (CSV, liste des clients). Pas les actions quotidiennes sur les
commandes. Une annotation `@ReauthentificationRequise` les marque et un test les recense.

### 9. Journal des actions d'administration

Table `journal_administration`, **immuable** : un déclencheur refuse tout `UPDATE` et ne laisse supprimer que les lignes de
plus de **3 ans** (purge nocturne, `Clock`). Une ligne : date, identifiant et e-mail de l'auteur (copie : le compte peut être
désactivé), rôle, action (`COMMANDE_CONFIRMEE`…), type et identifiant de l'objet, valeurs **avant** et **après** (JSON),
adresse IP, route, statut HTTP.

- Les services appellent `JournalAdministration.enregistrer(action, type, id, avant, après)` dans **la même transaction**
  que la modification : action annulée = ligne annulée.
- **Aucune donnée client dans les valeurs** : identifiants, statuts, montants, champs de configuration ; jamais nom,
  téléphone, e-mail ni adresse d'un client. Un test de non-régression recherche ces valeurs dans les lignes écrites.
- **Filet de sécurité** : un intercepteur observe toute requête d'écriture (`POST`, `PUT`, `PATCH`, `DELETE`) sur
  `/api/admin/**` qui réussit ; si aucun service n'a écrit de ligne détaillée, il écrit une ligne **générique**
  (`generique = true` : auteur, route, statut). Une action n'échappe donc jamais au journal, même ajoutée en oubliant l'appel.
  Un test vérifie que chaque route existante écrit sa ligne détaillée.
- Les tentatives refusées (403, réauthentification manquante) et les événements de sécurité (enrôlement, code de secours
  utilisé, second facteur réinitialisé, rôle changé) sont aussi journalisés.
- Lecture : `GET /api/admin/journal` (`SUPER_ADMIN`), filtres par auteur, action, objet et période, no-store.
  L'adresse IP est conservée en clair dans cette table (comptes de l'équipe, responsabilité individuelle) ; elle n'est jamais
  écrite dans les journaux de fichiers sans `MasqueJetons.adresse`.

### 10. Démonstration et CI

Le profil `demo` peut préenrôler le compte de démonstration avec un secret fixe (`DEMO_ADMIN_TOTP_SECRET`, Base32) pour que
Lighthouse et les scripts de capture calculent un code. **L'application refuse de démarrer en production** quand ce secret
est défini (même règle que la limitation du débit : indexation autorisée, ou profil `prod` sans `demo`) ; test dédié. Le
secret fixe n'est jamais valable que pour le compte de démonstration et est chiffré comme les autres.

### 11. Navigateur et réseau

`/admin` et `/admin/**` : `Cache-Control: no-store`, `X-Robots-Tag: noindex, nofollow`, `Referrer-Policy: no-referrer`
(nginx, comme `/compte`) ; `Disallow: /admin` dans `robots.txt` ; rendu côté navigateur seulement (`RenderMode.Client`). Les
pages d'invitation n'envoient pas le jeton en adresse de requête vers un tiers : il est lu puis renvoyé en POST (ADR 0003 §12).

## Conséquences

- Les tests qui se connectent avec un rôle interne passent par une aide qui enrôle le compte et calcule le code (`HorlogeReglable`).
- Chaque nouvelle route d'administration doit déclarer son rôle (matrice), sa réauthentification éventuelle et ses lignes de
  journal ; les tests de structure échouent sinon.
- Dépendance ajoutée : `com.google.zxing:core` (QR code, backend seulement).

## Limites et points ouverts

- Le jeton d'accès émis avant une désactivation reste valable jusqu'à 15 minutes (ADR 0003) ; les routes d'administration
  recontrôlent que le compte est actif et son rôle courant (une requête en base par appel d'écriture).
- Pas de clé WebAuthn ni de notification poussée : TOTP seulement. À reconsidérer si l'équipe grandit.
- Alerte e-mail à l'équipe à chaque nouvelle commande et notification WhatsApp : partie 6b (e-mail) et plus tard (WhatsApp).
