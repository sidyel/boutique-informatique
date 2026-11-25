package sn.boutique.catalogue.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.CategorieDepot;
import sn.boutique.catalogue.dto.CategorieNoeudReponse;
import sn.boutique.catalogue.dto.CategorieReponse;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Lectures publiques des catégories. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategorieService {

    private final JdbcClient jdbc;
    private final CategorieDepot categorieDepot;

    /** Arbre complet des catégories visibles, en une requête. */
    public ArbreCategories chargerArbre() {
        return new ArbreCategories(jdbc.sql("""
                        select c.id, c.parent_id, c.nom, c.slug, c.image_url, count(p.id) as produits_directs
                          from categorie c
                          left join produit p on p.categorie_id = c.id and p.actif
                         where c.actif
                         group by c.id
                         order by c.position, c.nom
                        """)
                .query((ligne, n) -> new ArbreCategories.Ligne(
                        ligne.getLong("id"), ligne.getObject("parent_id", Long.class), ligne.getString("nom"),
                        ligne.getString("slug"), ligne.getString("image_url"), ligne.getLong("produits_directs")))
                .list());
    }

    public List<CategorieNoeudReponse> arbre() {
        return chargerArbre().versReponse();
    }

    public CategorieReponse trouverParSlug(String slug) {
        ArbreCategories.Noeud noeud = chargerArbre().trouver(slug)
                .orElseThrow(() -> new RessourceIntrouvableException("Catégorie", slug));
        Categorie categorie = categorieDepot.findById(noeud.id())
                .orElseThrow(() -> new RessourceIntrouvableException("Catégorie", slug));
        return new CategorieReponse(categorie.getNom(), categorie.getSlug(), categorie.getDescription(),
                categorie.getImageUrl(), categorie.getBanniereUrl(), categorie.getTexteSeo(), categorie.getMetaTitre(),
                categorie.getMetaDescription(), noeud.nombreProduits(), noeud.filAriane(),
                noeud.enfants().stream().map(ArbreCategories.Noeud::versReponse).toList());
    }
}
