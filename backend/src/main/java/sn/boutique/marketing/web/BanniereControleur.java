package sn.boutique.marketing.web;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.marketing.dto.BanniereReponse;
import sn.boutique.marketing.modele.EmplacementBanniere;
import sn.boutique.marketing.service.BanniereService;

/** Bannières publiques (carrousel de l'accueil, en-tête de catégorie…). */
@RestController
@RequiredArgsConstructor
public class BanniereControleur {

    private final BanniereService banniereService;

    /** Exemple : {@code /api/bannieres?emplacement=ACCUEIL_CARROUSEL}. */
    @GetMapping("/api/bannieres")
    public List<BanniereReponse> bannieres(@RequestParam EmplacementBanniere emplacement,
                                           @RequestParam(required = false) String categorie) {
        return banniereService.visibles(emplacement, categorie);
    }
}
