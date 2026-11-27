package sn.boutique.commande.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.dto.SuiviCommandeReponse;
import sn.boutique.commande.dto.SuiviCommandeReponse.Etape;
import sn.boutique.commande.dto.SuiviCommandeReponse.Ligne;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.paiement.PaiementService;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Page de suivi d'une commande par son lien (sans compte).
 * <p>
 * Le jeton fait 256 bits et seule son empreinte SHA-256 est en base : introuvable = 404, sans distinguer « jamais existé » de « format invalide ». La réponse
 * ne contient que ce dont le client a besoin, avec le téléphone et l'adresse partiellement masqués : quelqu'un qui
 * verrait le lien par-dessus l'épaule ou dans un message transféré n'apprend ni le numéro complet ni la rue.
 */
@Service
@RequiredArgsConstructor
public class SuiviCommandeService {

    private static final Pattern FORMAT_JETON = Pattern.compile("[A-Za-z0-9_-]{43}");
    /** Statuts depuis lesquels la vente est acquise : l'événement « achat » des régies peut partir. */
    private static final Set<StatutCommande> VENTE_ACQUISE = EnumSet.of(StatutCommande.PAYEE,
            StatutCommande.CONFIRMEE, StatutCommande.EN_PREPARATION, StatutCommande.EXPEDIEE, StatutCommande.LIVREE);

    private final CommandeDepot commandeDepot;
    private final PaiementService paiementService;
    private final JdbcClient jdbc;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public SuiviCommandeReponse lire(String jeton) {
        Commande commande = trouver(jeton);
        boolean peutReprendre = commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT
                && commande.getModePaiement().enLigne()
                && commande.getExpireLe() != null && commande.getExpireLe().isAfter(horloge.instant())
                && paiementService.enLigneDisponible();
        List<Ligne> lignes = commande.getLignes().stream()
                .map(l -> new Ligne(l.getSku(), l.getNomProduit(), l.getLibelleVariante(), l.getImageUrl(),
                        l.getPrixUnitaire(), l.getQuantite(), l.getTotalLigne()))
                .toList();
        List<Etape> etapes = commande.getHistorique().stream()
                .map(h -> new Etape(h.getNouveauStatut(), h.getDateCreation()))
                .toList();
        return new SuiviCommandeReponse(commande.getNumero(), commande.getStatut(), commande.getDateCreation(),
                commande.getExpireLe(), prenom(commande.getNomClient()), telephoneMasque(commande.getTelephoneClient()),
                commande.getModeLivraison(),
                commande.getZoneLivraison() == null ? null : commande.getZoneLivraison().getNom(),
                adresseMasquee(commande), commande.getModePaiement(), lignes, commande.getSousTotal(),
                commande.getFraisLivraison(), commande.getRemise(), commande.getTotal(), commande.getCodePromo(), etapes,
                peutReprendre, commande.getAchatSuiviLe() == null && VENTE_ACQUISE.contains(commande.getStatut()));
    }

    /**
     * « Reprendre le paiement » : nouvelle facture chez le prestataire pour la même commande, tant qu'elle n'a
     * pas expiré. Le stock reste celui de la réservation initiale.
     *
     * @return page de paiement du prestataire
     */
    public String reprendrePaiement(String jeton) {
        return paiementService.reprendre(trouver(jeton).getNumero());
    }

    /**
     * Revendique l'émission de l'événement « achat » (pixel, dataLayer) : une seule fois par commande, même si la
     * page de suivi est ouverte dans deux onglets. Seul l'appelant qui reçoit {@code true} émet l'événement.
     * Refusé tant que la vente n'est pas acquise (commande payée ou confirmée).
     */
    @Transactional
    public boolean revendiquerAchat(String jeton) {
        trouver(jeton);
        return jdbc.sql("""
                        update commande set achat_suivi_le = :maintenant
                         where empreinte_jeton_suivi = :empreinte and achat_suivi_le is null
                           and statut in ('PAYEE', 'CONFIRMEE', 'EN_PREPARATION', 'EXPEDIEE', 'LIVREE')
                        """)
                .param("maintenant", OffsetDateTime.ofInstant(horloge.instant(), ZoneOffset.UTC))
                .param("empreinte", GenerateurJetons.empreinte(jeton))
                .update() == 1;
    }

    private Commande trouver(String jeton) {
        if (jeton == null || !FORMAT_JETON.matcher(jeton).matches()) {
            throw new RessourceIntrouvableException("Commande", "suivi");
        }
        return commandeDepot.findByEmpreinteJetonSuivi(GenerateurJetons.empreinte(jeton))
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", "suivi"));
    }

    /** « Awa Diop » → « Awa » : le prénom suffit pour dire merci. */
    static String prenom(String nomComplet) {
        if (nomComplet == null || nomComplet.isBlank()) {
            return null;
        }
        return nomComplet.strip().split("\\s+")[0];
    }

    /** +221771234567 → « +221 77 ••• •• 67 » */
    static String telephoneMasque(String telephone) {
        if (telephone == null || telephone.length() != 13) {
            return null;
        }
        return "+221 " + telephone.substring(4, 6) + " ••• •• " + telephone.substring(11);
    }

    /** Quartier et ville seulement : la rue et le point de repère restent cachés. */
    static String adresseMasquee(Commande commande) {
        if (commande.getModeLivraison() == ModeLivraison.RETRAIT_MAGASIN) {
            return null;
        }
        var adresse = commande.getAdresseLivraison();
        if (adresse == null) {
            return null;
        }
        return adresse.getQuartier() + ", " + adresse.getVille() + " (adresse complète communiquée au livreur)";
    }
}
