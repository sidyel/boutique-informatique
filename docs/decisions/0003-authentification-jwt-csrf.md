# ADR 0003 — Authentification : JWT, jeton de rafraîchissement et protection CSRF

- **Statut :** accepté (phase 1, commit 6)
- **Date :** 2026-11-24

## Contexte

L'API est sans état et sert un frontend Angular rendu côté serveur, consulté surtout sur téléphone.
Il faut une session longue pour les clients (pas de reconnexion à chaque visite), courte pour l'équipe
interne, et une protection contre le vol de jeton et le CSRF sans alourdir le frontend.

## Décisions

### 1. Deux jetons

| | Jeton d'accès | Jeton de rafraîchissement |
|---|---|---|
| Format | JWT HS256 signé avec `JWT_SECRET` | 256 bits aléatoires (Base64 URL, 43 caractères) |
| Transport | en-tête `Authorization: Bearer` | cookie `jeton_rafraichissement` |
| Stockage côté client | mémoire du frontend (jamais `localStorage`) | cookie HttpOnly, illisible par JavaScript |
| Stockage serveur | aucun | empreinte SHA-256 seulement (table `jeton_rafraichissement`) |
| Durée | 15 minutes | 30 jours (CLIENT) / 12 heures (rôles internes) |

**Contenu du JWT** : `iss` (`boutique-informatique`), `sub` (identifiant), `iat`, `exp`, `email`,
`roles` (liste), `emailVerifie`. Rien d'autre : un JWT est signé, pas chiffré.

**Décodage** : HS256 uniquement (« alg: none » et tout autre algorithme refusés), signature, expiration
(tolérance de 30 s) et émetteur vérifiés. `JWT_SECRET` doit faire 32 octets au moins une fois décodé :
sinon l'application refuse de démarrer en production. En dev, une clé jetable est générée (avec un
avertissement dans les journaux).

**Conséquence acceptée** : un changement de rôle ou une désactivation de compte prend effet au
prochain rafraîchissement, donc au plus 15 minutes plus tard. Le rafraîchissement relit le compte
en base (rôle, `actif`, `emailVerifie`).

### 2. Cookie

```
Set-Cookie: jeton_rafraichissement=<jeton>; Path=/api/auth; Max-Age=2592000; HttpOnly; Secure; SameSite=Strict
```

- `Path=/api/auth` : le cookie n'est jamais envoyé au reste de l'API.
- `Secure` : désactivé uniquement dans le profil `dev` (HTTP local, tests depuis un téléphone du réseau).
- Pas de préfixe `__Host-` : il impose `Path=/`.
- Déconnexion ou session invalide : même cookie avec `Max-Age=0`.

### 3. Durées par rôle (configurables dans `application.yml`)

| Rôle | Durée | Type |
|---|---|---|
| CLIENT | 30 jours (`duree-session-client`) | **glissante** : chaque rafraîchissement repart pour 30 jours |
| SERVICE_CLIENT, GESTIONNAIRE_STOCK, ADMIN, SUPER_ADMIN | 12 heures (`duree-session-interne`) | **absolue** : le rafraîchissement ne dépasse jamais la fin de la session |

Un compte interne laissé ouvert sur un poste partagé expire donc en fin de journée de travail.

### 4. Rotation et détection de réutilisation

- Chaque `/rafraichir` marque le jeton présenté comme utilisé et en émet un nouveau dans la même
  **famille** (une famille = une connexion sur un appareil).
- Présenter un jeton **déjà utilisé** signifie que deux personnes détiennent la session (vol) :
  toute la famille est révoquée, l'incident est journalisé (`SÉCURITÉ : réutilisation…`) avec l'IP,
  la réponse est `401 SESSION_INVALIDE` et le cookie est effacé. Le voleur comme la victime doivent
  se reconnecter ; les sessions des autres appareils ne sont pas touchées.
- La révocation est validée même si la requête échoue (`noRollbackFor`).
- Deux rafraîchissements simultanés du même jeton sont sérialisés (`SELECT … FOR UPDATE`) : le second
  est traité comme une réutilisation. **Limite connue** : deux onglets qui rafraîchissent au même instant
  déconnectent l'utilisateur. Le frontend (phase 5) n'effectuera qu'un rafraîchissement à la fois
  (verrou Web Locks / BroadcastChannel entre onglets).

### 5. Protection CSRF : SameSite=Strict + vérification stricte de l'Origin

Seules `/api/auth/rafraichir` et `/api/auth/deconnexion` utilisent un cookie ; toutes les autres routes
exigent l'en-tête `Authorization`, qu'un site tiers ne peut pas poser. Pour ces deux routes :

1. **SameSite=Strict** : le navigateur n'envoie pas le cookie depuis un autre site. Mais « même site »
   n'est pas « même origine » : un sous-domaine compromis (`blog.boutique.sn`) reste le même site.
2. **Vérification de l'Origin** (`VerificationOrigineFiltre`) : l'en-tête `Origin`, que les navigateurs
   envoient sur tout POST et qu'une page ne peut pas falsifier, est **obligatoire** et doit être
   `URL_SITE` ou l'une des `CORS_ORIGINES` (comparaison exacte du schéma, de l'hôte et du port).
   Sans `Origin` ou avec une autre origine : `403 ORIGINE_REFUSEE`, et le jeton n'est pas consommé.
   Le filtre CORS refuse aussi en amont toute origine étrangère.
3. **Pas de jeton CSRF** : il ajouterait de l'état et un cookie lisible par JavaScript, et compliquerait
   le rendu serveur. L'OWASP admet la vérification d'origine combinée à SameSite comme défense sans état.
4. **Effet d'une attaque réussie malgré tout** : la réponse de `/rafraichir` (qui contient le jeton
   d'accès) n'est pas lisible par une page tierce (CORS). L'attaquant ne pourrait que provoquer une
   rotation ou une déconnexion.

Les outils sans navigateur (curl, applications) doivent donc envoyer `Origin` explicitement.

### 6. Anti-énumération des comptes

- **Inscription avec un e-mail déjà utilisé** : même statut (202) et même corps qu'une inscription
  normale ; aucun compte n'est créé ni modifié, et le titulaire reçoit un e-mail « vous avez déjà un
  compte » (lien vers la connexion et le mot de passe oublié). Le mot de passe est haché dans les deux
  cas pour un temps de réponse comparable. L'inscription ne connecte pas automatiquement.
- **Connexion** : même réponse `401 IDENTIFIANTS_INVALIDES` (« E-mail ou mot de passe incorrect. ») pour
  un e-mail inconnu, un mot de passe faux, un compte désactivé ou verrouillé. Un calcul BCrypt factice
  est fait quand l'e-mail est inconnu.
- **Temps de réponse** (relecture de sécurité, M1) : une connexion échouée exécute **les mêmes requêtes**
  que le compte existe ou non. Les échecs sont comptés par l'empreinte de l'adresse *saisie* (§8), pas par
  compte : un e-mail inconnu écrit la même ligne qu'un compte existant. Auparavant, seul un compte existant
  déclenchait une écriture (quelques millisecondes d'écart mesurables).
- La raison réelle n'apparaît que dans les journaux, avec l'identifiant du compte et jamais le mot de passe.

### 7. Connexion avant vérification de l'e-mail

Autorisée : la commande en invité est de toute façon possible, et bloquer la connexion ferait perdre
des clients qui ne trouvent pas l'e-mail. `emailVerifie` figure dans le JWT et dans le profil ;
la vérification sera exigée pour les actions sensibles (publier un avis, phase 8).

### 8. Verrouillage après échecs : par couple (e-mail, IP)

**Révisé après la relecture de sécurité (I1).** Le verrouillage par compte permettait à quiconque
connaissait l'e-mail d'une victime (un administrateur, par exemple) de l'empêcher de se connecter : 5 mots
de passe faux toutes les 15 minutes, depuis n'importe quelle IP.

- 5 échecs consécutifs (`echecs-avant-verrouillage`) **depuis une même IP** verrouillent le couple
  (e-mail, IP) pendant 15 minutes (`duree-verrouillage`). Pendant ce temps, depuis cette IP, même le bon
  mot de passe est refusé, avec la réponse générique. **Depuis une autre IP, le titulaire se connecte
  normalement.** Une connexion réussie remet à zéro le compteur de son IP.
- Table `echec_connexion` (migration V12) : empreinte SHA-256 de l'adresse saisie et normalisée, IP, nombre
  d'échecs, fin du verrouillage. Un seul `INSERT … ON CONFLICT` atomique par échec : des tentatives
  simultanées sont toutes comptées. Aucune adresse en clair. Les anciennes colonnes `echecs_connexion` et
  `verrouille_jusqua` de `utilisateur` sont supprimées.
- **Lien de déverrouillage** : quand un couple d'un compte existant vient d'être verrouillé, le titulaire
  reçoit l'e-mail « Connexion bloquée sur votre compte » avec un lien **à usage unique, valable 1 h**
  (`duree-jeton-deverrouillage`, table `jeton_deverrouillage`, empreinte seulement). `POST
  /api/auth/deverrouiller` lève les verrouillages de cette adresse **depuis toutes les IP**. **Un seul
  e-mail tant qu'un lien est encore valable** : un attaquant qui verrouille depuis de nombreuses IP ne
  remplit pas la boîte de la victime. L'e-mail invite aussi à changer de mot de passe si ce n'était pas elle.
- Choisir un nouveau mot de passe (§12) lève aussi tous les verrouillages et invalide les liens de
  déverrouillage.
- Purge quotidienne (`PurgeJetonsExpires`, 3 h 30) : compteurs inactifs depuis 7 jours, liens expirés.
- Limite assumée : un attaquant derrière le **même CGNAT** que la victime partage son IP publique et peut
  la gêner ; le lien de déverrouillage règle ce cas.
- Contre une attaque sur de nombreux comptes, la limitation du débit (§9) complète ce verrouillage.

### 9. CGNAT et limitation de débit (commit 8)

Au Sénégal, les opérateurs mobiles placent de nombreux abonnés derrière une même adresse IP publique
(CGNAT). Une limite par IP trop basse bloquerait des clients légitimes au même moment : les limites par
IP sont donc larges et complétées par des limites **par e-mail** plus strictes.

**Révisé après la relecture de sécurité (I1, M2).** Les quotas qui **bloquent** (429) portent sur l'IP ou
sur le couple (e-mail, IP) : un attaquant n'épuise que les siens. Le quota par e-mail seul ne bloque
jamais la victime.

| Route | Par IP (429) | Par (e-mail, IP) (429) | Par e-mail seul (jamais de 429) |
|---|---|---|---|
| connexion | 30 / minute | 10 / heure | 100 / heure : **alerte** |
| inscription | 30 / heure | — | — |
| mot de passe oublié | 10 / heure | 3 / heure | 10 / heure : **202 sans envoi** |
| renvoi de la vérification | 10 / heure | 3 / heure | 10 / heure : **202 sans envoi** |
| rafraîchissement | 30 / minute | — | — |

- **Alerte de connexion ciblée** : au-delà de 100 tentatives par heure sur une même adresse (donc depuis
  plusieurs IP), journal ERROR avec le marqueur `ALERTE_CONNEXION_CIBLEE` (une fois par fenêtre, empreinte
  tronquée de l'adresse, jamais l'adresse). La connexion n'est **pas** bloquée : le titulaire doit pouvoir
  se connecter depuis sa propre IP.
- **E-mails envoyés** (M2) : au-delà de 10 demandes par heure sur une adresse, la réponse reste `202` avec le
  même message, mais aucun e-mail ne part (journal WARN). Avant, un 429 par e-mail permettait à un
  attaquant d'empêcher la victime de demander un lien. Limite qui demeure : en épuisant ce quota depuis
  plusieurs IP, un attaquant peut retarder d'au plus 1 h la réception d'un lien par la victime ; il ne peut
  ni lire le lien ni bloquer la connexion.

- Toutes les valeurs sont dans `application.yml` (`boutique.limitation.*`), avec un interrupteur `actif`.
- **Mise en œuvre** : fenêtre fixe dans Redis (script Lua atomique : `INCR`, expiration à la première
  requête, temps restant), partagée par toutes les instances du backend. Aucune dépendance ajoutée.
- **Dépassement** : `429 TROP_DE_REQUETES` au format ProblemDetail, en-tête `Retry-After` (secondes)
  et propriété `reessayerDans`.
- Les règles sont vérifiées de la plus large à la plus fine (IP, puis (e-mail, IP), puis e-mail) : une
  requête refusée ne consomme pas les quotas suivants.
- **Clés Redis** : `limite:<règle>:<ip>`, `limite:<règle>:<SHA-256 de l'e-mail normalisé>:<ip>` ou
  `limite:<règle>:<SHA-256 de l'e-mail normalisé>` : aucune adresse e-mail en clair dans Redis.
- Toutes les limites s'appliquent que le compte existe ou non : ni le 429 ni l'absence d'envoi ne révèlent
  quelles adresses ont un compte.
- **Instance Redis principale** (relecture de sécurité, I2) : `maxmemory 256mb`, `volatile-ttl`. Tous les
  compteurs expirent : pleine, l'instance supprime ceux qui sont les plus proches de leur fin au lieu de
  refuser les écritures. Avec l'ancien réglage (`64mb`, `noeviction`), quelques centaines d'IP envoyant des
  e-mails inventés remplissaient l'instance, et le limiteur, qui laissait alors tout passer, était désactivé.
- **Redis en erreur** (arrêté, injoignable, ou plein malgré tout) : la requête est comptée par un
  **limiteur de secours en mémoire locale** (`LimiteurLocal` : même fenêtre fixe, 10 000 compteurs au
  plus, les moins récemment utilisés sont oubliés). Une panne de Redis n'empêche donc ni de se connecter, ni
  de limiter. Limite : propre à chaque instance (avec N instances, au plus N fois la règle).
- **Alerte** : chaque panne est journalisée au niveau ERROR avec un marqueur stable, `ALERTE_REDIS_PLEIN`
  (cause « OOM ») ou `ALERTE_REDIS_INDISPONIBLE`, au plus une fois toutes les 30 s par marqueur avec le
  nombre d'erreurs depuis le message précédent. À brancher sur la supervision (phase 9).

### 10. Mots de passe

- BCrypt coût 12.
- 10 caractères minimum, 72 octets maximum (limite de BCrypt ; un mot de passe accentué compte plus
  d'octets que de caractères). Pas de règle de composition imposée (NIST 800-63B).
- À la connexion, un mot de passe de plus de 72 octets donne la réponse générique, jamais une erreur 500.

### 11. Téléphone

Facultatif à l'inscription, accepté sous toute forme usuelle (« 77 123 45 67 ») et stocké au format
E.164 (`+221771234567`), validé par `@TelephoneSenegalais` et par une contrainte en base.

## Routes

| Route | Authentification | Réponse |
|---|---|---|
| `POST /api/auth/inscription` | aucune | 202 + message (identique si l'e-mail existe) |
| `POST /api/auth/connexion` | aucune | 200 + jeton d'accès + cookie ; 401 `IDENTIFIANTS_INVALIDES` |
| `POST /api/auth/rafraichir` | cookie + `Origin` | 200 + nouveau jeton + nouveau cookie ; 401 `SESSION_INVALIDE` ; 403 `ORIGINE_REFUSEE` |
| `POST /api/auth/deconnexion` | cookie + `Origin` | 204 + cookie effacé (même si le jeton est inconnu) |
| `GET /api/compte/**` | jeton d'accès | 401 `NON_AUTHENTIFIE` sans jeton valide |
| `/api/admin/**` | jeton d'accès d'un rôle interne | 401 sans jeton, 403 `ACCES_REFUSE` pour un CLIENT |
| `POST /api/auth/verification-email` | aucune (jeton reçu par e-mail) | 200 ; 400 `JETON_INVALIDE` |
| `POST /api/auth/renvoyer-verification` | aucune | 202, message identique dans tous les cas |
| `POST /api/auth/mot-de-passe-oublie` | aucune | 202, message identique dans tous les cas |
| `POST /api/auth/reinitialiser` | aucune (jeton reçu par e-mail) | 200 ; 400 `JETON_INVALIDE` ou `VALIDATION` |
| `POST /api/auth/deverrouiller` | aucune (jeton reçu par e-mail au verrouillage) | 200 ; 400 `JETON_INVALIDE` |

### 12. E-mails de vérification et de réinitialisation (commit 7)

- **Jetons** : 256 bits aléatoires, empreinte SHA-256 en base, **usage unique** ; vérification valable
  24 h, réinitialisation 1 h (configurables). Un nouvel envoi invalide les liens précédents. Lien inconnu,
  expiré ou déjà utilisé : même réponse `400 JETON_INVALIDE`.
- **Liens** : `URL_SITE/compte/verifier-email?jeton=…` et `URL_SITE/compte/nouveau-mot-de-passe?jeton=…`.
  Les pages du frontend (phase 5) renvoient le jeton en POST et doivent servir
  `Referrer-Policy: no-referrer`, pour que le jeton ne fuie pas vers un site tiers.
- **Réinitialisation** : nouveau mot de passe, verrouillage levé, adresse considérée comme vérifiée
  (le lien prouve l'accès à la boîte mail), **toutes les sessions révoquées**, et e-mail d'alerte
  « votre mot de passe a été modifié ».
- **Vérification** : le JWT en cours garde `emailVerifie=false` jusqu'au rafraîchissement suivant ;
  le frontend rafraîchit après la vérification.
- **Envoi** : après validation de la transaction (un rollback n'envoie rien) et en arrière-plan
  (`@Async`) : la réponse part tout de suite, avec la même durée que le compte existe ou non. Le jeton
  est créé dans une courte transaction validée avant l'échange SMTP ; 3 tentatives, puis abandon
  journalisé sans le contenu de l'e-mail.
- **Modèles** : Thymeleaf (`templates/emails/`), en français, styles en ligne et mise en page par tableaux.
- **Mailpit** : en dev (service de `infra/docker-compose.dev.yml`, http://localhost:8025) et dans les
  tests d'intégration, qui lisent les vrais e-mails reçus pour en extraire les jetons.
- **Limitation du débit** : voir §9 (par IP, par couple (e-mail, IP) et par e-mail). E-mail de déverrouillage : §8.

### 13. Adresse IP du client : impossible à falsifier

La limitation par IP n'a de sens que si l'IP ne peut pas être choisie par le client. Règles :

1. **nginx écrase les en-têtes au lieu de les allonger** :
   `proxy_set_header X-Forwarded-For $remote_addr;` et `proxy_set_header X-Real-IP $remote_addr;`.
   Avec `$proxy_add_x_forwarded_for`, la valeur envoyée par le client serait conservée et pourrait
   être prise pour son IP. Si un CDN est ajouté un jour devant nginx, utiliser le module `real_ip` avec
   la liste des adresses du CDN, jamais une confiance aveugle.
2. **Spring ne fait confiance aux en-têtes de proxy que s'ils viennent du réseau Docker interne** :
   `server.forward-headers-strategy: native` (valve RemoteIp de Tomcat) avec
   `server.tomcat.remoteip.internal-proxies` limité à 10.0.0.0/8, 172.16.0.0/12 et 192.168.0.0/16.
   Une requête venant d'une autre adresse (y compris 127.0.0.1) garde son adresse réelle, quel que soit
   le `X-Forwarded-For` envoyé. L'ancienne stratégie `framework` acceptait l'en-tête de n'importe qui.
   Surchargeable par `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` si le réseau Docker change.
3. **En production, le port du backend n'est jamais exposé** (aucun `ports:` dans docker-compose) :
   seuls nginx et le frontend SSR y accèdent, par le réseau Docker. Exposé, il permettrait de contourner
   nginx depuis une adresse du réseau interne et donc de choisir son `X-Forwarded-For`.
4. **Test** : `LimitationDebitTest` envoie, sur un vrai serveur Tomcat, 31 tentatives de connexion avec un
   `X-Forwarded-For` (et un `X-Real-IP`) différent à chaque fois : la 31ᵉ est bloquée (429). Un autre test
   vérifie que l'expression des proxies internes accepte les réseaux Docker et refuse 127.0.0.1 et les IP
   publiques.

Le frontend SSR appelle l'API depuis le réseau Docker : il est donc un proxy de confiance. Il ne doit
jamais transmettre à l'API un `X-Forwarded-For` construit à partir de la requête du navigateur (il
n'appelle d'ailleurs pas les routes d'authentification, que le navigateur appelle directement via nginx).

## Conséquences

- Aucune session serveur : l'API peut avoir plusieurs instances derrière nginx.
- La table `jeton_rafraichissement` grossit d'une ligne par rafraîchissement : purge planifiée des
  jetons expirés (`supprimerExpiresAvant`) à brancher avec les tâches planifiées.
- Le frontend doit garder le jeton d'accès en mémoire, appeler `/rafraichir` au démarrage et à
  l'expiration, avec `credentials: 'include'`, et sérialiser les rafraîchissements entre onglets.

## À traiter dans les phases suivantes (relecture de sécurité)

### Phase 6 : double authentification (TOTP) obligatoire pour les rôles internes

Un compte administrateur ne doit pas reposer sur un simple mot de passe : un mot de passe volé (hameçonnage,
réutilisé sur un autre site) donnerait les prix, les commandes et les données des clients.
- Second facteur **TOTP** (RFC 6238 : application d'authentification, code à 6 chiffres sur 30 s),
  **obligatoire** pour SERVICE_CLIENT, GESTIONNAIRE_STOCK, ADMIN et SUPER_ADMIN ; facultatif pour les clients.
- Connexion d'un rôle interne en deux temps : mot de passe correct → jeton intermédiaire court (5 min, sans
  aucun droit), puis code TOTP → session. Pas de jeton d'accès avant le second facteur.
- Enrôlement imposé à la première connexion (QR code, vérification d'un premier code) ; secret TOTP chiffré
  en base (clé hors de la base) ; codes de secours à usage unique (empreintes seulement).
- Codes TOTP : limitation du débit et verrouillage comme pour le mot de passe (§8, §9) ; un code déjà
  utilisé est refusé (rejeu).
- Réinitialisation du second facteur par un SUPER_ADMIN seulement, journalisée, avec e-mail au titulaire.

### Phase 9 : IPv6 (relecture M3)

Aujourd'hui nginx n'écoute qu'en IPv4. Dès qu'IPv6 sera activé, un client disposera d'un préfixe /64 entier
(2^64 adresses) et pourrait changer d'adresse à chaque essai. La clé de limitation par IP (Redis, secours
local, `echec_connexion`, zones `limit_req` de nginx) devra alors être le **préfixe /64** pour une adresse
IPv6 (l'adresse complète pour IPv4) : fonction de normalisation unique côté backend, `map` sur
`$binary_remote_addr` côté nginx. Tests avec deux adresses du même /64.

### Phase 9 : en-têtes de sécurité (relecture M6)

Avec HTTPS, ajouter dans nginx (`always`, sur toutes les réponses) :
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (puis `preload` après vérification de
  tous les sous-domaines) ;
- `Content-Security-Policy` complète, construite pour le SSR Angular (nonces des scripts en ligne) et les
  domaines réellement utilisés : Cloudinary (`res.cloudinary.com` pour les images et vidéos,
  `api.cloudinary.com` pour l'envoi des vidéos du back-office), Google Tag Manager / GA4, pixel Meta,
  `youtube-nocookie.com` (cadre chargé au clic). Démarrer en `Content-Security-Policy-Report-Only` ;
- `Cross-Origin-Opener-Policy: same-origin` ;
- garder `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options` (ou `frame-ancestors 'self'` dans
  la CSP) et `Permissions-Policy`.
