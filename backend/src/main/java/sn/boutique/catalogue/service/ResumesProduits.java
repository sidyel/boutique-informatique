package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sn.boutique.catalogue.dto.Disponibilite;
import sn.boutique.catalogue.dto.ImageReponse;
import sn.boutique.catalogue.dto.LienReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.catalogue.modele.EtatProduit;

/**
 * Construit les cartes produit en <strong>une seule requête</strong>, à partir de couples
 * (produit, variante retenue) déjà triés et paginés par l'appelant (liste, recherche, similaires).
 */
@Component
@RequiredArgsConstructor
public class ResumesProduits {

    /** Produit à afficher et variante dont la carte montre le prix, la disponibilité et le SKU. */
    public record Selection(long produitId, long varianteId) {
    }

    private final JdbcClient jdbc;

    public List<ProduitResumeReponse> lire(List<Selection> selections) {
        if (selections.isEmpty()) {
            return List.of();
        }
        List<Long> produits = selections.stream().map(Selection::produitId).toList();
        List<Long> variantes = selections.stream().map(Selection::varianteId).toList();
        Map<Long, ProduitResumeReponse> parVariante = new HashMap<>();
        jdbc.sql("""
                        select p.id, p.slug, p.nom, p.etat, p.note_moyenne, p.nombre_avis,
                               m.nom as marque_nom, m.slug as marque_slug, c.nom as categorie_nom, c.slug as categorie_slug,
                               v.id as variante_id, v.sku, v.prix, v.prix_barre, v.stock, v.seuil_alerte,
                               img.url as image_url, img.texte_alternatif as image_texte,
                               (select count(*) from variante_produit vv where vv.produit_id = p.id and vv.actif)
                                   as nombre_variantes
                          from produit p
                          join marque m on m.id = p.marque_id
                          join categorie c on c.id = p.categorie_id
                          join variante_produit v on v.produit_id = p.id
                          left join media_produit img on img.produit_id = p.id and img.principal
                         where p.id in (:produits) and v.id in (:variantes)
                        """)
                .param("produits", produits)
                .param("variantes", variantes)
                .query((ligne, n) -> {
                    long prix = ligne.getLong("prix");
                    Long prixBarre = ligne.getObject("prix_barre", Long.class);
                    String imageUrl = ligne.getString("image_url");
                    return new ProduitResumeReponse(
                            ligne.getLong("id"), ligne.getString("slug"), ligne.getString("nom"),
                            new LienReponse(ligne.getString("marque_nom"), ligne.getString("marque_slug")),
                            new LienReponse(ligne.getString("categorie_nom"), ligne.getString("categorie_slug")),
                            imageUrl == null ? null : new ImageReponse(imageUrl, ligne.getString("image_texte")),
                            ligne.getLong("variante_id"), ligne.getString("sku"), prix, prixBarre,
                            MiseEnFormeCatalogue.reduction(prix, prixBarre),
                            EtatProduit.valueOf(ligne.getString("etat")),
                            Disponibilite.depuisStock(ligne.getInt("stock"), ligne.getInt("seuil_alerte")),
                            ligne.getBigDecimal("note_moyenne"), ligne.getInt("nombre_avis"),
                            ligne.getInt("nombre_variantes"));
                })
                .list()
                .forEach(resume -> parVariante.put(resume.varianteId(), resume));
        // Remet les cartes dans l'ordre de la sélection (le tri a été fait par l'appelant)
        List<ProduitResumeReponse> resultat = new ArrayList<>(selections.size());
        for (Selection selection : selections) {
            ProduitResumeReponse resume = parVariante.get(selection.varianteId());
            if (resume != null && resume.id() == selection.produitId()) {
                resultat.add(resume);
            }
        }
        return resultat;
    }
}
