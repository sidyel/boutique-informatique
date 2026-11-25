# ADR 0005 — Médias : Cloudinary, fichiers contrôlés, jamais d'original diffusé

- **Statut** : accepté
- **Date** : 2026-11-25
- **Phase** : 2 (commit « stockage des médias, contrôle des fichiers et nettoyage des SVG ») ; complété au
  commit des images des paramètres et des bannières

## 1. Stockage derrière une interface

`StockageMedia` (implémentation `StockageCloudinary`) : envoi, suppression, puis envoi signé et vérification
des vidéos (commit des médias produit). Les tests utilisent `FauxStockageMedia` (bean `@Primary`) et la
clé `CLOUDINARY_URL` du `.env` local est vidée dans leur contexte : **aucun test n'appelle Cloudinary**.

- La clé vient de l'environnement (`CLOUDINARY_URL`), n'est jamais journalisée ni recopiée dans un message.
  Sans clé, l'application démarre ; seuls les envois échouent (`503 STOCKAGE_INDISPONIBLE`).
- Un dossier par environnement : `boutique.medias.dossier-racine` = `dev/boutique` en développement,
  `boutique` en production. Une erreur en développement ne touche jamais les fichiers du site réel.
- Les appels à Cloudinary sont faits **hors transaction** de base de données ; si l'écriture en base échoue
  après un envoi (ou si un envoi échoue au milieu d'un lot), les fichiers déjà envoyés sont supprimés
  (compensation). Une suppression en base est suivie de la suppression sur Cloudinary **après** la validation,
  en tâche de fond : une panne de Cloudinary ne fait pas attendre l'administrateur.
- **Fichiers orphelins** : si la suppression échoue après 3 essais (`boutique.medias.suppression`), l'idPublic
  est enregistré dans la table `media_orphelin` (type, origine, dernière erreur, nombre d'essais), pas
  seulement dans les journaux. **Une tâche planifiée de nettoyage sera ajoutée en phase 9** : elle
  retentera la suppression des orphelins et retirera de la table ceux qui sont supprimés.
- Les médias sans idPublic (images de démonstration servies par le site) se réordonnent, deviennent
  principaux et se suppriment sans aucun appel au stockage.

## 1 bis. Règles des médias d'un produit

- **15 images au plus** par produit (`boutique.medias.images.max-par-produit`) : au-delà, `422 TROP_D_IMAGES`,
  vérifié avant l'envoi puis à nouveau sous verrou du produit (deux envois simultanés ne dépassent pas la
  limite à eux deux).
- **Texte alternatif obligatoire**, de 5 à 150 caractères, refusé s'il ressemble à un nom de fichier
  (`IMG_1234.jpg`, `DSC0001`, `photo.png`, `PXL_…`, captures d'écran datées, identifiants techniques). Les
  réponses du back-office proposent une suggestion « nom du produit – libellé de la variante » que le
  formulaire préremplit (phase 6).
- **Supprimer une variante ne supprime jamais ses photos** : `media_produit.variante_id` est en
  `ON DELETE SET NULL` (migration V10) ; elles restent rattachées au produit et l'équipe décide de leur sort.
- La dernière image d'un produit publié ne peut pas être supprimée (`409 DERNIERE_IMAGE`). Retirer l'image
  principale promeut la suivante.

## 1 ter. Vidéos

**Vidéos envoyées** (MP4 ou WebM, 100 Mo, une vidéo au plus par produit, vidéo YouTube comprise) : le fichier
va directement du navigateur à Cloudinary, jamais par le backend.

1. `POST /api/admin/produits/{id}/medias/videos/signature` : le backend génère l'idPublic dans le dossier
   **de ce produit** (`<racine>/produits/<id>/videos/<slug>-<aléatoire>`) et signe uniquement : cet idPublic,
   ce dossier, `overwrite=false`, `allowed_formats=mp4,webm` et l'horodatage. L'URL d'envoi fixe la ressource
   `video`. Le secret de l'API ne sort jamais du backend. **Ce que la signature ne peut pas couvrir** : voir
   « Limites de la signature » ci-dessous.
2. **Validité courte** : Cloudinary accepte une signature pendant 1 h après son horodatage, sans réglage
   possible. Le backend **antidate** l'horodatage (`maintenant − 1 h + validite-signature`) : l'envoi doit
   commencer dans les **50 minutes** par défaut, pour les connexions mobiles lentes
   (`boutique.medias.videos.validite-signature`, entre 1 et 50 min). Une vidéo produit devrait durer **20 à
   40 secondes et peser moins de 30 Mo** (README).
3. **Chaque signature est enregistrée** (`signature_envoi_video` : produit, idPublic, dossier, expiration,
   date d'utilisation).
4. `POST …/medias/videos` déclare la vidéo. Elle est refusée si l'idPublic n'a pas été signé **pour ce
   produit et dans son dossier** (`VIDEO_AUTRE_PRODUIT` : pas de réutilisation de l'idPublic d'un autre
   produit, qui n'est pas supprimé pour autant), si elle est déjà déclarée, ou au-delà de l'expiration
   augmentée de `delai-declaration` (1 h, pour les connexions lentes).
5. La vidéo est relue sur Cloudinary (présente, en ressource vidéo, format et taille autorisés) ; sinon elle
   est supprimée et refusée (`VIDEO_INVALIDE`).
6. URL de la vidéo transformée (`f_auto,q_auto`) ; **image d'aperçu générée par Cloudinary** (`so_auto` : image
   choisie automatiquement, puis `f_auto,q_auto`), en URL transformée comme les images.

**Limites de la signature (relecture de sécurité, M8 ; vérifié dans la documentation officielle de Cloudinary,
novembre 2026).**
- *Type de ressource* : `resource_type` n'entre **jamais** dans la chaîne signée (« `file`, `cloud_name`,
  `resource_type`, and `api_key` are never part of the string to sign », page *Authentication signatures*) :
  il est dans le chemin de l'URL d'envoi (`/v1_1/<cloud>/<type>/upload`). Une signature peut donc être
  présentée à `image/upload` ou `raw/upload` avec le même idPublic. `allowed_formats` reste signé, mais la
  documentation ne précise pas comment il s'applique aux fichiers `raw` (contrôle de l'extension seulement ?).
- *Taille* : **aucun paramètre d'envoi ne limite la taille** du fichier ; seules les limites du forfait du
  compte s'appliquent (et 100 Mo par requête sans découpage en morceaux). Un plafond ne peut donc pas être
  signé.
- *Conséquence* : quiconque obtient une signature (journaux, extension de navigateur sur le poste d'un
  gestionnaire) peut, pendant sa validité (50 min au plus), envoyer **un** fichier sous cet idPublic exact,
  sans écraser quoi que ce soit (`overwrite=false`), dans le type de ressource de son choix, jusqu'au plafond
  du compte. Ce fichier n'est **jamais diffusé par le site** : la déclaration relit la ressource `video`
  seulement, refuse et supprime un format ou une taille non autorisés (étape 5). Le risque se limite à
  l'occupation du stockage.
- *Réduction du risque déjà en place* : signature émise seulement aux rôles médias, liée à un idPublic
  aléatoire et à un produit, courte, enregistrée (`signature_envoi_video`), une seule vidéo déclarable.

**Vidéos envoyées mais jamais déclarées** (navigateur fermé en cours de route), et fichiers envoyés avec une
signature dans un autre type de ressource : présents sur Cloudinary, absents de `media_produit`. On les
retrouve par leurs signatures expirées et jamais utilisées. **La tâche de nettoyage de la phase 9 traitera
aussi ce cas**, en plus de `media_orphelin` :
- pour chaque signature expirée et jamais utilisée, supprimer l'idPublic dans **les trois types de ressources**
  (`video`, `image` et `raw`), puis marquer ou retirer la signature ;
- parcourir aussi, par l'API d'administration, **tous les types de ressources** du dossier
  `<racine>/produits/*/videos/` et supprimer tout fichier qui n'est ni une vidéo déclarée de `media_produit`
  ni une signature encore valide (cas d'un envoi `image` ou `raw` sous l'idPublic d'une vidéo déclarée) ;
- journaliser au niveau WARN tout fichier `image` ou `raw` trouvé dans un dossier vidéo : c'est le signe
  d'une signature détournée.

**Vidéos YouTube** (`POST …/medias/youtube`, lien `watch`, `youtu.be`, `shorts`, `embed`) :
- vérification par **oEmbed** avec un délai court (`boutique.medias.youtube.delai`, 3 s). Une vidéo privée,
  supprimée ou dont l'auteur a désactivé l'intégration est refusée en 422 avec un message clair
  (`YOUTUBE_NON_INTEGRABLE`, `YOUTUBE_INTROUVABLE`). Si YouTube ne répond pas : **502**
  `YOUTUBE_INDISPONIBLE` ;
- miniature (`i.ytimg.com/vi/<id>/hqdefault.jpg`, adresse construite par le code, jamais reprise d'une
  réponse tierce) copiée sur Cloudinary et servie en URL transformée : afficher la fiche ne contacte pas
  YouTube ;
- **phase 3** : le lecteur utilisera `youtube-nocookie.com` et ne sera chargé **qu'au clic** sur la
  miniature. Rien n'est chargé depuis YouTube avant ce clic (performances, vie privée, consentement).

## 1 bis (affichage). Photos produit : cadre carré, jamais recadrées

- Sur les cartes et la fiche, la photo est affichée dans un **cadre carré (1:1) à fond blanc**, en
  `object-fit: contain` : l'image n'est **jamais recadrée** (un produit coupé trompe le client).
- **Format conseillé à l'envoi : carré, fond blanc, 1 200 × 1 200 px au moins.** C'est aussi le format le
  mieux accepté par les catalogues Meta (Advantage+ catalogue, 1:1 recommandé) et Google Merchant Center
  (fond uni, produit entier) : la même photo sert au site et aux flux publicitaires.
- Une photo non carrée reste acceptée (marges blanches), mais l'équipe est invitée à la recadrer avant envoi.
  Aucune transformation Cloudinary de recadrage n'est appliquée aux photos produit (`c_fill` interdit) ; le
  chargeur d'images de la phase 3 n'ajoute qu'une largeur (`w_…`).

## 1 quater. Identité du site et bannières

| Image | Règles | Ce que renvoie `/api/parametres` |
|---|---|---|
| Logo, logo sombre | JPEG, PNG, WebP ou SVG nettoyé (§5), 16 px au moins | URL transformée |
| Favicon | **carré**, 512 × 512 px au moins, pas de SVG (déclinaisons raster) | `favicons` : 32 × 32 (onglet), 180 × 180 (apple-touch-icon), 192 × 192 et 512 × 512 (manifeste web), en PNG (`c_fill,w_…,h_…,f_png,q_auto`) |
| Image de partage | **600 × 315 px au moins** (sinon Facebook et WhatsApp montrent une petite vignette), 1200 × 630 conseillé | `imagePartageUrl` recadrée en 1200 × 630 (`c_fill,g_auto,w_1200,h_630,f_jpg,q_auto`) |

Les déclinaisons **remplacent** la transformation de diffusion de l'URL enregistrée (jamais d'empilement,
version et idPublic intacts) ; une URL d'image de démonstration est renvoyée telle quelle
(`TransformationsImages`). Remplacer une image vide les caches publics (dont `/api/parametres`, testé) et
supprime l'ancien fichier après la validation.

**Bannières** (`POST /api/admin/bannieres/images`, `format` = `ORDINATEUR` ou `MOBILE`) : 600 px au moins sur le
plus petit côté. Les proportions **ne bloquent pas** l'envoi ; la réponse porte des avertissements :

| Format | Conseillé | Avertissement si |
|---|---|---|
| Ordinateur | 1920 × 600 px (3,2:1) | proportions à plus de 25 % de 3,2:1, ou moins de 1440 px de large |
| Mobile | 800 × 1000 px (4:5) | proportions à plus de 25 % de 4:5, ou moins de 640 px de large |

L'image envoyée est rattachée à une bannière par le formulaire du back-office (phase 6) ; une image envoyée
mais jamais rattachée sera repérée par la tâche de nettoyage de la phase 9 (fichiers de
`<racine>/bannieres` absents de la table `banniere`).

## 2. Contrôle des fichiers envoyés (`VerificationFichiers`)

1. **Poids** : 5 Mo par image (multipart Spring 5 Mo par fichier, 50 Mo par requête ; nginx 55 Mo).
   Les vidéos (100 Mo) vont directement du navigateur à Cloudinary par envoi signé, jamais par le backend.
2. **Type réel** détecté par Tika sur les premiers octets, jamais d'après le nom ou le `Content-Type` envoyés :
   JPEG, PNG, WebP ; SVG pour le logo et le logo sombre uniquement.
3. **Dimensions lues dans les en-têtes** (`SOF` JPEG, `IHDR` PNG, `VP8X` WebP) par les analyseurs de Tika
   (`tika-parser-image-module`, sans les décodeurs TIFF/JPEG 2000/JBIG2, exclus) : l'image n'est **jamais
   décodée** dans le backend.
   - plus de **8 000 px** de côté : refus (`IMAGE_TROP_GRANDE`). Protection contre les « bombes de
     décompression » : un PNG de 9 000 × 9 000 px tout noir pèse quelques dizaines de Ko et 81 Mo décodé
     (testé : refusé en lisant l'en-tête, sans allocation notable) ;
   - photo produit de moins de **800 px** sur le plus petit côté : refus (`IMAGE_TROP_PETITE`), pour un zoom
     net sur la fiche et les minimums de Google Merchant. Bannières et image de partage : 600 px.
   - limites configurables : `boutique.medias.images.cote-max`, `boutique.medias.images.cote-min.<usage>`.

## 3. idPublic : jamais le nom du fichier d'origine

`<racine>/<dossier>/<slug>-<8 caractères aléatoires>`, par exemple
`boutique/produits/asus-vivobook-15-x1504va-k3f9q2mz`. Le nom d'origine peut contenir des caractères spéciaux
ou des données personnelles (« photo-awa-diop.jpg ») qui finiraient dans une URL publique et dans les flux
publicitaires : il n'est ni utilisé ni transmis (`use_filename: false`, envoi des octets seuls). Le suffixe
aléatoire (`SecureRandom`) évite les collisions et empêche de deviner les autres adresses.

## 4. Toute image diffusée passe par une URL transformée

Les originaux peuvent contenir des métadonnées EXIF (coordonnées GPS du téléphone qui a pris la photo,
modèle, date). **Aucune URL d'original ne sort du site** : pages, JSON-LD, Open Graph et flux Google/Meta
(phase 7) utilisent une URL Cloudinary transformée, au minimum `f_auto,q_auto`, ce qui produit un fichier
réencodé sans métadonnées. La base garde l'idPublic et l'URL de diffusion déjà transformée, avec sa version
(`…/image/upload/f_auto,q_auto/v<version>/<idPublic>.<format>`).

**Exception : les SVG** (logo, logo sombre) ne passent jamais par `f_auto`, qui les convertit en image
matricielle à leur taille d'origine (constaté lors de l'essai réel : logo livré en WebP de 120 × 40 px, flou).
Ils sont servis en vectoriel avec `fl_sanitize` (second nettoyage par Cloudinary à la diffusion) ; un SVG n'a
pas de métadonnées EXIF à retirer.

**Pour la phase 3 (chargeur `NgOptimizedImage`)** : les URL en base contiennent **déjà** `f_auto,q_auto`. Le
chargeur Angular doit **compléter** cette transformation avec la largeur demandée (`f_auto,q_auto,w_640`), sans
la dupliquer (jamais `f_auto,q_auto/f_auto,q_auto,w_640` ni deux `w_`). Les images de démonstration
(`/images/demo/….svg`, servies par le site, sans transformation) restent inchangées. Le chargeur aura un test
sur une URL enregistrée (largeur ajoutée une seule fois, version et idPublic intacts) et sur une URL d'image de
démonstration (renvoyée telle quelle).

## 5. SVG : nettoyés à l'envoi, affichés uniquement par `<img src>`

Le SVG est accepté pour le logo et le logo sombre (le vectoriel reste net sur tous les écrans). Le favicon
est une image raster : ses déclinaisons PNG en sont tirées.

- **Nettoyage par liste blanche** (`NettoyeurSvg`) : le document est reconstruit avec les seuls éléments et
  attributs autorisés. Retirés (tests dans `NettoyeurSvgTest`) : `<script>`, `<foreignObject>`, `<a>`,
  attributs `on*`, **animations SMIL** (`<animate>`, `<set>`, `<animateTransform>`, `<animateMotion>`,
  `<discard>` : elles pourraient réécrire un `href` en `javascript:` ou poser un `on*`), `<use>` et autres
  `href` vers une **ressource externe** (seul `#id` est accepté), `href`/`xlink:href` en `data:` sauf
  `data:image/png|jpeg|webp;base64`, **CSS** avec `@import`, `url()` externe, échappements `\`, `expression()`,
  commentaires (dont les commentaires conditionnels), instructions de traitement, éléments et attributs
  d'**espaces de noms inattendus** (XHTML, Inkscape, RDF…), `xml:base`.
- **Refusés** : toute **DTD**, donc toute entité (XXE, « billion laughs », DTD externe), XML mal formé, racine
  autre que `<svg>` dans l'espace de noms SVG, profondeur > 64 ou plus de 20 000 éléments, fichier > 512 Ko.
- **Le logo SVG est toujours affiché via `<img src="…">`, jamais injecté en ligne dans le HTML** (pas
  d'`innerHTML`, pas de `[innerHTML]` Angular, pas de `bypassSecurityTrustHtml`, pas d'inclusion du fichier
  dans un template SSR). Dans une balise `<img>`, les navigateurs n'exécutent aucun script et ne chargent
  aucune ressource externe d'un SVG : c'est la seconde barrière si le nettoyage laissait passer quelque chose.
  Même règle pour `<link rel="icon">` (favicon) et `og:image` (les réseaux sociaux n'acceptent de toute façon
  pas le SVG : l'image de partage est en JPEG/PNG/WebP).
