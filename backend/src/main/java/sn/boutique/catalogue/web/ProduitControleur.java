package sn.boutique.catalogue.web;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.FicheProduitReponse;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.catalogue.service.CriteresProduits;
import sn.boutique.catalogue.service.FicheProduitService;
import sn.boutique.catalogue.service.ListeProduitsService;

/** Produits publiés : liste filtrée, fiche et produits similaires. */
@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
public class ProduitControleur {

    private final FicheProduitService ficheProduitService;
    private final ListeProduitsService listeProduitsService;

    /**
     * Liste filtrée et paginée. Filtres à valeurs multiples en paramètres répétés :
     * {@code ?categorie=pc-portables&marque=asus&marque=lenovo&prixMin=300000&disponible=true&f.ram_go=16&tri=prix-croissant&page=2}.
     */
    @GetMapping
    public PageReponse<ProduitResumeReponse> lister(@RequestParam MultiValueMap<String, String> parametres) {
        return listeProduitsService.lister(CriteresProduits.depuis(parametres));
    }

    @GetMapping("/{slug}")
    public FicheProduitReponse fiche(@PathVariable String slug) {
        return ficheProduitService.trouverParSlug(slug);
    }

    @GetMapping("/{slug}/similaires")
    public List<ProduitResumeReponse> similaires(@PathVariable String slug) {
        return ficheProduitService.similaires(slug);
    }
}
