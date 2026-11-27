package sn.boutique.commande.web;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutPaiement;
import sn.boutique.commande.modele.TypeFournisseurPaiement;

/**
 * Paiements pour le service financier ({@code /api/admin/paiements}, rôles internes). Par défaut : les paiements
 * « à rembourser » (reçus en double ou sans vente), les plus anciens d'abord.
 */
@RestController
@RequestMapping("/api/admin/paiements")
@RequiredArgsConstructor
public class AdminPaiementControleur {

    private static final int TAILLE_MAX = 100;

    private final PaiementDepot paiementDepot;

    public record PaiementAdmin(long id, String numeroCommande, TypeFournisseurPaiement fournisseur,
                                String referenceExterne, long montant, StatutPaiement statut, Instant dateCreation) {

        static PaiementAdmin de(Paiement p) {
            return new PaiementAdmin(p.getId(), p.getCommande().getNumero(), p.getFournisseur(),
                    p.getReferenceExterne(), p.getMontant(), p.getStatut(), p.getDateCreation());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<PageReponse<PaiementAdmin>> lister(
            @RequestParam(defaultValue = "A_REMBOURSER") StatutPaiement statut,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int taille) {
        int tailleBornee = Math.clamp(taille, 1, TAILLE_MAX);
        Page<Paiement> resultat = paiementDepot.findByStatutOrderByDateCreationAsc(statut,
                PageRequest.of(Math.max(page, 1) - 1, tailleBornee));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageReponse.de(
                resultat.map(PaiementAdmin::de).getContent(), Math.max(page, 1), tailleBornee,
                resultat.getTotalElements()));
    }
}
