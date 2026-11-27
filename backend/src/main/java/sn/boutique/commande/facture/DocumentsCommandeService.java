package sn.boutique.commande.facture;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Téléchargement des factures et avoirs, par trois chemins : le lien de suivi (client sans compte, y compris sans
 * e-mail), le compte du client connecté (sa propre commande seulement), et le personnel (par numéro de pièce).
 * <p>
 * Une pièce qui n'existe pas, qui n'appartient pas au demandeur ou dont le lien est invalide donne toujours la même
 * erreur 404 : on ne révèle pas l'existence d'une commande ou d'une facture à qui n'y a pas droit.
 */
@Service
@RequiredArgsConstructor
public class DocumentsCommandeService {

    private static final Pattern FORMAT_JETON = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final CommandeDepot commandeDepot;
    private final FactureDepot factureDepot;
    private final AvoirDepot avoirDepot;

    /** Un PDF prêt à servir. */
    public record Document(String nomFichier, byte[] octets) {
    }

    /** Numéros des pièces émises pour une commande. */
    public record Pieces(String numeroFacture, List<String> numerosAvoirs) {
        public static final Pieces AUCUNE = new Pieces(null, List.of());
    }

    // ------------------------------------------------------------------ par lien de suivi

    @Transactional(readOnly = true)
    public Document factureParJeton(String jeton) {
        return facture(parJeton(jeton));
    }

    @Transactional(readOnly = true)
    public Document avoirParJeton(String jeton, String numeroAvoir) {
        return avoir(parJeton(jeton), numeroAvoir);
    }

    // ------------------------------------------------------------------ par compte client

    @Transactional(readOnly = true)
    public Document factureDuCompte(String numeroCommande, long utilisateurId) {
        return facture(duCompte(numeroCommande, utilisateurId));
    }

    @Transactional(readOnly = true)
    public Document avoirDuCompte(String numeroCommande, long utilisateurId, String numeroAvoir) {
        return avoir(duCompte(numeroCommande, utilisateurId), numeroAvoir);
    }

    // ------------------------------------------------------------------ personnel

    @Transactional(readOnly = true)
    public Document facturePourPersonnel(String numeroFacture) {
        byte[] pdf = factureDepot.pdf(numeroFacture)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture", numeroFacture));
        return new Document(numeroFacture + ".pdf", pdf);
    }

    @Transactional(readOnly = true)
    public Document avoirPourPersonnel(String numeroAvoir) {
        byte[] pdf = avoirDepot.pdf(numeroAvoir)
                .orElseThrow(() -> new RessourceIntrouvableException("Avoir", numeroAvoir));
        return new Document(numeroAvoir + ".pdf", pdf);
    }

    /** Numéros de la facture et des avoirs d'une commande (pour la page de suivi et l'espace client). */
    @Transactional(readOnly = true)
    public Pieces piecesDe(Commande commande) {
        Optional<FactureDepot.FactureArchivee> facture = factureDepot.trouverParCommande(commande.getId());
        if (facture.isEmpty()) {
            return Pieces.AUCUNE;
        }
        return new Pieces(facture.get().numero(),
                avoirDepot.parFacture(facture.get().id()).stream().map(AvoirDepot.AvoirArchive::numero).toList());
    }

    // ------------------------------------------------------------------ interne

    private Commande parJeton(String jeton) {
        if (jeton == null || !FORMAT_JETON.matcher(jeton).matches()) {
            throw introuvable();
        }
        return commandeDepot.findByEmpreinteJetonSuivi(GenerateurJetons.empreinte(jeton))
                .orElseThrow(DocumentsCommandeService::introuvable);
    }

    private Commande duCompte(String numeroCommande, long utilisateurId) {
        Commande commande = commandeDepot.findByNumero(numeroCommande)
                .orElseThrow(DocumentsCommandeService::introuvable);
        if (commande.getUtilisateur() == null || commande.getUtilisateur().getId() != utilisateurId) {
            throw introuvable();
        }
        return commande;
    }

    private Document facture(Commande commande) {
        var facture = factureDepot.trouverParCommande(commande.getId()).orElseThrow(DocumentsCommandeService::introuvable);
        byte[] pdf = factureDepot.pdf(facture.numero()).orElseThrow(DocumentsCommandeService::introuvable);
        return new Document(facture.numero() + ".pdf", pdf);
    }

    private Document avoir(Commande commande, String numeroAvoir) {
        var facture = factureDepot.trouverParCommande(commande.getId()).orElseThrow(DocumentsCommandeService::introuvable);
        // L'avoir doit porter sur la facture de CETTE commande
        boolean appartient = avoirDepot.parFacture(facture.id()).stream().anyMatch(a -> a.numero().equals(numeroAvoir));
        if (!appartient) {
            throw introuvable();
        }
        byte[] pdf = avoirDepot.pdf(numeroAvoir).orElseThrow(DocumentsCommandeService::introuvable);
        return new Document(numeroAvoir + ".pdf", pdf);
    }

    private static RessourceIntrouvableException introuvable() {
        return new RessourceIntrouvableException("Document", "demandé");
    }
}
