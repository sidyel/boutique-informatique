package sn.boutique.commande.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.administration.journal.JournalAdministration;
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
import sn.boutique.commande.facture.EncaissementService;
import sn.boutique.commande.facture.EncaissementService.LivraisonRequete;
import sn.boutique.commande.facture.EncaissementService.LivraisonResultat;
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
    private final EncaissementService encaissement;
    private final JournalAdministration journal;

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

    @Transactional
    @PostMapping("/{numero}/confirmer")
    public ResponseEntity<CommandeAdminResume> confirmer(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable String numero) {
        Commande commande = gestion.confirmer(numero, auteur(jwt));
        journal.enregistrer("COMMANDE_CONFIRMEE", "Commande", numero,
                Map.of("statut", StatutCommande.EN_ATTENTE_CONFIRMATION.name()), Map.of("statut", commande.getStatut().name()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CommandeAdminResume.de(commande));
    }

    @Transactional
    @PostMapping("/{numero}/annuler")
    public ResponseEntity<CommandeAdminResume> annuler(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero,
                                                       @Valid @RequestBody AnnulationRequete requete) {
        Commande commande = gestion.annuler(numero, auteur(jwt), requete.motif());
        journal.enregistrer("COMMANDE_ANNULEE", "Commande", numero, null, Map.of("statut", commande.getStatut().name()),
                requete.motif());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(CommandeAdminResume.de(commande));
    }

    /**
     * La commande est livrée. Paiement à la livraison : corps obligatoire (montant encaissé, moyen, motif de l'écart
     * si le montant est inférieur au total) ; la facture est émise à ce moment. Commande déjà payée en ligne : pas de
     * corps. Un montant supérieur au total est refusé (422).
     */
    @Transactional
    @PostMapping("/{numero}/livrer")
    public ResponseEntity<LivraisonResultat> livrer(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero,
                                                    @RequestBody(required = false) LivraisonRequete requete) {
        LivraisonResultat resultat = encaissement.livrer(numero, auteur(jwt), Long.valueOf(jwt.getSubject()), requete);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("statut", StatutCommande.LIVREE.name());
        apres.put("total", resultat.total());
        apres.put("montantRecu", resultat.montantRecu());
        apres.put("ecartEncaissement", resultat.ecartEncaissement());
        journal.enregistrer("COMMANDE_LIVREE", "Commande", numero, null, apres);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resultat);
    }

    /**
     * Le client a perdu son lien : un nouveau lien lui est envoyé (e-mail et message). L'ancien cesse de
     * fonctionner ; le lien n'est jamais renvoyé dans la réponse.
     */
    @Transactional
    @PostMapping("/{numero}/renvoyer-lien-suivi")
    public ResponseEntity<Void> renvoyerLienSuivi(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero) {
        gestion.renvoyerLienSuivi(numero, auteur(jwt));
        journal.enregistrer("LIEN_SUIVI_RENVOYE", "Commande", numero, null, null);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static String auteur(Jwt jwt) {
        return jwt.getClaimAsString(JetonAccesService.REVENDICATION_EMAIL);
    }
}
