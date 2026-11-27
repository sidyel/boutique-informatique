package sn.boutique.commande.web;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.service.GestionCommandeService;

/**
 * Commandes pour le service client ({@code /api/admin/commandes}, rôles internes).
 * <p>
 * Le service client appelle chaque commande à la livraison, puis la <strong>confirme</strong> ou l'<strong>annule</strong>.
 * Une commande non confirmée dans le délai réglé est annulée automatiquement (stock libéré).
 */
@RestController
@RequestMapping("/api/admin/commandes")
@RequiredArgsConstructor
public class AdminCommandeControleur {

    private static final int TAILLE_MAX = 100;

    private final CommandeDepot commandeDepot;
    private final GestionCommandeService gestion;

    /**
     * @param telephone numéro complet : le service client doit pouvoir rappeler le client
     */
    public record CommandeAdminResume(String numero, Instant dateCreation, Instant expireLe, StatutCommande statut,
                                      String nomClient, String telephone, String email, ModeLivraison modeLivraison,
                                      ModePaiement modePaiement, long total) {

        static CommandeAdminResume de(Commande c) {
            return new CommandeAdminResume(c.getNumero(), c.getDateCreation(), c.getExpireLe(), c.getStatut(),
                    c.getNomClient(), c.getTelephoneClient(), c.getEmailClient(), c.getModeLivraison(),
                    c.getModePaiement(), c.getTotal());
        }
    }

    public record AnnulationRequete(
            @NotBlank(message = "Le motif est obligatoire")
            @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
            String motif) {
    }

    /** Par défaut : les commandes à la livraison en attente de confirmation, les plus anciennes d'abord. */
    @GetMapping
    public ResponseEntity<PageReponse<CommandeAdminResume>> lister(
            @RequestParam(defaultValue = "EN_ATTENTE_CONFIRMATION") StatutCommande statut,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int taille) {
        int tailleBornee = Math.clamp(taille, 1, TAILLE_MAX);
        Page<Commande> resultat = commandeDepot.findByStatutOrderByDateCreationAsc(statut,
                PageRequest.of(Math.max(page, 1) - 1, tailleBornee));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageReponse.de(
                resultat.map(CommandeAdminResume::de).getContent(), Math.max(page, 1), tailleBornee,
                resultat.getTotalElements()));
    }

    @PostMapping("/{numero}/confirmer")
    public ResponseEntity<CommandeAdminResume> confirmer(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable String numero) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CommandeAdminResume.de(gestion.confirmer(numero, auteur(jwt))));
    }

    @PostMapping("/{numero}/annuler")
    public ResponseEntity<CommandeAdminResume> annuler(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero,
                                                       @Valid @RequestBody AnnulationRequete requete) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CommandeAdminResume.de(gestion.annuler(numero, auteur(jwt), requete.motif())));
    }

    /**
     * Le client a perdu son lien : un nouveau lien lui est envoyé (e-mail et message). L'ancien cesse de
     * fonctionner ; le lien n'est jamais renvoyé dans la réponse.
     */
    @PostMapping("/{numero}/renvoyer-lien-suivi")
    public ResponseEntity<Void> renvoyerLienSuivi(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero) {
        gestion.renvoyerLienSuivi(numero, auteur(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static String auteur(Jwt jwt) {
        return jwt.getClaimAsString(JetonAccesService.REVENDICATION_EMAIL);
    }
}
