# ADR 0004 — Produits commandés jamais supprimés, agrégats calculés par trigger

- **Statut** : accepté
- **Date** : 2026-11-24
- **Phase** : 1 (commit « entités des comptes, du panier et des commandes »)

## 1. Un produit déjà commandé n'est jamais supprimé physiquement

### Contexte
`ligne_commande` garde une **copie figée** du produit au moment de l'achat (nom, SKU, libellé de variante,
image, prix unitaire) : une facture, un historique client ou un export comptable ne changent jamais quand le
catalogue évolue. Elle garde aussi la **référence** à `variante_produit`, utile pour le service après-vente,
les statistiques de ventes par produit, la réassort et le lien « racheter ».

### Décision
- Clé étrangère `fk_ligne_commande_variante` : `ligne_commande.variante_id → variante_produit(id)` en
  **`ON DELETE RESTRICT`**. Comme `variante_produit → produit` est en `ON DELETE CASCADE`, supprimer le produit
  est lui aussi bloqué dès qu'une de ses variantes a été commandée. La base garantit la règle quel que soit le
  chemin d'écriture (application, script SQL, import).
- Un produit commandé est **désactivé** (`actif = false`), jamais supprimé. Idem pour une variante seule.
- Un produit **jamais commandé** peut être supprimé (brouillon, erreur de saisie) : ses variantes, médias et
  caractéristiques partent en cascade, et ses lignes de panier aussi (`ligne_panier → variante_produit` en
  `ON DELETE CASCADE`).
- Les paiements sont aussi en `ON DELETE RESTRICT` vers la commande (trace financière).

### À faire en phase 6 (admin)
- `DELETE /api/admin/produits/{id}` : si `LigneCommandeDepot.produitDejaCommande(id)` est vrai, **désactiver**
  au lieu de supprimer et renvoyer `200` avec `{"action": "DESACTIVE"}` (sinon `204`). L'interface l'annonce
  avant confirmation : « Ce produit a déjà été commandé : il sera masqué du site, pas supprimé ».
- Même règle pour retirer une variante (`LigneCommandeDepot.existsByVarianteId`). Attention : retirer une
  variante de `Produit.variantes` la supprime (`orphanRemoval`) et échouerait au flush ; le service désactive.
- Test de service pour les deux chemins (commandé → désactivé, jamais commandé → supprimé).

### À trancher en phase 3 (SEO)
URL d'un produit désactivé : **301** vers sa catégorie (garde le jus SEO, bonne expérience) ou **410 Gone**
(désindexation plus rapide). Piste : 301 s'il existe un produit de remplacement ou une catégorie active, 410
sinon. Les flux Merchant Center / Meta excluent déjà les produits inactifs.

## 2. `prixMinimum` et `stockTotal` : calculés par la base, périmés dans la transaction

### Contexte
`produit.prix_minimum` et `produit.stock_total` sont dénormalisés (tris et filtres du catalogue sans jointure)
et recalculés par le trigger `tr_variante_agregats_*` (V2) à chaque modification de variante. JPA les mappe en
lecture seule (`insertable = false, updatable = false`).

### Conséquence
Un `Produit` déjà chargé dans le contexte de persistance **garde les anciennes valeurs** après la modification
d'une de ses variantes, même après un `flush` : Hibernate ne sait pas que le trigger a écrit dans `produit`.

### Règle
Tout service qui modifie des variantes puis renvoie le produit (phase 2 : catalogue ; phase 6 : admin produits,
ajustement de stock ; phase 4 : décrément du stock à la commande si le produit est renvoyé) relit le produit
avant de répondre :

```java
produitDepot.saveAndFlush(produit);
entityManager.refresh(produit);   // ou une nouvelle requête après flush
```

Le commentaire sur les champs de `Produit` le rappelle.

### À faire
- Dans chacun de ces services, un **test de service** : modifier le prix ou le stock d'une variante via le
  service, puis vérifier que la réponse contient le nouveau `prixMinimum` / `stockTotal`. Le test de dépôt
  `CatalogueDepotTest.prixMinimumEtStockTotalSuiventLesVariantesActives` couvre déjà le trigger lui-même.

## 3. Suppression d'un compte : anonymisation partielle des commandes (phase 5)

### Contexte
`commande.utilisateur_id` est en `ON DELETE SET NULL` : supprimer un compte (droit à l'effacement) ne supprime
pas ses commandes. Mais les factures doivent être conservées : **en principe 10 ans en zone OHADA**
(Acte uniforme relatif au droit comptable), **durée à confirmer avec le comptable**.

### Règle (à implémenter en phase 5, service de suppression de compte)
Dans la même transaction que la suppression du compte, pour chaque commande du client :

| On garde (nécessaire à la facture) | On efface (mis à `NULL`) |
|---|---|
| numéro, dates, statut, historique des statuts | `adresse_ip`, `user_agent` |
| `livraison_nom_complet`, ville, quartier, adresse, point de repère | `fbp`, `fbc`, `gclid`, `fbclid` |
| lignes (copies figées), sous-total, frais, remise, total, code promo | `utm_*`, `page_arrivee`, `referent` |
| mode de paiement, paiements (montant, référence prestataire) | `paiement.donnees_brutes` (corps des webhooks) |
| e-mail et téléphone client **si** une facture a été émise | point de repère conservé seulement s'il figure sur la facture |

- Commande **sans facture** (jamais payée, annulée) : rien n'oblige à la conserver nominative ; on anonymise
  aussi nom, e-mail, téléphone et adresse (`livraison_nom_complet = 'Client supprimé'`, e-mail technique
  `supprime-{id}@invalide.local`, téléphone à `NULL` — la contrainte `NOT NULL` de `telephone_client` sera
  assouplie par migration en phase 5 pour ce cas).
- **Avis** : l'avis reste publié (note, titre, commentaire, photos, réponse de la boutique) et continue de
  compter dans la note du produit, mais `nom_auteur` remplace le prénom et l'initiale par :
  - **« Client vérifié »** si l'avis est lié à un achat vérifié (`achat_verifie = true`) ;
  - **« Client »** sinon (ne jamais affirmer un achat qui n'a pas eu lieu).

  `utilisateur_id` passe déjà à `NULL` par la clé étrangère. Aucune donnée permettant de reconnaître la
  personne ne reste affichée.
- **Messages SAV** : conservés (historique de la demande), `nom_auteur` devient « Client supprimé ».
- Purge des commandes au-delà de la durée légale : tâche planifiée, une fois la durée confirmée.
- Tests : compte supprimé → données marketing effacées, données de facture intactes, avis toujours publié
  et signé « Client vérifié » ou « Client » selon `achat_verifie`.

## 4. Numérotation : commandes avec trous, factures sans trou (phase 4)

### Décision
- **Numéro de commande** (`CMD-2026-000123`) : séquence PostgreSQL. Des trous sont possibles (transaction annulée)
  et **acceptés** : ce n'est pas une pièce comptable.
- **Facture** (entité `Facture`, phase 4) : numérotation **chronologique, continue et sans trou** par année
  (`FAC-2026-000001`), exigence comptable. Attribuée **uniquement quand le paiement est confirmé** (webhook vérifié
  ou encaissement à la livraison), jamais à la création de la commande.
- Mécanisme : **table compteur** verrouillée dans la transaction du paiement, **pas une séquence** (une séquence
  n'est pas annulée avec la transaction, d'où des trous) :

  ```sql
  CREATE TABLE compteur_facture (annee integer PRIMARY KEY, dernier_numero bigint NOT NULL DEFAULT 0);
  -- dans la transaction qui confirme le paiement :
  INSERT INTO compteur_facture (annee) VALUES (:annee) ON CONFLICT DO NOTHING;
  UPDATE compteur_facture SET dernier_numero = dernier_numero + 1
   WHERE annee = :annee RETURNING dernier_numero;   -- verrou de ligne jusqu'au commit
  ```

  Si la transaction échoue, l'incrément est annulé avec elle : pas de trou. Le verrou sérialise l'émission
  des factures, ce qui est négligeable au volume attendu.
- `Facture` : numéro unique, commande (unique : une facture par commande), date d'émission, copie des montants,
  PDF généré ; jamais modifiée ni supprimée (un remboursement donnera un **avoir**, numérotation séparée sur le
  même principe).
- Tests : factures concurrentes (plusieurs threads) → numéros consécutifs sans doublon ; transaction annulée → pas de trou.

## 5. Avis réservés aux acheteurs (phase 8)

### Règle
- Par défaut, **seuls les clients ayant une commande `LIVREE` contenant le produit** peuvent publier un avis
  (n'importe quelle variante du produit). L'avis est alors toujours enregistré avec `achat_verifie = true`.
- Vérification côté serveur au dépôt de l'avis (jamais sur la seule foi du frontend) : existe-t-il une
  `ligne_commande` de ce produit dans une commande `LIVREE` de cet utilisateur ? Sinon : `403` au format
  ProblemDetail, code `AVIS_RESERVE_AUX_ACHETEURS`, message « Seuls les clients ayant reçu ce produit
  peuvent laisser un avis. »
- Un avis par client et par produit (contrainte `uk_avis_produit_utilisateur`, déjà en place).

### Paramètre
- `ParametresSite.avisReservesAuxAcheteurs` (colonne `avis_reserves_aux_acheteurs`, **`true` par défaut**,
  migration V8), modifiable dans l'admin sans redéploiement.
- S'il passe à `false`, tout client connecté peut publier un avis ; `achat_verifie` est alors calculé (vrai
  seulement s'il existe une telle commande livrée) et le badge « Achat vérifié » n'apparaît que dans ce cas.

### Tests (phase 8)
- Paramètre à `true` : client avec commande livrée → avis accepté, `achat_verifie = true` ; commande non
  livrée ou absente → 403.
- Paramètre à `false` : avis accepté dans les deux cas, `achat_verifie` exact.

## 6. Cartes produit et sections de l'accueil (phase 3a, migration V13)

### Caractéristiques clés des cartes
- `definition_caracteristique.ordre_carte` (1 à 4, unique par catégorie) choisit les caractéristiques
  affichées sur la carte, `libelle_court` (« RAM » pour « Mémoire vive ») leur libellé. Contraintes en base :
  jamais plus de 4 caractéristiques par carte, jamais deux au même rang.
- `ProduitResumeReponse.caracteristiquesCles` : valeur de la **variante retenue** si elle en a une (RAM d'une
  déclinaison), sinon celle du produit ; unité séparée par une espace insécable (« 16 Go »).
- Lues dans la **même requête** que les cartes (`json_agg`) : une liste coûte toujours au plus 4 requêtes.

### Filtre `promotion=true`
Condition de variante (`prix_barre > prix`) : la variante retenue est la moins chère **parmi celles en
promotion**, et la carte affiche bien un prix barré.

### `GET /api/accueil` : aucun titre ne doit affirmer quelque chose de faux
Une seule réponse (un aller-retour sur mobile), en cache, avec les sections dans l'ordre d'affichage ; une
section vide est absente.

| Section | Contenu | Condition |
|---|---|---|
| Nouveautés | produits créés depuis moins de `anciennete-nouveautes` (90 j) | au moins un produit |
| Meilleures ventes | `nombre_ventes > 0`, tri popularité | au moins `seuil-meilleures-ventes` (8) produits vendus |
| Sélection du moment | produits mis en avant | à la place de « Meilleures ventes » sous le seuil |
| Promotions | variante retenue avec prix barré | au moins un produit |

Le titre est choisi par le serveur : le frontend n'écrit jamais « Meilleures ventes » lui-même.
Le compte à rebours des promotions ne vient que de la `dateFin` d'une bannière `ACCUEIL_PROMO` : les prix
n'ont pas de date de fin, aucune échéance n'est inventée.

### Promesses commerciales (`ParametresSite`)
`messageBarreSuperieure` (barre de l'en-tête) et `elementsConfiance` (bandeau de l'accueil : icône parmi une
liste fermée, titre, texte court ; 6 au plus) : saisis par l'équipe, jamais écrits en dur dans le site. Vides
par défaut ; un élément sans titre est masqué. Formulaire d'édition : back-office (phase 6) ; la démo fournit
des valeurs.
