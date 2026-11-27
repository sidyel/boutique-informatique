package sn.boutique.compte.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.authentification.dto.MessageReponse;
import sn.boutique.authentification.service.JetonAccesService;
import sn.boutique.compte.dto.AdresseReponse;
import sn.boutique.compte.dto.AdresseRequete;
import sn.boutique.compte.dto.ChangementEmailRequete;
import sn.boutique.compte.dto.ChangementMotDePasseRequete;
import sn.boutique.compte.dto.ConsentementRequete;
import sn.boutique.compte.dto.MesDonneesReponse;
import sn.boutique.compte.dto.ModificationProfilRequete;
import sn.boutique.compte.dto.MotDePasseRequete;
import sn.boutique.compte.dto.SuppressionCompteRequete;
import sn.boutique.compte.dto.ProfilReponse;
import sn.boutique.compte.service.AdresseService;
import sn.boutique.compte.service.AvisCompteService;
import sn.boutique.compte.service.AvisCompteService.MonAvis;
import sn.boutique.compte.service.ExportDonneesService;
import sn.boutique.compte.service.SuppressionCompteService;
import sn.boutique.compte.service.ChangementEmailService;
import sn.boutique.compte.service.CompteService;
import sn.boutique.noyau.config.OpenApiConfig;

/**
 * Espace client : réservé aux utilisateurs connectés (voir SecuriteConfig). Les réponses sont personnelles : jamais
 * mises en cache (en-tête {@code no-store} posé par la configuration de sécurité).
 */
@RestController
@RequestMapping("/api/compte")
@RequiredArgsConstructor
public class CompteControleur {

    static final String MESSAGE_CHANGEMENT_EMAIL =
            "Si cette adresse peut être utilisée, un e-mail de confirmation vient de lui être envoyé. "
                    + "Votre adresse actuelle ne change qu'après avoir cliqué sur le lien qu'il contient.";

    private final CompteService compteService;
    private final ChangementEmailService changementEmailService;
    private final AdresseService adresseService;
    private final SuppressionCompteService suppressionService;
    private final ExportDonneesService exportService;
    private final AvisCompteService avisService;

    @Operation(summary = "Profil de l'utilisateur connecté", security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @GetMapping("/profil")
    public ProfilReponse profil(@AuthenticationPrincipal Jwt jwt) {
        return compteService.profil(id(jwt));
    }

    @Operation(summary = "Modifier le nom et le téléphone (normalisé en E.164)",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PutMapping("/profil")
    public ProfilReponse modifierProfil(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ModificationProfilRequete requete) {
        return compteService.modifierProfil(id(jwt), requete);
    }

    @Operation(summary = "Accepter ou retirer le consentement marketing",
            description = "Date et source (COMPTE) enregistrées ; le retrait est aussi simple que l'acceptation.",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PutMapping("/consentement-marketing")
    public ProfilReponse consentementMarketing(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ConsentementRequete requete) {
        return compteService.definirConsentementMarketing(id(jwt), requete.consentement());
    }

    @Operation(summary = "Changer de mot de passe (l'ancien est exigé)",
            description = "Révoque les autres sessions ; la session courante reste ouverte ; e-mail d'alerte envoyé.",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PostMapping("/mot-de-passe")
    public MessageReponse changerMotDePasse(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ChangementMotDePasseRequete requete) {
        String session = jwt.getClaimAsString(JetonAccesService.REVENDICATION_SESSION);
        compteService.changerMotDePasse(id(jwt), session == null ? null : UUID.fromString(session), requete);
        return new MessageReponse("Votre mot de passe est modifié. Vos autres appareils ont été déconnectés.");
    }

    @Operation(summary = "Demander un changement d'adresse e-mail (mot de passe exigé)",
            description = "202 identique que la nouvelle adresse soit libre ou non. Le lien part vers la nouvelle adresse, "
                    + "une alerte vers l'ancienne.",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PostMapping("/email")
    public ResponseEntity<MessageReponse> changerEmail(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody ChangementEmailRequete requete) {
        changementEmailService.demander(id(jwt), requete.motDePasse(), requete.nouvelEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageReponse(MESSAGE_CHANGEMENT_EMAIL));
    }

    // ------------------------------------------------------------------ carnet d'adresses

    @Operation(summary = "Carnet d'adresses (l'adresse par défaut en premier)",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @GetMapping("/adresses")
    public List<AdresseReponse> adresses(@AuthenticationPrincipal Jwt jwt) {
        return adresseService.lister(id(jwt));
    }

    @Operation(summary = "Ajouter une adresse", security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PostMapping("/adresses")
    public ResponseEntity<AdresseReponse> ajouterAdresse(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody AdresseRequete requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adresseService.creer(id(jwt), requete));
    }

    @Operation(summary = "Modifier une adresse", security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PutMapping("/adresses/{id}")
    public AdresseReponse modifierAdresse(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                          @Valid @RequestBody AdresseRequete requete) {
        return adresseService.modifier(id(jwt), id, requete);
    }

    @Operation(summary = "Définir l'adresse par défaut", security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PutMapping("/adresses/{id}/par-defaut")
    public AdresseReponse adresseParDefaut(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return adresseService.definirParDefaut(id(jwt), id);
    }

    @Operation(summary = "Supprimer une adresse", security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @DeleteMapping("/adresses/{id}")
    public ResponseEntity<Void> supprimerAdresse(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        adresseService.supprimer(id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ avis, données personnelles, suppression

    @Operation(summary = "Mes avis (vide tant qu'aucun avis n'a été publié)",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @GetMapping("/avis")
    public List<MonAvis> avis(@AuthenticationPrincipal Jwt jwt) {
        return avisService.lister(id(jwt));
    }

    @Operation(summary = "Télécharger mes données personnelles (fichier JSON, mot de passe exigé)",
            description = "Droit d'accès : profil, adresses, commandes, consentements, changements d'e-mail et avis. "
                    + "En POST pour ne pas mettre le mot de passe dans l'URL ; jamais mis en cache.",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PostMapping("/mes-donnees")
    public ResponseEntity<MesDonneesReponse> mesDonnees(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody MotDePasseRequete requete) {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mes-donnees.json\"")
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(exportService.exporter(id(jwt), requete.motDePasse()));
    }

    @Operation(summary = "Supprimer mon compte (mot de passe et mot SUPPRIMER exigés)",
            description = "Anonymisation partielle (ADR 0004 §3). Refusée tant qu'une commande est en cours : 422 COMMANDE_EN_COURS.",
            security = @SecurityRequirement(name = OpenApiConfig.SCHEMA_JWT))
    @PostMapping("/suppression")
    public MessageReponse supprimer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SuppressionCompteRequete requete) {
        suppressionService.supprimer(id(jwt), requete.motDePasse(), requete.confirmation());
        return new MessageReponse("Votre compte est supprimé. Un e-mail de confirmation vous a été envoyé.");
    }

    private static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
