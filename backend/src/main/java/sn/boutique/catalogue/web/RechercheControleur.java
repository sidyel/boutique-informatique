package sn.boutique.catalogue.web;

import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.RechercheReponse;
import sn.boutique.catalogue.dto.SuggestionsReponse;
import sn.boutique.catalogue.service.CriteresProduits;
import sn.boutique.catalogue.service.MoteurRecherche;

/** Recherche dans le catalogue et autocomplétion. */
@RestController
@RequestMapping("/api/recherche")
@RequiredArgsConstructor
public class RechercheControleur {

    private final MoteurRecherche moteurRecherche;

    /** Exemple : {@code /api/recherche?q=portable asus&marque=asus&tri=prix-croissant}. Mêmes filtres que les listes. */
    @GetMapping
    public RechercheReponse rechercher(@RequestParam(required = false) String q,
                                       @RequestParam MultiValueMap<String, String> parametres) {
        return moteurRecherche.rechercher(q, CriteresProduits.depuis(parametres));
    }

    @GetMapping("/suggestions")
    public SuggestionsReponse suggestions(@RequestParam(required = false) String q) {
        return moteurRecherche.suggerer(q);
    }
}
