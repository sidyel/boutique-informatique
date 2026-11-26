package sn.boutique.commande.service;

import java.util.EnumSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.marketing.service.ReservationCodePromo;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;
import sn.boutique.stock.service.StockService;

/**
 * Confirmation et annulation d'une commande (service client, tâche d'expiration).
 * <p>
 * Chaque opération verrouille la commande ({@code SELECT ... FOR UPDATE}) puis revérifie son statut : une
 * confirmation et une expiration simultanées, ou deux instances du backend, ne s'appliquent jamais deux fois ;
 * le stock et le code promo ne sont rendus qu'une fois.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GestionCommandeService {

    public static final String AUTEUR_SYSTEME = "SYSTEME";

    /** Statuts depuis lesquels on peut encore annuler (avant la préparation de la commande). */
    private static final Set<StatutCommande> ANNULABLES = EnumSet.of(StatutCommande.EN_ATTENTE_PAIEMENT,
            StatutCommande.EN_ATTENTE_CONFIRMATION, StatutCommande.CONFIRMEE);

    private final CommandeDepot commandeDepot;
    private final StockService stockService;
    private final ReservationCodePromo reservationCodePromo;

    /**
     * Le service client a joint le client et validé la commande à la livraison. Le stock reste réservé : il est
     * déjà sorti du stock vendable.
     */
    @Transactional
    public Commande confirmer(String numero, String auteur) {
        Commande commande = verrouiller(numero);
        if (commande.getStatut() != StatutCommande.EN_ATTENTE_CONFIRMATION) {
            throw new ConflitException("STATUT_INVALIDE", commande.getStatut() == StatutCommande.ANNULEE
                    ? "Cette commande a été annulée (délai de confirmation dépassé ou annulation manuelle)."
                    : "Cette commande n'attend pas de confirmation (statut : %s).".formatted(commande.getStatut()));
        }
        commande.changerStatut(StatutCommande.CONFIRMEE, auteur, "Confirmée par téléphone");
        commande.setExpireLe(null);
        log.info("Commande {} confirmée par {}", numero, auteur);
        return commande;
    }

    /**
     * Annulation (service client, expiration) : stock remis en vente, utilisation du code promo rendue, paiements
     * en attente abandonnés. Une commande déjà payée ne s'annule pas ici : elle se rembourse (avoir).
     *
     * @param motif texte libre gardé dans l'historique de la commande
     */
    @Transactional
    public Commande annuler(String numero, String auteur, String motif) {
        Commande commande = verrouiller(numero);
        if (!ANNULABLES.contains(commande.getStatut())) {
            throw new ConflitException("STATUT_INVALIDE", commande.getStatut() == StatutCommande.ANNULEE
                    ? "Cette commande est déjà annulée."
                    : "Cette commande ne peut plus être annulée (statut : %s).".formatted(commande.getStatut()));
        }
        annulerVerrouillee(commande, auteur, motif);
        return commande;
    }

    /** Annulation d'une commande déjà verrouillée et vérifiée (tâche d'expiration). */
    @Transactional
    public void annulerVerrouillee(Commande commande, String auteur, String motif) {
        commande.changerStatut(StatutCommande.ANNULEE, auteur, motif);
        commande.setExpireLe(null);
        stockService.liberer(commande, auteur, "Commande " + commande.getNumero() + " annulée : " + motif);
        if (commande.getCodePromo() != null) {
            reservationCodePromo.liberer(commande.getCodePromo());
        }
        for (Paiement paiement : commande.getPaiements()) {
            if (paiement.getStatut() == StatutPaiement.EN_ATTENTE) {
                paiement.setStatut(StatutPaiement.ANNULE);
            }
        }
        log.info("Commande {} annulée par {} : {}", commande.getNumero(), auteur, motif);
    }

    private Commande verrouiller(String numero) {
        return commandeDepot.findByNumeroPourMiseAJour(numero)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
    }
}
