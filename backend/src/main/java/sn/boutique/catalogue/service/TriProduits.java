package sn.boutique.catalogue.service;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tris proposés sur les listes de produits ({@code tri=prix-croissant}). Chaque tri se termine par
 * l'identifiant du produit pour une pagination stable.
 * <p>
 * Le prix trié est celui de la <strong>variante retenue</strong> (celle affichée sur la carte).
 */
@Getter
@RequiredArgsConstructor
public enum TriProduits {
    /** Ordre de la boutique, par défaut sur une catégorie : mis en avant, puis popularité, puis nouveauté. */
    RECOMMANDE("recommande", "p.mis_en_avant desc, p.nombre_ventes desc, p.date_creation desc"),
    /** Uniquement avec une recherche (ordre défini par le moteur de recherche). */
    PERTINENCE("pertinence", null),
    PRIX_CROISSANT("prix-croissant", "v.prix asc"),
    PRIX_DECROISSANT("prix-decroissant", "v.prix desc"),
    NOUVEAUTE("nouveaute", "p.date_creation desc"),
    POPULARITE("popularite", "p.nombre_ventes desc, p.nombre_avis desc, p.note_moyenne desc"),
    NOTE("note", "p.note_moyenne desc, p.nombre_avis desc");

    private final String code;
    /** Fragment ORDER BY (alias p = produit, v = variante retenue), sans saisie de l'utilisateur. */
    private final String ordreSql;

    public static Optional<TriProduits> depuisCode(String code) {
        return Arrays.stream(values()).filter(tri -> tri.code.equals(code)).findFirst();
    }
}
