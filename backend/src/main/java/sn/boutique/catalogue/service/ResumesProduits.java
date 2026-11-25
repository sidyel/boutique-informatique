package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sn.boutique.catalogue.dto.CaracteristiqueCarteReponse;
import sn.boutique.catalogue.dto.Disponibilite;
import sn.boutique.catalogue.dto.ImageReponse;
import sn.boutique.catalogue.dto.LienReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.catalogue.modele.EtatProduit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Construit les cartes produit en <strong>une seule requête</strong> (caractéristiques clés comprises), à partir de couples
 * (produit, variante retenue) déjà triés et paginés par l'appelant (liste, recherche, similaires).
 */
@Component
@RequiredArgsConstructor
public class ResumesProduits {

    /** Produit à afficher et variante dont la carte montre le prix, la disponibilité et le SKU. */
    public record Selection(long produitId, long varianteId) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

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
                                   as nombre_variantes,
                               -- Caractéristiques de la carte : valeur propre de la variante, sinon celle du produit
                               (select coalesce(json_agg(json_build_object(
                                           'libelle', coalesce(d.libelle_court, d.libelle), 'type', d.type,
                                           'unite', d.unite, 'valeur', vc.valeur) order by d.ordre_carte), '[]')
                                  from definition_caracteristique d
                                  join lateral (select coalesce(x.valeur_texte, trim_scale(x.valeur_nombre)::text,
                                                                x.valeur_booleen::text) as valeur
                                                  from valeur_caracteristique x
                                                 where x.definition_id = d.id and x.produit_id = p.id
                                                   and (x.variante_id = v.id or x.variante_id is null)
                                                 order by x.variante_id nulls last
                                                 limit 1) vc on true
                                 where d.categorie_id = p.categorie_id and d.ordre_carte is not null)
                                   as caracteristiques
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
                            ligne.getInt("nombre_variantes"),
                            caracteristiques(ligne.getString("caracteristiques")));
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

    /** Caractéristiques agrégées par la requête ({@code json_agg}), mises en forme pour l'affichage. */
    private List<CaracteristiqueCarteReponse> caracteristiques(String json) {
        List<CaracteristiqueCarteReponse> lignes = new ArrayList<>();
        for (JsonNode c : jsonMapper.readTree(json)) {
            String valeur = MiseEnFormeCatalogue.valeurAffichee(
                    TypeCaracteristique.valueOf(c.get("type").asString()), c.path("valeur").asString(null));
            String unite = c.path("unite").asString(null);
            if (valeur != null) {
                lignes.add(new CaracteristiqueCarteReponse(c.get("libelle").asString(),
                        unite == null || unite.isBlank() ? valeur : valeur + "\u00A0" + unite));
            }
        }
        return List.copyOf(lignes);
    }
}
