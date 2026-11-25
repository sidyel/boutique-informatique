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
  après un envoi, le fichier envoyé est supprimé (compensation). Une suppression en base est suivie de la
  suppression sur Cloudinary **après** la validation.

## 2. Contrôle des fichiers envoyés (`VerificationFichiers`)

1. **Poids** : 5 Mo par image (multipart Spring 5 Mo par fichier, 50 Mo par requête ; nginx 55 Mo).
   Les vidéos (100 Mo) vont directement du navigateur à Cloudinary par envoi signé, jamais par le backend.
2. **Type réel** détecté par Tika sur les premiers octets, jamais d'après le nom ou le `Content-Type` envoyés :
   JPEG, PNG, WebP ; SVG pour le logo et le favicon uniquement.
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
réencodé sans métadonnées. La base garde l'idPublic ; les URL sont construites à la lecture.

## 5. SVG : nettoyés à l'envoi, affichés uniquement par `<img src>`

Le SVG est accepté pour le logo, le logo sombre et le favicon (le vectoriel reste net sur tous les écrans).

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
