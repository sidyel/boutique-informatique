package sn.boutique.apresvente.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.apresvente.dto.AvisReponse;
import sn.boutique.apresvente.service.AvisPublicService;
import sn.boutique.catalogue.dto.PageReponse;

/** Avis publiés d'un produit (lecture seule). */
@RestController
@RequiredArgsConstructor
public class AvisControleur {

    private final AvisPublicService avisPublicService;

    /** Avis approuvés, les plus récents d'abord : {@code ?page=1&taille=5} (taille 20 au plus). */
    @GetMapping("/api/produits/{slug}/avis")
    public PageReponse<AvisReponse> lister(@PathVariable String slug,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "5") int taille) {
        return avisPublicService.listerParProduit(slug, page, taille);
    }
}
