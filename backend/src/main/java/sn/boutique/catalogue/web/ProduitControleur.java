package sn.boutique.catalogue.web;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.FicheProduitReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.catalogue.service.FicheProduitService;

/** Produits publiés : fiche et produits similaires. */
@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
public class ProduitControleur {

    private final FicheProduitService ficheProduitService;

    @GetMapping("/{slug}")
    public FicheProduitReponse fiche(@PathVariable String slug) {
        return ficheProduitService.trouverParSlug(slug);
    }

    @GetMapping("/{slug}/similaires")
    public List<ProduitResumeReponse> similaires(@PathVariable String slug) {
        return ficheProduitService.similaires(slug);
    }
}
