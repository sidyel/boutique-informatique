package sn.boutique.commande.paiement;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.Article;
import sn.boutique.commande.paiement.FournisseurPaiement.DemandePaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.SessionPaiement;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Démarrage du paiement en ligne d'une commande en attente.
 * <p>
 * L'appel au prestataire se fait <strong>hors transaction</strong> : aucune connexion ni verrou de base n'est
 * gardé pendant un appel réseau. Trois temps : lecture de la commande, appel du prestataire, enregistrement du
 * paiement. Aucun stock n'est touché : il est réservé une fois pour toutes à la création de la commande.
 */
@Slf4j
@Service
public class PaiementService {

    private final List<FournisseurPaiement> fournisseurs;
    private final CommandeDepot commandeDepot;
    private final PaiementDepot paiementDepot;
    private final ProprietesBoutique proprietesBoutique;
    private final Clock horloge;
    private final TransactionTemplate transaction;

    public PaiementService(List<FournisseurPaiement> fournisseurs, CommandeDepot commandeDepot,
                           PaiementDepot paiementDepot, ProprietesBoutique proprietesBoutique, Clock horloge,
                           PlatformTransactionManager gestionnaireTransactions) {
        this.fournisseurs = fournisseurs;
        this.commandeDepot = commandeDepot;
        this.paiementDepot = paiementDepot;
        this.proprietesBoutique = proprietesBoutique;
        this.horloge = horloge;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    /** Mobile money et carte ne sont proposés que si le prestataire est configuré. */
    public boolean enLigneDisponible() {
        return fournisseurEnLigne().isPresent();
    }

    /**
     * Page de paiement d'une commande qui vient d'être créée : celle d'un paiement déjà démarré s'il existe
     * (requête rejouée, double appui), sinon un nouveau.
     */
    public String demarrer(String numero) {
        return initier(numero, false);
    }

    /** « Reprendre le paiement » : nouvelle facture chez le prestataire pour la même commande, sans nouveau stock. */
    public String reprendre(String numero) {
        return initier(numero, true);
    }

    private String initier(String numero, boolean nouveau) {
        FournisseurPaiement fournisseur = fournisseurEnLigne()
                .orElseThrow(() -> new PaiementIndisponibleException("Aucun prestataire de paiement configuré"));

        record Preparation(DemandePaiement demande, String urlExistante) {
        }
        Preparation preparation = transaction.execute(statut -> {
            Commande commande = commandeDepot.findByNumero(numero)
                    .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
            if (commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT || !commande.getModePaiement().enLigne()) {
                throw new ConflitException("PAIEMENT_NON_REPRENABLE",
                        "Cette commande n'attend plus de paiement en ligne.");
            }
            if (commande.getExpireLe() != null && !commande.getExpireLe().isAfter(horloge.instant())) {
                throw new ConflitException("COMMANDE_EXPIREE",
                        "Le délai de paiement est dépassé : la commande va être annulée. Passez une nouvelle commande.");
            }
            Optional<String> existante = nouveau ? Optional.empty()
                    : paiementDepot.findByCommandeIdOrderByDateCreationDesc(commande.getId()).stream()
                            .filter(p -> p.getStatut() == sn.boutique.commande.modele.StatutPaiement.EN_ATTENTE)
                            .map(Paiement::getUrlPaiement)
                            .filter(url -> url != null)
                            .findFirst();
            String base = proprietesBoutique.urlSiteSansBarre();
            String suivi = base + "/suivi/" + commande.getJetonSuivi();
            DemandePaiement demande = new DemandePaiement(commande.getNumero(), commande.getTotal(),
                    commande.getModePaiement(), commande.getNomClient(), commande.getTelephoneClient(),
                    commande.getEmailClient(),
                    commande.getLignes().stream()
                            .map(l -> new Article(l.getNomProduit() + " — " + l.getLibelleVariante(), l.getQuantite(),
                                    l.getPrixUnitaire()))
                            .toList(),
                    suivi + "?retour=paiement", suivi + "?retour=annule", base + "/api/paiements/paydunya/webhook");
            return new Preparation(demande, existante.orElse(null));
        });
        if (preparation.urlExistante() != null) {
            return preparation.urlExistante();
        }

        SessionPaiement session = fournisseur.initier(preparation.demande());

        transaction.executeWithoutResult(statut -> {
            Commande commande = commandeDepot.findByNumero(numero).orElseThrow();
            Paiement paiement = new Paiement(commande, fournisseur.type(), commande.getTotal());
            paiement.setReferenceExterne(session.referenceExterne());
            paiement.setUrlPaiement(session.urlPaiement());
            paiementDepot.save(paiement);
        });
        log.info("Paiement en ligne démarré pour la commande {}", numero);
        return session.urlPaiement();
    }

    private Optional<FournisseurPaiement> fournisseurEnLigne() {
        return fournisseurs.stream()
                .filter(f -> f.type() == TypeFournisseurPaiement.PAYDUNYA && f.disponible())
                .findFirst();
    }
}
