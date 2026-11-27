package sn.boutique.commande.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.dto.CommandeResumeReponse;
import sn.boutique.commande.dto.SuiviCommandeReponse;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.LigneCommande;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Commandes du client connecté (espace client). Une commande d'un autre compte est <strong>introuvable (404)</strong>,
 * jamais « interdite » : on ne révèle pas son existence. Les commandes passées en invité puis rattachées au compte
 * (même e-mail vérifié) y apparaissent comme les autres.
 */
@Service
@RequiredArgsConstructor
public class CommandeCompteService {

    static final int TAILLE_PAGE_MAX = 50;

    private final CommandeDepot commandeDepot;
    private final SuiviCommandeService suiviService;

    @Transactional(readOnly = true)
    public Page<CommandeResumeReponse> lister(Long utilisateurId, int page, int taille) {
        var demande = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_PAGE_MAX));
        return commandeDepot.findByUtilisateurIdOrderByDateCreationDesc(utilisateurId, demande).map(this::resume);
    }

    @Transactional(readOnly = true)
    public SuiviCommandeReponse lire(String numero, Long utilisateurId) {
        return suiviService.construire(trouver(numero, utilisateurId));
    }

    /** Nouvelle page de paiement du prestataire pour une commande du compte encore en attente de règlement. */
    public String reprendrePaiement(String numero, Long utilisateurId) {
        trouver(numero, utilisateurId);
        return suiviService.reprendrePaiementDe(numero);
    }

    private Commande trouver(String numero, Long utilisateurId) {
        return commandeDepot.findByNumero(numero)
                .filter(c -> c.getUtilisateur() != null && utilisateurId.equals(c.getUtilisateur().getId()))
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
    }

    private CommandeResumeReponse resume(Commande c) {
        LigneCommande premiere = c.getLignes().isEmpty() ? null : c.getLignes().get(0);
        int articles = c.getLignes().stream().mapToInt(LigneCommande::getQuantite).sum();
        return new CommandeResumeReponse(c.getNumero(), c.getStatut(), c.getDateCreation(), c.getTotal(), articles,
                premiere == null ? null : premiere.getNomProduit(), premiere == null ? null : premiere.getImageUrl());
    }
}
