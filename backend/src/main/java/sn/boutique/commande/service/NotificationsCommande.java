package sn.boutique.commande.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.service.CommandeService.CommandeCreee;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.noyau.email.EmailService;
import sn.boutique.noyau.email.ProprietesEmail;
import sn.boutique.noyau.format.FormatFcfa;
import sn.boutique.noyau.notification.NotificationClient;

/**
 * Après la création d'une commande (une fois la transaction validée, hors de la requête du client) :
 * <ul>
 *   <li>e-mail « Commande reçue » avec le lien de suivi, <strong>si le client a donné une adresse</strong> ;</li>
 *   <li>message court (WhatsApp/SMS) via {@link NotificationClient}, toujours, pour que le client retrouve sa
 *   commande même s'il perd l'onglet. Aucun prestataire n'est branché : la notification est seulement journalisée.</li>
 * </ul>
 * Un échec d'envoi ne touche jamais la commande.
 */
@Slf4j
@Component
public class NotificationsCommande {

    private final CommandeDepot commandeDepot;
    private final EmailService emailService;
    private final NotificationClient notificationClient;
    private final ProprietesBoutique proprietesBoutique;
    private final ProprietesEmail proprietesEmail;
    private final TransactionTemplate transaction;

    public NotificationsCommande(CommandeDepot commandeDepot, EmailService emailService,
                                 NotificationClient notificationClient, ProprietesBoutique proprietesBoutique,
                                 ProprietesEmail proprietesEmail, PlatformTransactionManager gestionnaireTransactions) {
        this.commandeDepot = commandeDepot;
        this.emailService = emailService;
        this.notificationClient = notificationClient;
        this.proprietesBoutique = proprietesBoutique;
        this.proprietesEmail = proprietesEmail;
        this.transaction = new TransactionTemplate(gestionnaireTransactions);
    }

    @Async
    @TransactionalEventListener
    void surCommandeCreee(CommandeCreee evenement) {
        envoyerCommandeRecue(evenement.commandeId());
    }

    /** Public pour les tests : l'écouteur asynchrone ne se prête pas à l'attente. */
    public void envoyerCommandeRecue(long commandeId) {
        Optional<Message> message = transaction.execute(statut -> commandeDepot.findById(commandeId)
                .map(this::composer));
        message.ifPresent(m -> {
            try {
                if (m.email() != null) {
                    emailService.envoyer(m.email(), "Commande " + m.numero() + " reçue", "commande-recue",
                            m.variables());
                }
            } catch (RuntimeException e) {
                log.error("E-mail de la commande {} non envoyé", m.numero(), e);
            }
            try {
                notificationClient.envoyer(m.telephone(), "%s : commande %s reçue (%s). Suivi : %s".formatted(
                        m.nomSite(), m.numero(), m.totalTexte(), m.lienSuivi()));
            } catch (RuntimeException e) {
                log.error("Notification de la commande {} non envoyée", m.numero(), e);
            }
        });
    }

    private Message composer(Commande commande) {
        String lien = proprietesBoutique.urlSiteSansBarre() + "/suivi/" + commande.getJetonSuivi();
        boolean aLaLivraison = commande.getModePaiement() == ModePaiement.A_LA_LIVRAISON;
        List<Map<String, Object>> lignes = commande.getLignes().stream()
                .map(l -> Map.<String, Object>of("nom", l.getNomProduit(), "variante", l.getLibelleVariante(),
                        "quantite", l.getQuantite(), "total", FormatFcfa.formater(l.getTotalLigne())))
                .toList();
        Map<String, Object> variables = Map.of(
                "prenom", SuiviCommandeService.prenom(commande.getNomClient()) == null ? ""
                        : SuiviCommandeService.prenom(commande.getNomClient()),
                "numero", commande.getNumero(),
                "lignes", lignes,
                "total", FormatFcfa.formater(commande.getTotal()),
                "retrait", commande.getModeLivraison() == ModeLivraison.RETRAIT_MAGASIN,
                "aLaLivraison", aLaLivraison,
                "lienSuivi", lien);
        return new Message(commande.getNumero(), commande.getEmailClient(), commande.getTelephoneClient(), lien,
                FormatFcfa.formater(commande.getTotal()), proprietesEmail.nomSite(), variables);
    }

    private record Message(String numero, String email, String telephone, String lienSuivi, String totalTexte,
                           String nomSite, Map<String, Object> variables) {
    }
}
