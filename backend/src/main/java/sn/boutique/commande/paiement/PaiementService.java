package sn.boutique.commande.paiement;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.authentification.securite.GenerateurJetons;
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
 * <strong>Aucun appel au prestataire ne se fait avec une transaction ou un verrou de base ouvert.</strong> Trois temps,
 * chacun dans sa propre transaction courte, l'appel réseau se faisant entre les deux :
 * <ol>
 *   <li><em>Réservation</em> (verrou {@code FOR UPDATE} bref, puis commit) : si une facture valide existe, elle est
 *   renvoyée ; si une autre requête est déjà en train d'en créer une (réservation en cours, {@code
 *   reservationPaiement}), on attend son résultat ; sinon la commande est marquée « facture en cours de création »
 *   jusqu'à une échéance courte ;</li>
 *   <li><em>Appel au prestataire</em>, hors transaction (délais courts du client HTTP) ;</li>
 *   <li><em>Enregistrement</em> de la facture (nouvelle transaction, nouveau verrou bref) et fin de la réservation.
 *   En cas d'échec de l'appel, la réservation est levée aussitôt.</li>
 * </ol>
 * Une commande n'a ainsi jamais deux factures payables : deux reprises simultanées créent une seule facture, la
 * seconde réutilise le résultat de la première. Une réservation périmée (serveur arrêté pendant l'appel) est reprise
 * par la requête suivante. Si malgré tout deux factures sont payées, {@link ReceptionPaiementService} marque la
 * seconde « à rembourser ».
 * <p>
 * Aucun stock n'est touché : il est réservé une fois pour toutes à la création de la commande.
 * L'adresse de retour donnée au prestataire porte un jeton de retour propre à la facture (seule son empreinte est
 * stockée), jamais le jeton de suivi : celui-ci peut être renouvelé.
 */
@Slf4j
@Service
public class PaiementService {

    /** Marge d'attente au-delà de la durée de la réservation, pour laisser le temps d'enregistrer le résultat. */
    private static final int ITERATIONS_MARGE = 10;

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
     * La carte bancaire n'est pas proposée tant que le prestataire n'indique pas qu'elle est activée sur le compte
     * (engagement de la 4c) : le site ne l'annonce donc nulle part.
     */
    public boolean carteDisponible() {
        return false;
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

    /** Résultat de la réservation : facture existante, réservation obtenue, ou commande réservée par un autre. */
    private sealed interface Etape {
    }

    private record Existante(String url) implements Etape {
    }

    private record Reservee(DemandePaiement demande, String jetonRetour) implements Etape {
    }

    private record Occupee() implements Etape {
    }

    private String initier(String numero) {
        FournisseurPaiement fournisseur = fournisseurEnLigne()
                .orElseThrow(() -> new PaiementIndisponibleException("Aucun prestataire de paiement configuré"));
        long pause = Math.max(1, proprietesCommande.pauseAttentePaiement().toMillis());
        long iterations = proprietesCommande.reservationPaiement().toMillis() / pause + ITERATIONS_MARGE;
        for (long i = 0; i < iterations; i++) {
            Etape etape = transaction.execute(statut -> reserver(numero));
            switch (etape) {
                case Existante existante -> {
                    return existante.url();
                }
                case Reservee reservee -> {
                    return creerFacture(fournisseur, numero, reservee);
                }
                case Occupee occupee -> attendre(pause);
            }
        }
        throw new PaiementIndisponibleException("Création de la facture déjà en cours pour la commande " + numero);
    }

    /** Temps 1 (transaction brève, verrou sur la commande). */
    private Etape reserver(String numero) {
        Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", numero));
        Instant maintenant = horloge.instant();
        if (commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT || !commande.getModePaiement().enLigne()) {
            throw new ConflitException("PAIEMENT_NON_REPRENABLE", "Cette commande n'attend plus de paiement en ligne.");
        }
        if (commande.getExpireLe() != null && !commande.getExpireLe().isAfter(maintenant)) {
            throw new ConflitException("COMMANDE_EXPIREE",
                    "Le délai de paiement est dépassé : la commande va être annulée. Passez une nouvelle commande.");
        }
        Instant limite = maintenant.minus(proprietesCommande.validiteFacture());
        Optional<Paiement> valide = facturesEnAttente(commande).stream()
                .filter(p -> p.getDateCreation().isAfter(limite)).findFirst();
        if (valide.isPresent()) {
            return new Existante(valide.get().getUrlPaiement());
        }
        if (commande.getPaiementEnCoursJusqua() != null && commande.getPaiementEnCoursJusqua().isAfter(maintenant)) {
            return new Occupee();
        }
        commande.setPaiementEnCoursJusqua(maintenant.plus(proprietesCommande.reservationPaiement()));
        String jetonRetour = GenerateurJetons.generer();
        return new Reservee(demande(commande, jetonRetour), jetonRetour);
    }

    /** Temps 2 (appel réseau, hors transaction) puis temps 3 (enregistrement). */
    private String creerFacture(FournisseurPaiement fournisseur, String numero, Reservee reservee) {
        SessionPaiement session;
        try {
            session = fournisseur.initier(reservee.demande());
        } catch (RuntimeException e) {
            levee(numero);
            throw e;
        }
        String url = transaction.execute(statut -> {
            Commande commande = commandeDepot.findByNumeroPourMiseAJour(numero).orElseThrow();
            // Les factures précédentes sont expirées : elles ne doivent plus être proposées
            facturesEnAttente(commande).forEach(p -> p.setStatut(StatutPaiement.ANNULE));
            Paiement paiement = new Paiement(commande, fournisseur.type(), commande.getTotal());
            paiement.setReferenceExterne(session.referenceExterne());
            paiement.setUrlPaiement(session.urlPaiement());
            paiement.setEmpreinteJetonRetour(GenerateurJetons.empreinte(reservee.jetonRetour()));
            boolean encoreEnAttente = commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT;
            if (!encoreEnAttente) {
                // Commande annulée pendant l'appel : la facture existe chez le prestataire, on la garde pour
                // reconnaître un éventuel paiement tardif, mais on ne la propose pas
                paiement.setStatut(StatutPaiement.ANNULE);
            }
            paiementDepot.save(paiement);
            commande.setPaiementEnCoursJusqua(null);
            log.info("Facture de paiement créée pour la commande {}", numero);
            return encoreEnAttente ? session.urlPaiement() : null;
        });
        if (url == null) {
            throw new ConflitException("PAIEMENT_NON_REPRENABLE", "Cette commande n'attend plus de paiement en ligne.");
        }
        return url;
    }

    /** Le prestataire n'a pas répondu : la réservation est levée tout de suite, la requête suivante peut réessayer. */
    private void levee(String numero) {
        try {
            transaction.executeWithoutResult(statut -> commandeDepot.findByNumeroPourMiseAJour(numero)
                    .ifPresent(c -> c.setPaiementEnCoursJusqua(null)));
        } catch (RuntimeException e) {
            log.error("Réservation de paiement non levée pour la commande {} : elle expirera d'elle-même", numero, e);
        }
    }

    private List<Paiement> facturesEnAttente(Commande commande) {
        return paiementDepot.findByCommandeIdOrderByDateCreationDesc(commande.getId()).stream()
                .filter(p -> p.getStatut() == StatutPaiement.EN_ATTENTE && p.getUrlPaiement() != null)
                .toList();
    }

    private DemandePaiement demande(Commande commande, String jetonRetour) {
        String base = proprietesBoutique.urlSiteSansBarre();
        String retour = base + "/commande/retour/" + jetonRetour;
        return new DemandePaiement(commande.getNumero(), commande.getTotal(), commande.getModePaiement(),
                commande.getNomClient(), commande.getTelephoneClient(), commande.getEmailClient(),
                commande.getLignes().stream()
                        .map(l -> new Article(l.getNomProduit() + " — " + l.getLibelleVariante(), l.getQuantite(),
                                l.getPrixUnitaire()))
                        .toList(),
                retour, retour + "?annule=1", base + "/api/paiements/paydunya/webhook");
    }

    private static void attendre(long millisecondes) {
        try {
            Thread.sleep(millisecondes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaiementIndisponibleException("Attente interrompue");
        }
    }

    private Optional<FournisseurPaiement> fournisseurEnLigne() {
        return fournisseurs.stream()
                .filter(f -> f.type() == TypeFournisseurPaiement.PAYDUNYA && f.disponible())
                .findFirst();
    }
}
