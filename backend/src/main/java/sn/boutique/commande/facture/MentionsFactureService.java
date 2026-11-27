package sn.boutique.commande.facture;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.contenu.modele.RegimeTva;

/**
 * Mentions légales du vendeur et régime de TVA, lus dans {@code parametres_site}. Ils sont copiés sur chaque facture
 * à son émission : les modifier ne change jamais une facture déjà émise.
 */
@Service
@RequiredArgsConstructor
public class MentionsFactureService {

    private final ParametresSiteDepot parametresDepot;
    private final ApplicationEventPublisher evenements;

    /** {@code tauxTvaBp} en points de base : 1800 = 18 %. */
    public record MentionsFactureRequete(
            @Size(max = 160) String raisonSociale,
            @Size(max = 40) String ninea,
            @Size(max = 60) String rccm,
            @Size(max = 255) String adresseSiege,
            @NotNull RegimeTva regimeTva,
            @Min(0) @Max(10_000) int tauxTvaBp,
            @NotBlank @Size(max = 255) String mentionTva) {
    }

    public record MentionsFactureReponse(String raisonSociale, String ninea, String rccm, String adresseSiege,
                                         RegimeTva regimeTva, int tauxTvaBp, String mentionTva,
                                         List<String> manquantes) {

        static MentionsFactureReponse de(ParametresSite p) {
            return new MentionsFactureReponse(p.getFactureRaisonSociale(), p.getFactureNinea(), p.getFactureRccm(),
                    p.getFactureAdresseSiege(), p.getFactureRegimeTva(), p.getFactureTauxTvaBp(),
                    p.getFactureMentionTva(), MentionsFactureService.manquantes(p));
        }
    }

    /** Mentions encore vides (obligatoires sur une facture en production). */
    public static List<String> manquantes(ParametresSite p) {
        List<String> manquantes = new ArrayList<>();
        if (vide(p.getFactureRaisonSociale())) {
            manquantes.add("raison sociale");
        }
        if (vide(p.getFactureNinea())) {
            manquantes.add("NINEA");
        }
        if (vide(p.getFactureRccm())) {
            manquantes.add("RCCM");
        }
        if (vide(p.getFactureAdresseSiege())) {
            manquantes.add("adresse du siège");
        }
        return manquantes;
    }

    @Transactional(readOnly = true)
    public MentionsFactureReponse lire() {
        return MentionsFactureReponse.de(parametresDepot.lire());
    }

    @Transactional
    public MentionsFactureReponse modifier(MentionsFactureRequete requete) {
        ParametresSite p = parametresDepot.lire();
        p.setFactureRaisonSociale(nettoyer(requete.raisonSociale()));
        p.setFactureNinea(nettoyer(requete.ninea()));
        p.setFactureRccm(nettoyer(requete.rccm()));
        p.setFactureAdresseSiege(nettoyer(requete.adresseSiege()));
        p.setFactureRegimeTva(requete.regimeTva());
        p.setFactureTauxTvaBp(requete.tauxTvaBp());
        p.setFactureMentionTva(requete.mentionTva().strip());
        parametresDepot.saveAndFlush(p);
        evenements.publishEvent(new CatalogueModifie("paramètres du site : mentions légales des factures"));
        return MentionsFactureReponse.de(p);
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static String nettoyer(String valeur) {
        return vide(valeur) ? null : valeur.strip();
    }
}
