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
- La raison réelle n'apparaît que dans les journaux, avec l'identifiant du compte et jamais le mot de passe.

### 7. Connexion avant vérification de l'e-mail

Autorisée : la commande en invité est de toute façon possible, et bloquer la connexion ferait perdre
des clients qui ne trouvent pas l'e-mail. `emailVerifie` figure dans le JWT et dans le profil ;
la vérification sera exigée pour les actions sensibles (publier un avis, phase 8).

### 8. Verrouillage après échecs

5 échecs consécutifs (`echecs-avant-verrouillage`) verrouillent le compte 15 minutes
(`duree-verrouillage`). Une connexion réussie remet le compteur à zéro. Pendant le verrouillage,
même le bon mot de passe est refusé, avec la réponse générique.
Ce verrouillage par compte ne suffit pas contre une attaque sur de nombreux comptes : la limitation
de débit par IP et par e-mail (commit 8, Redis) le complète.

### 9. CGNAT et limitation de débit (commit 8)

Au Sénégal, les opérateurs mobiles placent de nombreux abonnés derrière une même adresse IP publique
(CGNAT). Une limite par IP trop basse bloquerait des clients légitimes au même moment : les limites par
IP sont donc larges (connexion 30/min, inscription 30/h, mot de passe oublié 10/h, rafraîchissement
30/min) et complétées par des limites **par e-mail** plus strictes (connexion 10/h, mot de passe oublié
3/h). Toutes les valeurs sont configurables.

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

Commit 7 : vérification de l'e-mail, mot de passe oublié et envoi réel des e-mails (Mailpit en dev).

## Conséquences

- Aucune session serveur : l'API peut avoir plusieurs instances derrière nginx.
- La table `jeton_rafraichissement` grossit d'une ligne par rafraîchissement : purge planifiée des
  jetons expirés (`supprimerExpiresAvant`) à brancher avec les tâches planifiées.
- Le frontend doit garder le jeton d'accès en mémoire, appeler `/rafraichir` au démarrage et à
  l'expiration, avec `credentials: 'include'`, et sérialiser les rafraîchissements entre onglets.
