package sn.boutique.catalogue.service;

import sn.boutique.catalogue.dto.RechercheReponse;
import sn.boutique.catalogue.dto.SuggestionsReponse;

/**
 * Moteur de recherche du catalogue. L'implémentation actuelle utilise PostgreSQL (plein texte et
 * trigrammes) ; cette interface permet de passer à Elasticsearch ou OpenSearch sans toucher aux
 * contrôleurs.
 */
public interface MoteurRecherche {

    int LONGUEUR_MIN = 2;
    int LONGUEUR_MAX = 100;

    /**
     * Recherche paginée, avec les mêmes filtres et tris que les listes (plus la pertinence, tri par défaut).
     * Sans résultat exact, bascule sur une recherche tolérante aux fautes de frappe.
     */
    RechercheReponse rechercher(String texte, CriteresProduits criteres);

    /** Suggestions pendant la frappe (quelques produits, catégories et marques). */
    SuggestionsReponse suggerer(String texte);
}
