package sn.boutique.commande.facture;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import sn.boutique.commande.facture.AvoirService.AvoirEmis;
import sn.boutique.commande.facture.EmissionFactureService.FactureEmise;
import sn.boutique.noyau.email.EmailService;
import sn.boutique.noyau.email.EmailService.PieceJointe;
import sn.boutique.noyau.format.FormatFcfa;

/**
 * Envoi de la facture ou de l'avoir par e-mail, en pièce jointe PDF, une fois la transaction d'émission validée
 * (hors de la requête du client). Seulement si le client a donné une adresse : sans e-mail, le document se télécharge
 * depuis la page de suivi. Un échec d'envoi ne touche jamais la pièce, déjà émise.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationsFacture {

    /** Au-delà, la pièce jointe n'est pas envoyée (les boîtes mail refusent les gros messages) : lien de suivi. */
    static final int TAILLE_MAX_PIECE_JOINTE = 1024 * 1024;

    private final FactureDepot factureDepot;
    private final AvoirDepot avoirDepot;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener
    void surFactureEmise(FactureEmise evenement) {
        try {
            var facture = factureDepot.trouverParNumero(evenement.numero()).orElse(null);
            if (facture == null || vide(facture.clientEmail())) {
                return;
            }
            Map<String, Object> variables = Map.of("prenom", prenom(facture.clientNom()),
                    "numero", facture.numero(), "numeroCommande", numeroCommande(evenement.commandeId()),
                    "total", FormatFcfa.formater(facture.totalTtc()));
            emailService.envoyer(facture.clientEmail(), "Votre facture " + facture.numero(), "facture-emise",
                    variables, pieces(facture.numero(), factureDepot.pdf(facture.numero()).orElse(null)));
        } catch (RuntimeException e) {
            log.error("E-mail de la facture {} non envoyé : {}", evenement.numero(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener
    void surAvoirEmis(AvoirEmis evenement) {
        try {
            var avoir = avoirDepot.trouverParNumero(evenement.numero()).orElse(null);
            var facture = factureDepot.trouverParCommande(evenement.commandeId()).orElse(null);
            if (avoir == null || facture == null || vide(facture.clientEmail())) {
                return;
            }
            Map<String, Object> variables = Map.of("prenom", prenom(facture.clientNom()),
                    "numero", avoir.numero(), "numeroFacture", facture.numero(),
                    "total", FormatFcfa.formater(avoir.totalTtc()), "motif", avoir.motif());
            emailService.envoyer(facture.clientEmail(), "Votre avoir " + avoir.numero(), "avoir-emis", variables,
                    pieces(avoir.numero(), avoirDepot.pdf(avoir.numero()).orElse(null)));
        } catch (RuntimeException e) {
            log.error("E-mail de l'avoir {} non envoyé : {}", evenement.numero(), e.getMessage());
        }
    }

    private static List<PieceJointe> pieces(String numero, byte[] pdf) {
        if (pdf == null || pdf.length > TAILLE_MAX_PIECE_JOINTE) {
            log.warn("Pièce {} non jointe à l'e-mail (absente ou trop lourde)", numero);
            return List.of();
        }
        return List.of(new PieceJointe(numero + ".pdf", pdf, "application/pdf"));
    }

    private String numeroCommande(long commandeId) {
        return factureDepot.numeroCommande(commandeId);
    }

    private static String prenom(String nom) {
        return vide(nom) ? "" : nom.strip().split("\\s+")[0];
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
