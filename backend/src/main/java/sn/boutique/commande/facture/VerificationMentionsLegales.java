package sn.boutique.commande.facture;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import sn.boutique.noyau.config.ProprietesBoutique;

/**
 * Les factures doivent porter les mentions légales du vendeur (raison sociale, NINEA, RCCM, TVA).
 * <ul>
 *   <li>Mentions manquantes : avertissement très visible dans les journaux à chaque démarrage, les factures se
 *   génèrent quand même (développement, démonstration).</li>
 *   <li>Mentions manquantes <strong>et</strong> mise en production (indexation autorisée, ou profil {@code prod}
 *   quand {@code boutique.facture.mentions-obligatoires} est vrai) : le démarrage échoue.</li>
 * </ul>
 * Le profil {@code demo} (jeu de données de démonstration) n'est jamais une production : il ne bloque pas.
 */
@Slf4j
@Component
public class VerificationMentionsLegales {

    private final ProprietesFacture facture;
    private final ProprietesBoutique boutique;
    private final Environment environnement;

    public VerificationMentionsLegales(ProprietesFacture facture, ProprietesBoutique boutique,
                                       Environment environnement) {
        this.facture = facture;
        this.boutique = boutique;
        this.environnement = environnement;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        List<String> manquantes = facture.manquantes();
        if (manquantes.isEmpty()) {
            return;
        }
        if (miseEnProduction()) {
            throw new IllegalStateException("Mentions légales des factures manquantes : " + String.join(", ", manquantes)
                    + ". Renseignez-les avant de passer en production (indexation autorisée ou profil prod).");
        }
        log.warn("""

                ==========================================================================================
                AVERTISSEMENT : mentions légales des factures non renseignées ({}).
                Les factures sont générées sans ces mentions : acceptable en développement uniquement.
                Le démarrage sera refusé en production tant qu'elles sont vides.
                ==========================================================================================""",
                String.join(", ", manquantes));
    }

    boolean miseEnProduction() {
        if (boutique.indexationAutorisee()) {
            return true;
        }
        return facture.mentionsObligatoires() && environnement.acceptsProfiles(Profiles.of("prod"))
                && !environnement.acceptsProfiles(Profiles.of("demo"));
    }
}
