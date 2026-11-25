package sn.boutique.catalogue.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.AccueilReponse;
import sn.boutique.catalogue.service.AccueilService;

/** Sections de produits de la page d'accueil. */
@RestController
@RequiredArgsConstructor
public class AccueilControleur {

    private final AccueilService accueilService;

    @GetMapping("/api/accueil")
    public AccueilReponse accueil() {
        return accueilService.sections();
    }
}
