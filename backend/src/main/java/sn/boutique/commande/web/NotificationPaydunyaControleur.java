package sn.boutique.commande.web;

import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.PaiementIndisponibleException;
import sn.boutique.commande.paiement.PaydunyaFournisseur;

/**
 * Notification de paiement de PayDunya (IPN), adresse {@code callback_url} donnée à la création de la facture.
 * <p>
 * PayDunya envoie un POST {@code application/x-www-form-urlencoded} dont le contenu est sous la clé {@code data}
 * (champs {@code data[hash]}, {@code data[status]}, {@code data[invoice][token]}…). Le {@code hash} est le SHA-512 de
 * notre clé principale : <strong>fixe, il ne protège pas le contenu</strong>. La notification ne sert donc que de
 * signal : après le contrôle du hash, la facture est <strong>toujours revérifiée</strong> auprès de PayDunya
 * ({@link ConfirmationPaiementService}), et ni le statut, ni le montant, ni le numéro de commande du corps ne sont crus.
 * <p>
 * Réponses : 401 hash invalide ; 400 jeton absent ; 503 PayDunya injoignable pour la revérification (PayDunya pourra
 * renvoyer la notification, et la tâche d'expiration revérifie de toute façon) ; 200 sinon, y compris pour une facture
 * inconnue (rien à faire, inutile d'en provoquer le renvoi). SUPPOSE : comportement de PayDunya face à un code non 2xx
 * (renvoi ou non), à observer au premier test réel.
 */
@Slf4j
@RestController
@RequestMapping("/api/paiements/paydunya")
@RequiredArgsConstructor
public class NotificationPaydunyaControleur {

    private final PaydunyaFournisseur paydunya;
    private final ConfirmationPaiementService confirmation;

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> notification(@RequestParam Map<String, String> parametres) {
        if (!paydunya.notificationAuthentique(parametres.get("data[hash]"))) {
            // Rien du contenu n'est journalisé : il vient d'un inconnu
            log.warn("Notification PayDunya refusée : hash invalide");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String jeton = parametres.get("data[invoice][token]");
        if (jeton == null || jeton.isBlank() || jeton.length() > 200) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Long> paiementId = confirmation.idParReference(TypeFournisseurPaiement.PAYDUNYA, jeton);
        if (paiementId.isEmpty()) {
            log.warn("Notification PayDunya authentique pour une facture inconnue");
            return ResponseEntity.ok().build();
        }
        try {
            ConfirmationPaiementService.Resultat resultat = confirmation.confirmer(paiementId.get());
            log.info("Notification PayDunya traitée : {}", resultat);
            return ResponseEntity.ok().build();
        } catch (PaiementIndisponibleException e) {
            log.warn("Notification PayDunya : revérification impossible ({})", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
