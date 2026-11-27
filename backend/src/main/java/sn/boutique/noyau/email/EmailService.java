package sn.boutique.noyau.email;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
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

    /** Pièce jointe : nom de fichier, contenu et type MIME. */
    public record PieceJointe(String nom, byte[] octets, String type) {
    }

    /**
     * @param modele    nom du modèle sous {@code templates/emails/}, sans extension
     * @param variables variables du modèle ; {@code nomSite} et {@code urlSite} sont ajoutées
     */
    public void envoyer(String destinataire, String sujet, String modele, Map<String, Object> variables) {
        envoyer(destinataire, sujet, modele, variables, List.of());
    }

    /**
     * Comme {@link #envoyer(String, String, String, Map)} avec des pièces jointes. Le message est toujours
     * multipart/alternative : version texte (dérivée du HTML) et version HTML.
     */
    public void envoyer(String destinataire, String sujet, String modele, Map<String, Object> variables,
                        List<PieceJointe> pieces) {
        String html = rendre(modele, sujet, variables);
        for (int tentative = 1; tentative <= proprietes.tentatives(); tentative++) {
            try {
                expediteurMail.send(message(destinataire, sujet, html, pieces));
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

    private MimeMessage message(String destinataire, String sujet, String html, List<PieceJointe> pieces)
            throws MessagingException {
        MimeMessage message = expediteurMail.createMimeMessage();
        MimeMessageHelper aide = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                "UTF-8");
        aide.setFrom(proprietes.expediteur());
        aide.setTo(destinataire);
        aide.setSubject(sujet);
        // Texte puis HTML : multipart/alternative
        aide.setText(HtmlVersTexte.convertir(html), html);
        for (PieceJointe piece : pieces) {
            aide.addAttachment(piece.nom(), new ByteArrayResource(piece.octets()), piece.type());
        }
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
