# Cahier des charges — Boutique en ligne de matériel informatique

> À placer dans `docs/CAHIER_DES_CHARGES.md`. C'est la référence fonctionnelle du projet.
> Les conventions de code (tout en français, etc.) sont dans `CLAUDE.md` à la racine.

---

## 1. Objectifs

1. Vendre du matériel informatique en ligne, avec une expérience comparable aux grands sites spécialisés (LDLC, Materiel.net, Newegg), adaptée au marché sénégalais.
2. **Être très bien référencé sur Google** grâce au SEO technique, aux données structurées et au contenu.
3. **Rentabiliser les campagnes Meta Ads et Google Ads** : suivi fiable des conversions (navigateur et serveur), flux produits pour les catalogues publicitaires, et attribution des ventes par source.
4. Permettre à l'administrateur de tout gérer sans développeur : produits, médias, logo, bannières, promotions, identifiants de suivi.

---

## 2. Modèle de données (entités en français)

Les montants sont des `Long` en FCFA. Toutes les entités ont `id`, `dateCreation` et `dateModification` (audit JPA).

### Catalogue
- **Categorie** : nom, slug, parent (arborescence), description, imageUrl (tuile d'accueil), banniereUrl (en-tête de la page catégorie), position, actif, metaTitre, metaDescription, texteSeo (texte éditorial en bas de page), categorieGoogle (`google_product_category`).
- **Marque** : nom, slug, logoUrl, description, metaTitre, metaDescription.
- **Produit** : nom, slug, marque, categorie, descriptionCourte, descriptionLongue (HTML nettoyé), etat (`NEUF`, `RECONDITIONNE`, `OCCASION`), garantieMois, gtin (EAN/UPC), mpn (référence fabricant), actif, misEnAvant, metaTitre, metaDescription, noteMoyenne, nombreAvis.
- **VarianteProduit** : produit, sku (unique, sert d'identifiant dans les flux et le suivi publicitaire), libelle (ex. « 16 Go / 512 Go / Gris »), attributs (couleur, capacité…), prix, prixBarre (facultatif), stock, seuilAlerte, poidsGrammes, actif.
- **MediaProduit** : produit, variante (facultatif, pour les photos propres à une couleur), type (`IMAGE`, `VIDEO_FICHIER`, `VIDEO_YOUTUBE`), url, idPublic (Cloudinary), urlMiniature (aperçu vidéo), texteAlternatif, position, principal (booléen).
  - Au moins une image par produit et une seule image principale. Au plus une vidéo par produit (règle paramétrable).
- **DefinitionCaracteristique** : categorie, cle (ex. `processeur_modele`, `ram_go`, `socket`), libelle, unite (Go, Hz, W, pouces), type (`TEXTE`, `NOMBRE`, `BOOLEEN`, `LISTE`), groupe (« Processeur », « Écran », « Connectique »), filtrable, ordre.
- **ValeurCaracteristique** : produit (ou variante), definition, valeurTexte, valeurNombre.
  - Ce modèle sert à la fois au **tableau de caractéristiques** de la fiche produit, aux **filtres dynamiques par catégorie**, au **comparateur** et à la **compatibilité** du configurateur de PC.
- **HistoriqueSlug** : typeEntite (`PRODUIT`, `CATEGORIE`, `MARQUE`, `ARTICLE`), ancienSlug, entiteId. Il permet de rediriger en 301 quand un slug change.

### Clients et comptes
- **Utilisateur** : email (unique), motDePasseHash (BCrypt), prenom, nom, telephone, role, actif, emailVerifie, consentementMarketing, dateConsentement.
- **Role** (enum) : `CLIENT`, `SERVICE_CLIENT`, `GESTIONNAIRE_STOCK`, `ADMIN`, `SUPER_ADMIN`.
- **Adresse** : utilisateur, libelle (« Maison », « Bureau »), nomComplet, telephone, ville, quartier, adresseLigne, pointDeRepere (très utile à Dakar), parDefaut.
- **JetonRafraichissement**, **JetonVerificationEmail** et **JetonReinitialisationMotDePasse**.

### Panier et commandes
- **Panier** : utilisateur (nullable), jetonInvite (cookie pour les visiteurs non connectés), lignes. Le panier invité est **fusionné** avec celui du compte à la connexion.
- **LignePanier** : panier, variante, quantite.
- **Commande** :
  - numero lisible (`CMD-2026-000123`), utilisateur (nullable si commande en invité), emailClient, telephoneClient ;
  - adresseLivraison (**copie figée**, pas une référence), zoneLivraison, modeLivraison (`DOMICILE`, `RETRAIT_MAGASIN`) ;
  - sousTotal, fraisLivraison, remise, total, codePromo, modePaiement, statut ;
  - **attribution marketing** : utmSource, utmMedium, utmCampagne, utmContenu, utmTerme, gclid, fbclid, fbp, fbc, pageArrivee, referent, adresseIp, userAgent.
- **StatutCommande** : `EN_ATTENTE_PAIEMENT`, `CONFIRMEE` (paiement à la livraison), `PAYEE`, `EN_PREPARATION`, `EXPEDIEE`, `LIVREE`, `ANNULEE`, `REMBOURSEE`.
- **LigneCommande** : commande, variante, copies figées (nomProduit, sku, libelleVariante, imageUrl), prixUnitaire, quantite, totalLigne.
- **HistoriqueStatutCommande** : commande, ancienStatut, nouveauStatut, auteur, commentaire, date.
- **Paiement** : commande, fournisseur (`STRIPE`, `PAYDUNYA`, `CINETPAY`, `WAVE`, `ORANGE_MONEY`, `A_LA_LIVRAISON`), referenceExterne, montant, statut, donneesBrutes (JSON du webhook), date.
- **ZoneLivraison** : nom (« Dakar Plateau », « Banlieue », « Thiès »…), villes/quartiers couverts, tarif, delaiJours, actif.

### Marketing et contenu
- **CodePromo** : code, type (`POURCENTAGE`, `MONTANT_FIXE`, `LIVRAISON_OFFERTE`), valeur, montantMinimum, dateDebut, dateFin, utilisationsMax, utilisationsParClient, categoriesCiblees, actif.
- **Banniere** : emplacement (`ACCUEIL_CARROUSEL`, `ACCUEIL_PROMO`, `ENTETE_CATEGORIE`, `TUNNEL_CONFIANCE`, `PIED_DE_PAGE`), imageOrdinateurUrl, imageMobileUrl, titre, sousTitre, texteBouton, lien, position, actif, dateDebut, dateFin.
- **ParametresSite** (une seule ligne) :
  - nomSite, logoUrl, logoSombreUrl, faviconUrl, imagePartageUrl (Open Graph par défaut) ;
  - telephone, whatsapp, email, adresse, horaires, liensReseauxSociaux ;
  - **identifiants de suivi** modifiables sans redéploiement : idGoogleTagManager, idGa4, idGoogleAds, libelleConversionAchat, idPixelMeta, codeVerificationGoogle, codeVerificationMeta.
  - (Les **secrets** comme le jeton de l'API Conversions de Meta restent dans les variables d'environnement, jamais en base.)
- **Article** (blog et guides d'achat) : titre, slug, extrait, contenu, imageCouverture, auteur, categorie, produitsLies, publie, datePublication, metaTitre, metaDescription.
- **PageStatique** : slug, titre, contenu (CGV, mentions légales, politique de confidentialité, livraison, retours et garantie, à propos, contact).
- **Collection** : pages d'atterrissage pour les campagnes (`/promo/black-friday`), avec titre, slug, banniere, produits, indexable (oui/non), dateDebut, dateFin.

### Après-vente et engagement
- **Avis** : produit, utilisateur, note (1 à 5), titre, commentaire, photos, achatVerifie, statut (`EN_ATTENTE`, `APPROUVE`, `REJETE`), reponseBoutique.
- **ElementListeSouhaits** : utilisateur, variante.
- **AlerteProduit** : email, variante, type (`RETOUR_EN_STOCK`, `BAISSE_DE_PRIX`), prixCible, envoyee.
- **DemandeSav** : commande, ligneCommande, motif (`DEFECTUEUX`, `NON_CONFORME`, `RETRACTATION`, `GARANTIE`), description, photos, statut, messages.
- **MouvementStock** : variante, quantite, type (`ENTREE`, `SORTIE_VENTE`, `AJUSTEMENT`, `RETOUR`), motif, auteur.
- **JournalConversion** : commande, plateforme (`META`, `GOOGLE_ADS`), evenement, idEvenement, statut, reponse, nombreTentatives. Il sert à suivre les envois côté serveur et à éviter les doublons.

---

## 3. Fonctionnalités côté client

### Navigation et catalogue
- Méga-menu par catégories, avec les marques mises en avant.
- **Page d'accueil** : carrousel de bannières (images distinctes pour ordinateur et mobile), tuiles de catégories, nouveautés, meilleures ventes, promotions avec compte à rebours, marques, bandeau de confiance (livraison, garantie, paiement mobile, SAV).
- **Page catégorie** :
  - filtres **dynamiques** issus des caractéristiques filtrables (prix, marque, état, disponibilité + caractéristiques propres à la catégorie) ;
  - tri, pagination ;
  - texte SEO éditorial en bas de page.
- **Recherche** : suggestions pendant la frappe (produits, catégories, marques), tolérance aux fautes, page de résultats avec filtres. Au départ, recherche plein texte PostgreSQL (`tsvector` + `pg_trgm`), avec une interface prévue pour passer à Elasticsearch plus tard.
- Pages marque (`/marque/asus`).

### Fiche produit
- Galerie : image principale, miniatures, vidéo affichée comme une miniature avec une icône lecture, zoom au survol sur ordinateur, balayage sur mobile, plein écran.
- Choix de la variante : met à jour le prix, le stock, le SKU, les photos et l'URL (paramètre `?variante=`), sans créer de contenu dupliqué (l'URL canonique reste celle du produit).
- Prix, prix barré, pourcentage de réduction, indicateur de stock, garantie, état.
- **Tableau de caractéristiques** regroupé par sections, avec les valeurs en police à chasse fixe.
- Description longue, avis clients (photos, « achat vérifié »), réponse de la boutique.
- « Souvent achetés ensemble », produits similaires, produits récemment consultés.
- Boutons : ajouter au panier, acheter maintenant, **commander par WhatsApp** (message prérempli avec le produit), liste de souhaits, comparer, alerte de stock ou de prix.
- Barre d'achat collante sur mobile.

### Fonctionnalités propres à l'informatique
- **Comparateur** : 2 à 4 produits de la même catégorie, différences mises en évidence.
- **Configurateur de PC** (phase 3) : choix étape par étape (processeur, carte mère, RAM, carte graphique, stockage, alimentation, boîtier, refroidissement) avec vérification de compatibilité (socket, type de mémoire, format, puissance totale estimée par rapport à l'alimentation), puis ajout de toute la configuration au panier.
- **Guides d'achat** : articles reliés aux produits (contenu utile au SEO).

### Panier et tunnel de commande
- Panier latéral, panier complet, panier invité conservé, mise à jour des quantités avec contrôle du stock.
- Code promo, estimation des frais de livraison selon la zone.
- Tunnel court (une page avec étapes repliables) : coordonnées, puis livraison, puis paiement, puis confirmation. **Commande en invité possible.**
- Paiement :
  - mobile money (**Wave, Orange Money, Free Money**) via un agrégateur (PayDunya, CinetPay ou PayTech) ;
  - carte bancaire (Stripe ou l'agrégateur) ;
  - **paiement à la livraison**.
  - Prévoir une interface `FournisseurPaiement` pour pouvoir ajouter ou changer d'agrégateur.
- **Le statut payé est confirmé uniquement par le webhook vérifié du fournisseur**, jamais par le frontend.
- Page de confirmation, e-mail de confirmation, facture PDF, SMS ou WhatsApp en option.

### Compte client
- Inscription, connexion, vérification de l'e-mail, mot de passe oublié, connexion Google en option.
- Tableau de bord : commandes avec suivi et historique des statuts, factures, adresses, liste de souhaits, alertes, avis, demandes SAV, préférences marketing (consentement), suppression du compte.

---

## 4. Back-office (`/admin`, protégé par rôles)

- **Tableau de bord** : chiffre d'affaires (jour, semaine, mois), nombre de commandes, panier moyen, taux de conversion, meilleures ventes, stocks faibles, **ventes par source (utm_source / gclid / fbclid)**.
- **Produits** : liste filtrable, création et modification (variantes, caractéristiques selon la catégorie, SEO avec aperçu Google, médias), duplication, activation et désactivation, import et export CSV.
- **Médias** :
  - envoi multiple par glisser-déposer avec progression, réorganisation par glisser-déposer, choix de la photo principale, texte alternatif obligatoire ;
  - vidéo par fichier ou lien YouTube ;
  - **envoi direct vers Cloudinary** avec signature générée par le backend pour les vidéos lourdes.
- **Catégories, marques, caractéristiques** (définitions par catégorie).
- **Stock** : mouvements, ajustements, alertes de seuil.
- **Commandes** : changement de statut (avec e-mail automatique au client), remboursement, bon de livraison, facture, notes internes.
- **Clients**, **avis** (modération et réponse), **SAV**.
- **Marketing** : codes promo, bannières programmées, collections ou pages promo, envoi des alertes.
- **Contenu** : articles, pages statiques.
- **Paramètres du site** : logo, favicon, coordonnées, réseaux sociaux, **identifiants de suivi (GTM, GA4, Google Ads, pixel Meta, codes de vérification)**, zones de livraison.
- **Utilisateurs internes et rôles.**
- **Journal des conversions** envoyées à Meta et Google (succès et erreurs).

---

## 5. Médias (images, vidéos, logo, décoration)

- Stockage : **Cloudinary**. La base ne garde que l'url, l'idPublic et les métadonnées. Aucun fichier en BLOB.
- Vérifications côté serveur :
  - images JPG, PNG ou WebP, 5 Mo maximum ;
  - logo en SVG autorisé **après nettoyage** (pas de scripts) ;
  - vidéos MP4 ou WebM, 100 Mo maximum ;
  - type réel vérifié avec Apache Tika.
- Affichage : transformations Cloudinary (`f_auto,q_auto`, largeurs 300, 600 et 1200) avec `srcset`, `NgOptimizedImage`, chargement différé sauf pour l'image LCP (`priority`), vidéo en `preload="none"` avec image d'aperçu, lecteur YouTube chargé seulement au clic (façade légère).
- Suppression d'un média = suppression en base **et** sur Cloudinary.

---

## 6. SEO (référencement Google)

### Technique
- **SSR Angular** pour toutes les pages publiques, et **prérendu** des pages statiques (CGV, à propos…).
- `Title` et `Meta` d'Angular gérés par un `SeoService` central : titre, meta description, URL canonique, robots, Open Graph (`og:title`, `og:description`, `og:image`, `og:type=product`), Twitter Card.
- Modèles de titres :
  - produit : `{Nom} – {caractéristique clé} | {NomSite}` ;
  - catégorie : `{Catégorie} au meilleur prix au Sénégal | {NomSite}`.
  - L'administrateur peut les remplacer champ par champ.
- **Codes HTTP corrects** depuis le serveur SSR : 404 pour une page inexistante, **301** pour un ancien slug (via `HistoriqueSlug`), 410 en option pour un produit supprimé définitivement.
- **Produit en rupture** : la page reste en ligne (pas de 404), avec `availability: OutOfStock` et l'alerte de retour en stock.
- **`sitemap.xml` dynamique** généré par le backend (index + sitemaps produits, catégories, marques, articles, pages), avec `lastmod` et les images des produits. **`robots.txt`** bloque `/admin`, `/compte`, `/panier`, `/commande` et les URL de recherche interne.
- **Navigation à facettes** : les combinaisons de filtres sont en `noindex,follow` avec une canonique vers la catégorie. Seules quelques combinaisons utiles (ex. `/categorie/pc-portables/marque/asus`) sont indexables avec leur propre titre et texte.
- Pagination avec des URL propres (`?page=2`) et une canonique sur chaque page.
- Fil d'Ariane visible sur toutes les pages.
- Attribut `lang="fr"` et `hreflang` si le site devient multilingue.

### Données structurées (JSON-LD rendu côté serveur)
- **Product** sur la fiche produit : name, image[], description, sku, mpn, gtin, brand, `itemCondition`, **offers** (price, `priceCurrency: "XOF"`, availability, url, priceValidUntil, seller), aggregateRating et review si des avis existent, avec un `hasVariant` / `ProductGroup` pour les variantes.
- **BreadcrumbList** sur les produits, catégories et articles.
- **Organization** (logo, coordonnées, réseaux sociaux) et **WebSite** avec `SearchAction` sur l'accueil.
- **ItemList** sur les pages catégorie.
- **Article** sur le blog.
- **LocalBusiness / Store** si une boutique physique existe (adresse, horaires, téléphone).
- Valider chaque modèle avec l'outil de test des résultats enrichis de Google.

### Performance (Core Web Vitals, aussi décisifs pour le coût des publicités)
- Objectifs sur mobile : **LCP < 2,5 s, INP < 200 ms, CLS < 0,1**, Lighthouse ≥ 90.
- Polices hébergées sur le site, en `font-display: swap`, avec préchargement de la police principale.
- Dimensions explicites sur toutes les images, squelettes de chargement de taille fixe.
- Cache HTTP (nginx + en-têtes), compression gzip ou brotli, cache Redis des réponses du catalogue côté API.
- Scripts de suivi chargés via GTM **après** l'affichage, sans bloquer le rendu.

### Contenu
- Description unique pour chaque produit (pas de copier-coller du fabricant sans réécriture), texte éditorial par catégorie, blog et guides d'achat reliés aux produits, maillage interne (catégories ↔ guides ↔ produits).

---

## 7. Publicité : Meta Ads et Google Ads

### 7.1 Architecture du suivi
- **Google Tag Manager** comme conteneur unique côté navigateur (GA4, Google Ads et, si on veut, le pixel Meta y sont configurés). Son identifiant vient de `ParametresSite`.
- Angular : un **`SuiviService`** unique, appelé uniquement dans le navigateur, qui :
  - pousse les événements dans `window.dataLayer` au **format e-commerce GA4** ;
  - déclenche les événements du pixel Meta avec un `eventID` ;
  - gère les changements de page de l'application monopage (`page_view` à chaque navigation).
- Backend : un **`ConversionMetaService`** (API Conversions de Meta) et un **`ConversionGoogleAdsService`** (conversions améliorées et import de conversions hors ligne), appelés de façon **asynchrone** avec plusieurs tentatives et un historique dans `JournalConversion`.

### 7.2 Correspondance des événements

| Action utilisateur | Événement GA4 / Google Ads | Événement Meta |
|---|---|---|
| Affichage d'une page | `page_view` | `PageView` |
| Liste de produits affichée | `view_item_list` | — |
| Clic sur un produit dans une liste | `select_item` | — |
| Fiche produit vue | `view_item` | `ViewContent` |
| Recherche | `search` | `Search` |
| Ajout au panier | `add_to_cart` | `AddToCart` |
| Retrait du panier | `remove_from_cart` | — |
| Panier consulté | `view_cart` | — |
| Ajout à la liste de souhaits | `add_to_wishlist` | `AddToWishlist` |
| Début du tunnel de commande | `begin_checkout` | `InitiateCheckout` |
| Livraison choisie | `add_shipping_info` | — |
| Paiement choisi | `add_payment_info` | `AddPaymentInfo` |
| **Achat** | **`purchase`** (conversion Google Ads) | **`Purchase`** |
| Inscription | `sign_up` | `CompleteRegistration` |
| Clic sur WhatsApp ou téléphone | `contact` (événement personnalisé) | `Contact` |
| Bannière promo vue / cliquée | `view_promotion` / `select_promotion` | — |

- Paramètres obligatoires : `currency: "XOF"`, `value`, `items[]` (`item_id` = SKU, `item_name`, `item_brand`, `item_category`, `item_variant`, `price`, `quantity`), et pour Meta `content_ids` (SKU), `content_type: "product"`, `contents`, `value`, `currency`.
- **`purchase` / `Purchase`** :
  - est déclenché **une seule fois par commande** : la page de confirmation vérifie via l'API que la commande n'a pas encore été suivie ;
  - utilise `transaction_id` = numéro de commande ;
  - utilise `event_id` = `achat-{numeroCommande}`.

### 7.3 Côté serveur (suivi fiable malgré les bloqueurs de publicité et iOS)
- **API Conversions de Meta** :
  - envoi de `Purchase` (et en option `InitiateCheckout` et `AddToCart`) depuis Spring Boot avec le **même `event_id`** que le pixel, pour le dédoublonnage ;
  - `user_data` : email, téléphone (format international `221…`), prénom, nom, ville et pays **hachés en SHA-256 après normalisation**, `external_id` (identifiant client haché), `client_ip_address`, `client_user_agent`, `fbp`, `fbc` ;
  - `action_source: "website"`, `event_source_url` ;
  - version de l'API Graph et jeton d'accès dans la configuration (variables d'environnement) ;
  - un mode test (`test_event_code`) activable.
- **Google Ads** :
  - **conversions améliorées** (email et téléphone hachés transmis avec la conversion d'achat via `user_data` dans GTM) ;
  - **import de conversions hors ligne** via l'API Google Ads à partir du `gclid` enregistré sur la commande (phase 2), indispensable pour le paiement à la livraison.
- **Paiement à la livraison** : la commande est d'abord confirmée, puis réellement payée à la livraison. Il faut :
  - envoyer côté navigateur un événement de commande au moment où le client valide ;
  - envoyer la **vraie conversion `Purchase` côté serveur quand le statut passe à `LIVREE`**, en gardant l'heure d'origine de l'événement si les délais de la plateforme le permettent ;
  - pour Google Ads, importer la conversion hors ligne avec le `gclid`.
  - Ainsi les algorithmes publicitaires apprennent sur les **vraies ventes**, pas sur les commandes annulées.

### 7.4 Attribution
- À l'arrivée sur le site, capturer `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term`, `gclid`, `fbclid`, la page d'arrivée et le référent. Les stocker en premier-partie (cookie de 90 jours), au premier et au dernier clic.
- Construire le cookie `_fbc` à partir du `fbclid` s'il n'existe pas (`fb.1.{horodatageMs}.{fbclid}`) et lire `_fbp`.
- Enregistrer ces données sur la **Commande** pour le tableau de bord « ventes par source ».

### 7.5 Flux produits (catalogues publicitaires)
- **`/flux/google-merchant.xml`** (RSS 2.0, espace de noms `g:`) pour Google Merchant Center (annonces Shopping et Performance Max).
  - **Vérifier que le pays visé est éligible aux annonces Shopping**. Sinon, utiliser les campagnes Search ou Performance Max sans flux, et garder le flux prêt pour plus tard.
- **`/flux/meta-catalogue.xml`** (ou CSV) pour le catalogue Meta (publicités Advantage+ catalogue, reciblage dynamique, boutique Facebook et Instagram).
- Une ligne par **variante** :
  - `id` = SKU, `item_group_id` = identifiant du produit ;
  - `title`, `description`, `link` (URL avec la variante), `image_link`, `additional_image_link` ;
  - `availability`, `price` (ex. `450000 XOF`), `sale_price`, `brand`, `gtin`, `mpn`, `condition` ;
  - `google_product_category`, `product_type` (chemin de catégories), `color`, `shipping`.
- Exclure les produits inactifs, régénérer le flux par une tâche planifiée (`@Scheduled`, toutes les heures), le mettre en cache, et éventuellement le protéger par un jeton dans l'URL.

### 7.6 Consentement et vie privée
- Bandeau de cookies (catégories : nécessaires, mesure d'audience, publicité) avec « Tout accepter », « Tout refuser » et « Personnaliser », et un lien permanent pour modifier son choix.
- **Google Consent Mode v2** : valeurs par défaut `denied` pour `ad_storage`, `analytics_storage`, `ad_user_data` et `ad_personalization`, puis mise à jour selon le choix de l'utilisateur.
- Pixel Meta : `fbq('consent', 'revoke' | 'grant')`. L'API Conversions n'envoie des données personnelles qu'en respectant le consentement enregistré.
- Politique de confidentialité conforme à la loi sénégalaise sur les données personnelles (loi n° 2008-12, CDP) et au RGPD si des clients européens sont visés.

### 7.7 Pages d'atterrissage pour les campagnes
- Pages `Collection` (`/promo/{slug}`) rapides, centrées sur une offre, avec un appel à l'action clair et un bouton WhatsApp. Elles peuvent être en `noindex` si elles ne servent qu'à la publicité.
- Les paramètres UTM sont conservés tout au long de la navigation.
- Balises de vérification de domaine (Google Search Console, Meta) issues de `ParametresSite`, injectées dans le `<head>` par le SSR.

---

## 8. Sécurité

- JWT d'accès de courte durée (15 min) et jeton de rafraîchissement en cookie `HttpOnly`, `Secure`, `SameSite`, avec rotation.
- Mots de passe hachés en BCrypt, limite de tentatives de connexion, limitation du débit sur les routes sensibles (connexion, inscription, mot de passe oublié, codes promo, avis).
- CORS restrictif, en-têtes de sécurité (CSP compatible avec GTM et le pixel, HSTS, X-Content-Type-Options).
- Nettoyage du HTML (descriptions, avis) avec la bibliothèque OWASP Java HTML Sanitizer.
- **Webhooks de paiement** : signature vérifiée, traitement idempotent, et montant revérifié par rapport à la commande.
- Recalcul systématique des prix et des totaux côté serveur. **Le frontend n'envoie jamais de prix.**
- Réservation du stock lors de la création de la commande, et verrou optimiste (`@Version`) sur les variantes.
- Journalisation des actions d'administration.

---

## 9. Design et expérience utilisateur

- Respecter les règles « anti-IA » de `CLAUDE.md`.
- Créer d'abord un **système de design** dans `frontend/src/styles/` : variables de couleurs, typographie, espacements, rayons, ombres. Puis des composants de base réutilisables : bouton, champ, badge, carte produit, prix, indicateur de stock, note en étoiles, squelette, fenêtre modale, panneau latéral, notification.
- Prix au format sénégalais : `450 000 FCFA` (espace comme séparateur de milliers).
- États soignés : panier vide, aucun résultat, erreurs, chargement, page 404 personnalisée.
- Accessibilité : contrastes AA, navigation au clavier, `aria-label`, textes alternatifs, focus visible.
- Mobile d'abord, barre d'achat collante, bouton WhatsApp flottant.

---

## 10. Contrat de l'API (extrait)

```
# Public
GET    /api/parametres
GET    /api/bannieres?emplacement=ACCUEIL_CARROUSEL
GET    /api/categories                       (arbre)
GET    /api/categories/{slug}
GET    /api/categories/{slug}/filtres
GET    /api/produits?categorie=&marque=&prixMin=&prixMax=&tri=&page=&caracteristiques[...]=
GET    /api/produits/{slug}
GET    /api/produits/{slug}/similaires
GET    /api/produits/{slug}/avis
GET    /api/recherche?q=  |  /api/recherche/suggestions?q=
GET    /api/marques/{slug}
GET    /api/articles  |  /api/articles/{slug}
GET    /api/pages/{slug}
GET    /api/collections/{slug}
GET    /api/redirections?chemin=             (résolution des 301)
GET    /sitemap.xml  |  /robots.txt
GET    /flux/google-merchant.xml  |  /flux/meta-catalogue.xml

# Panier et commande
GET/POST/PATCH/DELETE /api/panier, /api/panier/lignes/{id}
POST   /api/panier/code-promo
GET    /api/livraison/zones
POST   /api/commandes
GET    /api/commandes/{numero}/confirmation
POST   /api/paiements/{fournisseur}/initier
POST   /api/paiements/{fournisseur}/webhook

# Authentification et compte
POST   /api/auth/inscription | connexion | rafraichir | deconnexion | mot-de-passe-oublie | reinitialiser
GET    /api/compte/profil | commandes | adresses | liste-souhaits | alertes | sav

# Administration (rôles requis)
/api/admin/produits, /api/admin/produits/{id}/medias, /api/admin/medias/signature-envoi
/api/admin/categories, /api/admin/marques, /api/admin/caracteristiques
/api/admin/commandes, /api/admin/clients, /api/admin/avis, /api/admin/sav
/api/admin/codes-promo, /api/admin/bannieres, /api/admin/collections
/api/admin/articles, /api/admin/pages, /api/admin/parametres, /api/admin/zones-livraison
/api/admin/statistiques, /api/admin/journal-conversions
```

---

## 11. Données de démonstration

Créer un jeu de données réaliste (migration Flyway séparée ou profil `demo`) :
- environ 8 catégories et des sous-catégories ;
- environ 15 marques réelles (Asus, Lenovo, HP, Dell, Apple, MSI, Acer, Samsung, Logitech, Kingston, AMD, Intel, NVIDIA…) ;
- environ 60 produits avec leurs variantes, leurs caractéristiques complètes et des prix en FCFA cohérents ;
- quelques avis, codes promo, bannières, zones de livraison (Dakar, banlieue, régions) et un compte administrateur.

---

## 12. Phases de développement

| Phase | Contenu |
|---|---|
| **0** | Initialisation du monorepo, Docker, CI, système de design, structure Angular SSR |
| **1** | Modèle de données, migrations, données de démonstration, sécurité et authentification |
| **2** | API du catalogue, médias Cloudinary, recherche |
| **3** | Frontend public : mise en page, accueil, catégorie avec filtres, fiche produit, SEO complet (SSR, JSON-LD, sitemap, redirections) |
| **4** | Panier, tunnel de commande, paiements (mobile money, carte, paiement à la livraison), e-mails, factures |
| **5** | Espace client |
| **6** | Back-office complet |
| **7** | Suivi publicitaire : consentement, GTM/GA4, pixel Meta, API Conversions, conversions améliorées, attribution, flux produits |
| **8** | Avis, liste de souhaits, alertes, comparateur, blog et guides, pages promo |
| **9** | Configurateur de PC, import des conversions hors ligne, optimisation des performances, tests de bout en bout, déploiement en production |
