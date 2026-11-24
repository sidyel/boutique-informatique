package sn.boutique.noyau.email;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import sn.boutique.noyau.config.ProprietesBoutique;

/**
 * Envoi des e-mails transactionnels, mis en forme par les modèles Thymeleaf de {@code templates/emails/}.
 * <p>
 * Appelé depuis des écouteurs asynchrones (après validation de la transaction) : un serveur SMTP lent
 * ou indisponible ne ralentit ni ne fait échouer la requête de l'utilisateur. Plusieurs tentatives,
 * puis abandon journalisé (sans le contenu, qui peut contenir un lien secret).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Locale FRANCAIS = Locale.FRENCH;

    private final JavaMailSender expediteurMail;
    private final ITemplateEngine moteurModeles;
    private final ProprietesEmail proprietes;
    private final ProprietesBoutique proprietesBoutique;

    /**
     * @param modele    nom du modèle sous {@code templates/emails/}, sans extension
     * @param variables variables du modèle ; {@code nomSite} et {@code urlSite} sont ajoutées
     */
    public void envoyer(String destinataire, String sujet, String modele, Map<String, Object> variables) {
        String html = rendre(modele, sujet, variables);
        for (int tentative = 1; tentative <= proprietes.tentatives(); tentative++) {
            try {
                expediteurMail.send(message(destinataire, sujet, html));
                log.info("E-mail « {} » envoyé (modèle {})", sujet, modele);
                return;
            } catch (MailException | MessagingException e) {
                if (tentative == proprietes.tentatives()) {
                    log.error("Échec définitif de l'envoi de l'e-mail (modèle {}) après {} tentatives : {}",
                            modele, tentative, e.getMessage());
                    return;
                }
                log.warn("Échec de l'envoi de l'e-mail (modèle {}), tentative {} : {}", modele, tentative, e.getMessage());
                attendre(Duration.ofSeconds(2L * tentative));
            }
        }
    }

    String rendre(String modele, String sujet, Map<String, Object> variables) {
        Map<String, Object> toutes = new HashMap<>(variables);
        toutes.put("sujet", sujet);
        toutes.put("nomSite", proprietes.nomSite());
        toutes.put("urlSite", proprietesBoutique.urlSite());
        return moteurModeles.process("emails/" + modele, new Context(FRANCAIS, toutes));
    }

    private MimeMessage message(String destinataire, String sujet, String html) throws MessagingException {
        MimeMessage message = expediteurMail.createMimeMessage();
        MimeMessageHelper aide = new MimeMessageHelper(message, false, "UTF-8");
        aide.setFrom(proprietes.expediteur());
        aide.setTo(destinataire);
        aide.setSubject(sujet);
        aide.setText(html, true);
        return message;
    }

    private static void attendre(Duration duree) {
        try {
            Thread.sleep(duree);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
