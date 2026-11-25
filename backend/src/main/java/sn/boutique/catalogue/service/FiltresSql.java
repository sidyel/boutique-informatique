package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sn.boutique.catalogue.modele.EtatProduit;

/**
 * Traduit des {@link CriteresProduits} en conditions SQL paramétrées (jamais de valeur saisie concaténée).
 * <p>
 * Deux familles de conditions :
 * <ul>
 *   <li><strong>produit</strong> (alias {@code p}) : catégorie, marque, état, recherche ;</li>
 *   <li><strong>variante</strong> (alias {@code v}, variante active du produit {@code p}) : prix, disponibilité et
 *       caractéristiques. Une variante ne convient que si elle satisfait <em>tous</em> ces filtres à la fois.</li>
 * </ul>
 * Pour une caractéristique, la valeur d'une variante est sa valeur propre si elle en a une (RAM d'une
 * déclinaison), sinon celle du produit (processeur commun à toutes les déclinaisons).
 */
final class FiltresSql {

    /** Valeur brute d'une caractéristique, au même format que les filtres : « 16 », « 15.6 », « true », texte. */
    static final String VALEUR_BRUTE =
            "coalesce(vc.valeur_texte, trim_scale(vc.valeur_nombre)::text, vc.valeur_booleen::text)";

    /**
     * Valeur de caractéristique qui s'applique à la variante {@code v} du produit {@code p} : celle de la
     * variante, ou à défaut celle du produit. {@code %s} : paramètre de la clé.
     */
    static final String VALEUR_APPLICABLE = """
            vc.produit_id = p.id
            and (vc.variante_id = v.id
                 or (vc.variante_id is null and not exists (
                       select 1 from valeur_caracteristique vs
                         join definition_caracteristique ds on ds.id = vs.definition_id
                        where vs.produit_id = p.id and vs.variante_id = v.id and ds.cle = d.cle)))""";

    private final List<String> conditionsProduit = new ArrayList<>();
    private final List<String> conditionsVariante = new ArrayList<>();
    private final Map<String, Object> parametres = new HashMap<>();

    /**
     * @param categories identifiants de la catégorie et de ses descendants, ou {@code null} (toutes)
     * @param recherche  condition plein texte sur {@code p} déjà paramétrée, ou {@code null}
     */
    FiltresSql(CriteresProduits criteres, List<Long> categories, String recherche, Map<String, Object> parametresRecherche) {
        if (categories != null) {
            conditionsProduit.add("p.categorie_id in (:categories)");
            parametres.put("categories", categories);
        }
        if (!criteres.marques().isEmpty()) {
            conditionsProduit.add("p.marque_id in (select id from marque where slug in (:marques))");
            parametres.put("marques", List.copyOf(criteres.marques()));
        }
        if (!criteres.etats().isEmpty()) {
            conditionsProduit.add("p.etat in (:etats)");
            parametres.put("etats", criteres.etats().stream().map(EtatProduit::name).toList());
        }
        if (recherche != null) {
            conditionsProduit.add(recherche);
            parametres.putAll(parametresRecherche);
        }
        if (criteres.prixMin() != null) {
            conditionsVariante.add("v.prix >= :prixMin");
            parametres.put("prixMin", criteres.prixMin());
        }
        if (criteres.prixMax() != null) {
            conditionsVariante.add("v.prix <= :prixMax");
            parametres.put("prixMax", criteres.prixMax());
        }
        if (criteres.disponible()) {
            conditionsVariante.add("v.stock > 0");
        }
        if (criteres.promotion()) {
            conditionsVariante.add("v.prix_barre > v.prix");
        }
        int numero = 0;
        for (Map.Entry<String, Set<String>> filtre : criteres.caracteristiques().entrySet()) {
            String cle = "cle" + numero;
            String valeurs = "valeurs" + numero++;
            conditionsVariante.add("""
                    exists (select 1 from valeur_caracteristique vc
                              join definition_caracteristique d on d.id = vc.definition_id
                             where d.cle = :%s and %s
                               and %s in (:%s))""".formatted(cle, VALEUR_APPLICABLE, VALEUR_BRUTE, valeurs));
            parametres.put(cle, filtre.getKey());
            parametres.put(valeurs, List.copyOf(filtre.getValue()));
        }
    }

    /**
     * Produits retenus avec <strong>leur variante active la moins chère parmi celles qui conviennent</strong>
     * (colonnes {@code v.id}, {@code v.prix}, {@code v.stock}). Un produit sans variante qui convient est écarté.
     */
    String produitsAvecVarianteRetenue() {
        return produitsAvecVarianteRetenue("");
    }

    /** Idem, avec des jointures supplémentaires sur {@code p} (fragment fixe, jamais saisi par l'utilisateur). */
    String produitsAvecVarianteRetenue(String jointures) {
        return """
                from produit p %s
                cross join lateral (select v.id, v.prix, v.stock
                                      from variante_produit v
                                     where v.produit_id = p.id and v.actif%s
                                     order by v.prix, v.id
                                     limit 1) v
                where p.actif%s""".formatted(jointures, et(conditionsVariante), et(conditionsProduit));
    }

    /** Toutes les variantes qui conviennent (une ligne par couple produit, variante) : base des facettes. */
    String variantesQuiConviennent() {
        return """
                from produit p
                join variante_produit v on v.produit_id = p.id and v.actif%s
                where p.actif%s""".formatted(et(conditionsVariante), et(conditionsProduit));
    }

    Map<String, Object> parametres() {
        return parametres;
    }

    private static String et(List<String> conditions) {
        StringBuilder sql = new StringBuilder();
        conditions.forEach(condition -> sql.append("\n and ").append(condition));
        return sql.toString();
    }
}
