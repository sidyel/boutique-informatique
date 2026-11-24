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
