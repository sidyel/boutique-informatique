package sn.boutique.commande.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.dto.OptionsCommandeReponse;
import sn.boutique.commande.paiement.PaiementService;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;

/** Ce que le tunnel de commande propose aujourd'hui : modes de paiement, retrait en boutique. */
@Service
@RequiredArgsConstructor
public class OptionsCommandeService {

    private final ParametresSiteDepot parametresDepot;
    private final PaiementService paiementService;

    @Transactional(readOnly = true)
    public OptionsCommandeReponse options() {
        ParametresSite parametres = parametresDepot.findById(ParametresSite.ID_UNIQUE)
                .orElseThrow(() -> new IllegalStateException("Paramètres du site absents"));
        String adresse = parametres.isBoutiquePhysique()
                ? parametres.getBoutiqueRue() + ", " + parametres.getBoutiqueVille()
                : null;
        return new OptionsCommandeReponse(paiementService.enLigneDisponible(), parametres.isPaiementLivraisonActif(),
                parametres.getPaiementLivraisonMontantMax(), parametres.isBoutiquePhysique(), adresse,
                parametres.getDelaiPaiementMobileMinutes());
    }
}
