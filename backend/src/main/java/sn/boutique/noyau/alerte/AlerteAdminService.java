package sn.boutique.noyau.alerte;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sn.boutique.noyau.email.EmailService;

/**
 * Alerte à l'équipe pour un événement qui demande une action humaine (argent à rendre, commande à traiter).
 * <p>
 * Toujours journalisée au niveau ERROR avec le repère {@code ALERTE_ADMIN} (filtrable dans la supervision), et
 * envoyée par e-mail si une adresse est configurée. Un échec d'envoi ne remonte jamais à l'appelant : l'alerte ne
 * doit pas faire échouer la transaction qui l'a déclenchée. Le détail ne contient aucune donnée personnelle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlerteAdminService {

    private final ProprietesAlertes proprietes;
    private final EmailService emailService;

    public void signaler(String sujet, String detail) {
        log.error("ALERTE_ADMIN {} : {}", sujet, detail);
        String destinataire = proprietes.destinataire();
        if (destinataire == null || destinataire.isBlank()) {
            return;
        }
        try {
            emailService.envoyer(destinataire.strip(), "[Alerte] " + sujet, "alerte-admin",
                    Map.of("titre", sujet, "detail", detail));
        } catch (RuntimeException e) {
            log.error("E-mail d'alerte non envoyé : {}", e.getMessage());
        }
    }
}
