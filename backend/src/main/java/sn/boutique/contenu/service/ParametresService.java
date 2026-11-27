package sn.boutique.contenu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.paiement.PaiementService;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.config.ProprietesBoutique;

/** Paramètres publics du site, lus à chaque page : en cache. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametresService {

    private final ParametresSiteDepot parametresDepot;
    private final ProprietesBoutique proprietes;
    private final PaiementService paiementService;

    @Cacheable(cacheNames = CachesPublics.PARAMETRES, key = "'publics'")
    public ParametresPublicsReponse publics() {
        ParametresSite parametres = parametresDepot.lire();
        return ParametresPublicsReponse.depuis(parametres, site(), modesPaiement(parametres));
    }

    /** Moyens de paiement réellement actifs (configuration du prestataire et réglage de l'admin). */
    public ParametresPublicsReponse.ModesPaiement modesPaiement(ParametresSite parametres) {
        return new ParametresPublicsReponse.ModesPaiement(paiementService.enLigneDisponible(),
                paiementService.carteDisponible(), parametres.isPaiementLivraisonActif());
    }

    public ParametresPublicsReponse.Site site() {
        return new ParametresPublicsReponse.Site(proprietes.urlSiteSansBarre(), proprietes.indexationAutorisee());
    }
}
