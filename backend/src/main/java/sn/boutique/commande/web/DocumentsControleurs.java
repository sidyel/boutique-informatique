package sn.boutique.commande.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.facture.DocumentsCommandeService;

/** Téléchargement des factures et avoirs : espace client et personnel. (Le lien de suivi est dans CommandeControleur.) */
public final class DocumentsControleurs {

    private DocumentsControleurs() {
    }

    /** Client connecté : ses propres commandes seulement ({@code /api/compte/**} exige un compte). */
    @RestController
    @RequestMapping("/api/compte/commandes/{numero}")
    @RequiredArgsConstructor
    public static class Compte {

        private final DocumentsCommandeService documents;

        @GetMapping("/facture")
        public ResponseEntity<byte[]> facture(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero) {
            return ReponsePdf.de(documents.factureDuCompte(numero, Long.parseLong(jwt.getSubject())));
        }

        @GetMapping("/avoirs/{numeroAvoir}")
        public ResponseEntity<byte[]> avoir(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero,
                                            @PathVariable String numeroAvoir) {
            return ReponsePdf.de(documents.avoirDuCompte(numero, Long.parseLong(jwt.getSubject()), numeroAvoir));
        }
    }

    /** Personnel (rôles internes) : par numéro de pièce. */
    @RestController
    @RequestMapping("/api/admin")
    @RequiredArgsConstructor
    public static class Personnel {

        private final DocumentsCommandeService documents;

        @GetMapping("/factures/{numero}/pdf")
        public ResponseEntity<byte[]> facture(@PathVariable String numero) {
            return ReponsePdf.de(documents.facturePourPersonnel(numero));
        }

        @GetMapping("/avoirs/{numero}/pdf")
        public ResponseEntity<byte[]> avoir(@PathVariable String numero) {
            return ReponsePdf.de(documents.avoirPourPersonnel(numero));
        }
    }
}
