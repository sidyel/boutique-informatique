package sn.boutique.panier.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.noyau.limitation.LimiteurDebit;
import sn.boutique.panier.dto.AjoutLigneRequete;
import sn.boutique.panier.dto.CodePromoRequete;
import sn.boutique.panier.dto.PanierReponse;
import sn.boutique.panier.dto.QuantiteRequete;
import sn.boutique.panier.service.PanierService;
import sn.boutique.panier.service.PanierService.Demandeur;
import sn.boutique.panier.service.PanierService.Resultat;
import sn.boutique.panier.service.ProprietesPanier;

/**
 * Panier ({@code /api/panier}), invité ou connecté. Chaque réponse est le panier complet recalculé, en
 * {@code no-store}. Les modifications exigent un en-tête Origin du site (VerificationOrigineFiltre) : le panier
 * invité repose sur un cookie.
 * <p>
 * Paramètre facultatif {@code zone} sur toutes les routes : identifiant de la zone de livraison, pour estimer
 * les frais et le total.
 */
@RestController
@RequestMapping("/api/panier")
@RequiredArgsConstructor
public class PanierControleur {

    private final PanierService panierService;
    private final ProprietesPanier proprietes;
    private final LimiteurDebit limiteur;

    @GetMapping
    public ResponseEntity<PanierReponse> lire(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                              @RequestParam(required = false) Long zone) {
        return repondre(panierService.lire(demandeur(jwt, http), zone));
    }

    @PostMapping("/lignes")
    public ResponseEntity<PanierReponse> ajouter(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                 @RequestParam(required = false) Long zone,
                                                 @Valid @RequestBody AjoutLigneRequete requete) {
        return repondre(panierService.ajouter(demandeur(jwt, http), requete.varianteId(), requete.quantite(), zone));
    }

    @PatchMapping("/lignes/{ligneId}")
    public ResponseEntity<PanierReponse> modifierQuantite(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                          @PathVariable long ligneId,
                                                          @RequestParam(required = false) Long zone,
                                                          @Valid @RequestBody QuantiteRequete requete) {
        return repondre(panierService.modifierQuantite(demandeur(jwt, http), ligneId, requete.quantite(), zone));
    }

    @DeleteMapping("/lignes/{ligneId}")
    public ResponseEntity<PanierReponse> supprimerLigne(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                        @PathVariable long ligneId,
                                                        @RequestParam(required = false) Long zone) {
        return repondre(panierService.supprimerLigne(demandeur(jwt, http), ligneId, zone));
    }

    /** Essais limités par IP (anti-énumération des codes). */
    @PutMapping("/code-promo")
    public ResponseEntity<PanierReponse> appliquerCodePromo(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                            @RequestParam(required = false) Long zone,
                                                            @Valid @RequestBody CodePromoRequete requete) {
        limiteur.verifier("code-promo-ip", proprietes.codePromoIp(), http.getRemoteAddr());
        return repondre(panierService.appliquerCodePromo(demandeur(jwt, http), requete.code(), zone));
    }

    @DeleteMapping("/code-promo")
    public ResponseEntity<PanierReponse> retirerCodePromo(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                          @RequestParam(required = false) Long zone) {
        return repondre(panierService.retirerCodePromo(demandeur(jwt, http), zone));
    }

    /** Le client accepte les nouveaux prix signalés (« Le prix de cet article est passé de X à Y »). */
    @PostMapping("/accepter-prix")
    public ResponseEntity<PanierReponse> accepterPrix(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
                                                      @RequestParam(required = false) Long zone) {
        return repondre(panierService.accepterPrix(demandeur(jwt, http), zone));
    }

    private Demandeur demandeur(Jwt jwt, HttpServletRequest http) {
        return new Demandeur(jwt == null ? null : Long.valueOf(jwt.getSubject()), jetonInvite(http));
    }

    private String jetonInvite(HttpServletRequest http) {
        if (http.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : http.getCookies()) {
            if (proprietes.cookie().nom().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseEntity<PanierReponse> repondre(Resultat resultat) {
        ResponseEntity.BodyBuilder reponse = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (resultat.nouveauJetonInvite() != null) {
            reponse.header(HttpHeaders.SET_COOKIE,
                    cookie(resultat.nouveauJetonInvite()).maxAge(proprietes.cookie().duree()).build().toString());
        } else if (resultat.effacerCookie()) {
            reponse.header(HttpHeaders.SET_COOKIE, cookie("").maxAge(0).build().toString());
        }
        return reponse.body(resultat.panier());
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String valeur) {
        ProprietesPanier.Cookie cookie = proprietes.cookie();
        return ResponseCookie.from(cookie.nom(), valeur)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Lax")
                .path(cookie.chemin());
    }
}
