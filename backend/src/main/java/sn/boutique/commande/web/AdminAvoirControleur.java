package sn.boutique.commande.web;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.commande.facture.AvoirDepot;
import sn.boutique.commande.facture.AvoirDepot.AvoirArchive;
import sn.boutique.commande.facture.AvoirService;
import sn.boutique.commande.facture.AvoirService.AvoirRequete;
import sn.boutique.commande.facture.FactureDepot;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Avoirs d'une facture ({@code /api/admin/factures/{numero}/avoirs}). L'émission est réservée à l'administration
 * (règle dans SecuriteConfig) ; la lecture, aux rôles internes.
 */
@RestController
@RequestMapping("/api/admin/factures/{numero}/avoirs")
@RequiredArgsConstructor
public class AdminAvoirControleur {

    private final AvoirService avoirService;
    private final AvoirDepot avoirDepot;
    private final FactureDepot factureDepot;

    @PostMapping
    public ResponseEntity<AvoirArchive> emettre(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero,
                                                @Valid @RequestBody AvoirRequete requete) {
        String auteur = jwt.getClaimAsString(JetonAccesService.REVENDICATION_EMAIL);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(avoirService.emettre(numero, auteur, requete));
    }

    @GetMapping
    public ResponseEntity<List<AvoirArchive>> lister(@PathVariable String numero) {
        long factureId = factureDepot.trouverParNumero(numero).orElseThrow(
                () -> new RessourceIntrouvableException("Facture", numero)).id();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(avoirDepot.parFacture(factureId));
    }
}
