package sn.boutique.commande.web;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.dto.CommandeResumeReponse;
import sn.boutique.commande.dto.SuiviCommandeReponse;
import sn.boutique.commande.service.CommandeCompteService;
import sn.boutique.noyau.config.OpenApiConfig;

/** « Mes commandes » : liste, détail et reprise du paiement. Les factures et avoirs sont dans DocumentsControleurs. */
@RestController
@RequestMapping("/api/compte/commandes")
@RequiredArgsConstructor
public class CommandeCompteControleur {

    private final CommandeCompteService service;

    @Operation(summary = "Mes commandes, de la plus récente à la plus ancienne (y compris celles passées en invité puis rattachées)",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @GetMapping
    public ResponseEntity<Page<CommandeResumeReponse>> lister(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int taille) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.lister(Long.valueOf(jwt.getSubject()), page, taille));
    }

    @Operation(summary = "Détail d'une de mes commandes : statuts, lignes, totaux, facture et avoirs",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @GetMapping("/{numero}")
    public ResponseEntity<SuiviCommandeReponse> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String numero) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.lire(numero, Long.valueOf(jwt.getSubject())));
    }

    @Operation(summary = "Reprendre le paiement d'une de mes commandes en attente : renvoie la page de paiement",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PostMapping("/{numero}/reprise-paiement")
    public ResponseEntity<Map<String, String>> reprendrePaiement(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable String numero) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("urlPaiement", service.reprendrePaiement(numero, Long.valueOf(jwt.getSubject()))));
    }
}
