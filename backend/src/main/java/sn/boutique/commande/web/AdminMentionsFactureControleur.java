package sn.boutique.commande.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.facture.MentionsFactureService;
import sn.boutique.commande.facture.MentionsFactureService.MentionsFactureReponse;
import sn.boutique.commande.facture.MentionsFactureService.MentionsFactureRequete;

/**
 * Mentions légales et régime de TVA des factures. Réservé à ADMIN et SUPER_ADMIN (règle
 * {@code /api/admin/parametres/**} de SecuriteConfig). L'écran viendra en phase 6.
 */
@RestController
@RequestMapping("/api/admin/parametres/facturation")
@RequiredArgsConstructor
public class AdminMentionsFactureControleur {

    private final MentionsFactureService service;

    @GetMapping
    public ResponseEntity<MentionsFactureReponse> lire() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.lire());
    }

    @PutMapping
    public ResponseEntity<MentionsFactureReponse> modifier(@Valid @RequestBody MentionsFactureRequete requete) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.modifier(requete));
    }
}
