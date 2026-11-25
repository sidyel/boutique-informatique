package sn.boutique.catalogue.web;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.CategorieNoeudReponse;
import sn.boutique.catalogue.dto.CategorieReponse;
import sn.boutique.catalogue.dto.FiltresReponse;
import sn.boutique.catalogue.dto.MarqueReponse;
import sn.boutique.catalogue.dto.MarqueResumeReponse;
import sn.boutique.catalogue.dto.RedirectionReponse;
import sn.boutique.catalogue.service.CategorieService;
import sn.boutique.catalogue.service.CriteresProduits;
import sn.boutique.catalogue.service.FacettesService;
import sn.boutique.catalogue.service.MarqueService;
import sn.boutique.catalogue.service.RedirectionService;

/** Lectures publiques : catégories, marques et redirections des anciens slugs. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogueControleur {

    private final CategorieService categorieService;
    private final FacettesService facettesService;
    private final MarqueService marqueService;
    private final RedirectionService redirectionService;

    @GetMapping("/categories")
    public List<CategorieNoeudReponse> arbreCategories() {
        return categorieService.arbre();
    }

    @GetMapping("/categories/{slug}")
    public CategorieReponse categorie(@PathVariable String slug) {
        return categorieService.trouverParSlug(slug);
    }

    /** Filtres de la catégorie avec leurs décomptes ; mêmes paramètres que {@code /api/produits}. */
    @GetMapping("/categories/{slug}/filtres")
    public FiltresReponse filtres(@PathVariable String slug, @RequestParam MultiValueMap<String, String> parametres) {
        return facettesService.filtres(slug, CriteresProduits.depuis(parametres));
    }

    @GetMapping("/marques")
    public List<MarqueResumeReponse> marques() {
        return marqueService.lister();
    }

    @GetMapping("/marques/{slug}")
    public MarqueReponse marque(@PathVariable String slug) {
        return marqueService.trouverParSlug(slug);
    }

    /** Exemple : {@code /api/redirections?chemin=/produit/ancien-slug}. */
    @GetMapping("/redirections")
    public RedirectionReponse redirection(@RequestParam String chemin) {
        return redirectionService.resoudre(chemin);
    }
}
