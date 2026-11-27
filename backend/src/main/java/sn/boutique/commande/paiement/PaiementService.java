package sn.boutique.commande.paiement;

import java.time.Clock;
import java.time.Instant;
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
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.Article;
import sn.boutique.commande.paiement.FournisseurPaiement.DemandePaiement;
import sn.boutique.commande.paiement.FournisseurPaiement.SessionPaiement;
import sn.boutique.commande.service.ProprietesCommande;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Démarrage et reprise du paiement en ligne d'une commande en attente.
 * <p>
 * <strong>Une commande n'a jamais deux factures payables en même temps</strong> : deux factures permettraient au
 * client de payer deux fois. Toute la démarche se fait donc sous le verrou de la commande
 * ({@code SELECT ... FOR UPDATE}) :
 * <ol>
 *   <li>une facture encore valide (en attente, créée depuis moins de {@code validiteFacture}) est réutilisée ;</li>
 *   <li>sinon seulement, une nouvelle est demandée au prestataire, et les anciennes factures en attente sont
 *   abandonnées (elles sont expirées).</li>
 * </ol>
 * Deux « Reprendre le paiement » simultanés s'exécutent l'un après l'autre : le second trouve la facture du
 * premier. Contrepartie : le verrou est gardé pendant l'appel au prestataire (le client attend de toute façon sa
 * page de paiement) ; l'appel doit donc avoir un délai court. Aucun stock n'est touché : il est réservé une fois
 * pour toutes à la création de la commande. Si malgré tout deux factures sont payées (facture réputée expirée mais
 * encore ouverte chez le prestataire), {@link ReceptionPaiementService} marque la seconde « à rembourser ».
 */
@Slf4j
@Service
public class PaiementService {

    private final List<FournisseurPaiement> fournisseurs;
    private final CommandeDepot commandeDepot;
    private final PaiementDepot paiementDepot;
    private final ProprietesBoutique proprietesBoutique;
    private final ProprietesCommande proprietesCommande;
    private final Clock horloge;
    private final TransactionTemplate transaction;

    public PaiementService(List<FournisseurPaiement> fournisseurs, CommandeDepot commandeDepot,
                           PaiementDepot paiementDepot, ProprietesBoutique proprietesBoutique,
                           ProprietesCommande proprietesCommande, Clock horloge,
                           PlatformTransactionManager gestionnaireTransactions) {
        this.fournisseurs = fournisseurs;
        this.commandeDepot = commandeDepot;
        this.paiementDepot = paiementDepot;
        this.proprietesBoutique = proprietesBoutique;
        this.proprietesCommande = proprietesCommande;
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
        return initier(numero);
    }

    /**
     * « Reprendre le paiement » : la facture encore valide si elle existe, sinon une nouvelle facture chez le
     * prestataire pour la même commande, sans nouveau stock.
     */
    public String reprendre(String numero) {
        return initier(numero);
    }

    private String initier(String numero) {
        FournisseurPaiement fournisseur = fournisseurEnLigne()
                .orElseThrow(() -> new PaiementIndisponibleException("Aucun prestataire de paiement configuré"));
        return transaction.execute(statut -> {
            Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero)
                    .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
            Instant maintenant = horloge.instant();
            if (commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT || !commande.getModePaiement().enLigne()) {
                throw new ConflitException("PAIEMENT_NON_REPRENABLE",
                        "Cette commande n'attend plus de paiement en ligne.");
            }
            if (commande.getExpireLe() != null && !commande.getExpireLe().isAfter(maintenant)) {
                throw new ConflitException("COMMANDE_EXPIREE",
                        "Le délai de paiement est dépassé : la commande va être annulée. Passez une nouvelle commande.");
            }
            List<Paiement> enAttente = paiementDepot.findByCommandeIdOrderByDateCreationDesc(commande.getId()).stream()
                    .filter(p -> p.getStatut() == StatutPaiement.EN_ATTENTE && p.getUrlPaiement() != null)
                    .toList();
            Instant limite = maintenant.minus(proprietesCommande.validiteFacture());
            Optional<Paiement> valide = enAttente.stream().filter(p -> p.getDateCreation().isAfter(limite)).findFirst();
            if (valide.isPresent()) {
                return valide.get().getUrlPaiement();
            }

            SessionPaiement session = fournisseur.initier(demande(commande));
            // Les factures précédentes sont expirées : elles ne doivent plus être proposées
            enAttente.forEach(p -> p.setStatut(StatutPaiement.ANNULE));
            Paiement paiement = new Paiement(commande, fournisseur.type(), commande.getTotal());
            paiement.setReferenceExterne(session.referenceExterne());
            paiement.setUrlPaiement(session.urlPaiement());
            paiementDepot.save(paiement);
            log.info("Facture de paiement créée pour la commande {}", numero);
            return session.urlPaiement();
        });
    }

    private DemandePaiement demande(Commande commande) {
        String base = proprietesBoutique.urlSiteSansBarre();
        String suivi = base + "/suivi/" + commande.getJetonSuivi();
        return new DemandePaiement(commande.getNumero(), commande.getTotal(), commande.getModePaiement(),
                commande.getNomClient(), commande.getTelephoneClient(), commande.getEmailClient(),
                commande.getLignes().stream()
                        .map(l -> new Article(l.getNomProduit() + " — " + l.getLibelleVariante(), l.getQuantite(),
                                l.getPrixUnitaire()))
                        .toList(),
                suivi + "?retour=paiement", suivi + "?retour=annule", base + "/api/paiements/paydunya/webhook");
    }

    private Optional<FournisseurPaiement> fournisseurEnLigne() {
        return fournisseurs.stream()
                .filter(f -> f.type() == TypeFournisseurPaiement.PAYDUNYA && f.disponible())
                .findFirst();
    }
}
