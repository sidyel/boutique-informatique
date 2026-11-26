package sn.boutique.noyau.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;
import sn.boutique.noyau.config.ProprietesBoutique;

class EmailServiceTest {

    private final JavaMailSender expediteur = mock(JavaMailSender.class);
    private final ITemplateEngine modeles = mock(ITemplateEngine.class);
    private final EmailService service = new EmailService(expediteur, modeles,
            new ProprietesEmail("Boutique <no-reply@exemple.sn>", "Boutique", 2),
            new ProprietesBoutique("https://boutique.sn", List.of("https://boutique.sn"), false));

    @Test
    void reessaieApresUnEchecDuServeurSmtp() throws Exception {
        when(expediteur.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        when(modeles.process(any(String.class), any(IContext.class))).thenReturn("<p>Bonjour</p>");
        doThrow(new MailSendException("SMTP indisponible")).doNothing().when(expediteur).send(any(MimeMessage.class));

        service.envoyer("awa@exemple.sn", "Sujet", "verification-email", Map.of());

        verify(expediteur, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void abandonneSansExceptionApresLaDerniereTentative() throws Exception {
        when(expediteur.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        when(modeles.process(any(String.class), any(IContext.class))).thenReturn("<p>Bonjour</p>");
        doThrow(new MailSendException("SMTP indisponible")).when(expediteur).send(any(MimeMessage.class));

        service.envoyer("awa@exemple.sn", "Sujet", "verification-email", Map.of());

        verify(expediteur, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void ajouteLeNomEtLUrlDuSiteAuxVariables() {
        when(modeles.process(any(String.class), any(IContext.class))).thenAnswer(i -> {
            IContext contexte = i.getArgument(1);
            return contexte.getVariable("nomSite") + "|" + contexte.getVariable("urlSite") + "|" + contexte.getVariable("sujet");
        });

        assertThat(service.rendre("verification-email", "Sujet", Map.of())).isEqualTo("Boutique|https://boutique.sn|Sujet");
    }
}
