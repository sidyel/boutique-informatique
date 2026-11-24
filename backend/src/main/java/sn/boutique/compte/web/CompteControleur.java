package sn.boutique.compte.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.dto.ProfilReponse;
import sn.boutique.noyau.config.OpenApiConfig;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Espace client : réservé aux utilisateurs connectés (voir SecuriteConfig). */
@RestController
@RequestMapping("/api/compte")
@RequiredArgsConstructor
public class CompteControleur {

    private final UtilisateurDepot utilisateurDepot;

    @Operation(summary = "Profil de l'utilisateur connecté", security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @GetMapping("/profil")
    @Transactional(readOnly = true)
    public ProfilReponse profil(@AuthenticationPrincipal Jwt jwt) {
        return utilisateurDepot.findById(Long.valueOf(jwt.getSubject()))
                .map(ProfilReponse::de)
                .orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
    }
}
