package sn.boutique.commande.web;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.dto.CommandeCreeeReponse;
import sn.boutique.commande.dto.CreationCommandeRequete;
import sn.boutique.commande.dto.OptionsCommandeReponse;
import sn.boutique.commande.dto.RetourPaiementReponse;
import sn.boutique.commande.service.RetourPaiementService;
import sn.boutique.commande.dto.SuiviCommandeReponse;
import sn.boutique.commande.service.CommandeService.ContexteRequete;
import sn.boutique.commande.service.LimitesCommande;
import sn.boutique.commande.service.OptionsCommandeService;
import sn.boutique.commande.service.PriseDeCommandeService;
import sn.boutique.commande.service.SuiviCommandeService;
import sn.boutique.panier.service.PanierService.Demandeur;
import sn.boutique.panier.service.ProprietesPanier;

/**
 * Commande ({@code /api/commandes}). Toutes les réponses sont en {@code no-store} (données personnelles) ; les
 * routes qui modifient un état exigent un en-tête Origin du site (le panier invité repose sur un cookie).
 * <p>
 * {@code POST /api/commandes} exige l'en-tête {@code Idempotency-Key} : le frontend génère une clé par tentative
 * de commande et la garde jusqu'au succès, pour qu'un réessai (coupure réseau, double appui) ne crée jamais deux
 * commandes.
 */
@RestController
@RequestMapping("/api/commandes")
@RequiredArgsConstructor
public class CommandeControleur {

    static final String ENTETE_IDEMPOTENCE = "Idempotency-Key";

    private final PriseDeCommandeService priseDeCommande;
    private final SuiviCommandeService suiviService;
    private final OptionsCommandeService optionsService;
    private final RetourPaiementService retourService;
    private final LimitesCommande limites;
    private final ProprietesPanier proprietesPanier;

    @GetMapping("/options")
    public ResponseEntity<OptionsCommandeReponse> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(optionsService.options());
    }

    @PostMapping
    public ResponseEntity<CommandeCreeeReponse> creer(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                      @RequestHeader(value = ENTETE_IDEMPOTENCE, required = false)
                                                      String cle,
                                                      @Valid @RequestBody CreationCommandeRequete requete) {
        limites.creation(http.getRemoteAddr(), requete.telephone());
        Demandeur demandeur = new Demandeur(jwt == null ? null : Long.valueOf(jwt.getSubject()), jetonPanier(http));
        CommandeCreeeReponse reponse = priseDeCommande.commander(demandeur, cle, requete, new ContexteRequete(
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return ResponseEntity.status(reponse.rejouee() ? HttpStatus.OK : HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore()).body(reponse);
    }

    /**
     * Page de retour du prestataire de paiement. Le jeton de retour n'ouvre que ce statut (jamais le suivi complet).
     * Le statut annoncé par le navigateur n'est pas cru : la commande est vérifiée auprès du prestataire.
     */
    @GetMapping("/retour/{jeton}")
    public ResponseEntity<RetourPaiementReponse> retour(HttpServletRequest http, @PathVariable String jeton) {
        limites.suivi(http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(retourService.lire(jeton));
    }

    @GetMapping("/suivi/{jeton}")
    public ResponseEntity<SuiviCommandeReponse> suivi(HttpServletRequest http, @PathVariable String jeton) {
        limites.suivi(http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(suiviService.lire(jeton));
    }

    /** « Reprendre le paiement » : renvoie la nouvelle page de paiement du prestataire. */
    @PostMapping("/suivi/{jeton}/reprise-paiement")
    public ResponseEntity<Map<String, String>> reprendrePaiement(HttpServletRequest http,
                                                                @PathVariable String jeton) {
        limites.suivi(http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("urlPaiement", suiviService.reprendrePaiement(jeton)));
    }

    /** Revendique l'émission de l'événement « achat » : {@code emettre} est vrai pour un seul appelant. */
    @PostMapping("/suivi/{jeton}/achat-suivi")
    public ResponseEntity<Map<String, Boolean>> achatSuivi(HttpServletRequest http, @PathVariable String jeton) {
        limites.suivi(http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("emettre", suiviService.revendiquerAchat(jeton)));
    }

    private String jetonPanier(HttpServletRequest http) {
        if (http.getCookies() != null) {
            for (Cookie cookie : http.getCookies()) {
                if (proprietesPanier.cookie().nom().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
