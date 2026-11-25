package sn.boutique.commande.web;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.dto.ZoneLivraisonReponse;
import sn.boutique.commande.service.LivraisonService;

/** Zones et tarifs de livraison (lecture seule). */
@RestController
@RequiredArgsConstructor
public class LivraisonControleur {

    private final LivraisonService livraisonService;

    @GetMapping("/api/livraison/zones")
    public List<ZoneLivraisonReponse> zones() {
        return livraisonService.zonesActives();
    }
}
