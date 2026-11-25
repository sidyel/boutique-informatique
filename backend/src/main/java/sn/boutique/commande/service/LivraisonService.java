package sn.boutique.commande.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.ZoneLivraisonDepot;
import sn.boutique.commande.dto.ZoneLivraisonReponse;

/** Zones de livraison publiées (fiche produit, puis tunnel de commande en phase 4). */
@Service
@RequiredArgsConstructor
public class LivraisonService {

    private final ZoneLivraisonDepot zoneDepot;

    /** Zones actives, de la moins chère à la plus chère. */
    @Transactional(readOnly = true)
    public List<ZoneLivraisonReponse> zonesActives() {
        return zoneDepot.findByActifTrueOrderByTarifAsc().stream().map(ZoneLivraisonReponse::depuis).toList();
    }
}
