package sn.boutique.administration.utilisateur;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.administration.securite.ReauthentificationRequise;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Comptes de l'équipe : {@code SUPER_ADMIN} seulement (règle dans SecuriteConfig). Toute action qui modifie un compte exige une
 * réauthentification par code TOTP (ADR 0009 §7 et §8).
 */
@Tag(name = "Administration — utilisateurs")
@RestController
@RequestMapping("/api/admin/utilisateurs")
@RequiredArgsConstructor
public class AdminUtilisateurControleur {

    public record UtilisateurInterneReponse(Long id, String email, String prenom, String nom, Role role, boolean actif,
                                            boolean secondFacteurActif, boolean invitationEnAttente,
                                            long codesSecoursRestants, Instant dateCreation) {
    }

    public record InvitationRequete(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 80) String prenom,
                                    @NotBlank @Size(max = 80) String nom, @NotNull Role role) {
    }

    public record RoleRequete(@NotNull Role role) {
    }

    private final UtilisateurInterneService service;

    @Operation(summary = "Lister les comptes de l'équipe")
    @GetMapping
    public ResponseEntity<List<UtilisateurInterneReponse>> lister() {
        List<UtilisateurInterneReponse> comptes = service.lister().stream().map(this::reponse).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comptes);
    }

    @Operation(summary = "Inviter un membre de l'équipe (lien valable 48 h envoyé par e-mail)")
    @ReauthentificationRequise
    @PostMapping
    public ResponseEntity<UtilisateurInterneReponse> inviter(@Valid @RequestBody InvitationRequete requete) {
        Utilisateur invite = service.inviter(requete.email(), requete.prenom(), requete.nom(), requete.role());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(reponse(invite));
    }

    @Operation(summary = "Renvoyer l'invitation d'un compte qui n'a pas encore choisi son mot de passe")
    @ReauthentificationRequise
    @PostMapping("/{id}/renvoyer-invitation")
    public ResponseEntity<Void> renvoyerInvitation(@PathVariable Long id) {
        service.renvoyerInvitation(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Changer le rôle d'un compte (révoque ses sessions)")
    @ReauthentificationRequise
    @PatchMapping("/{id}/role")
    public ResponseEntity<UtilisateurInterneReponse> changerRole(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                 @Valid @RequestBody RoleRequete requete) {
        return ok(service.changerRole(Long.valueOf(jwt.getSubject()), id, requete.role()));
    }

    @Operation(summary = "Désactiver un compte (révoque ses sessions)")
    @ReauthentificationRequise
    @PostMapping("/{id}/desactiver")
    public ResponseEntity<UtilisateurInterneReponse> desactiver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ok(service.desactiver(Long.valueOf(jwt.getSubject()), id));
    }

    @Operation(summary = "Réactiver un compte désactivé")
    @ReauthentificationRequise
    @PostMapping("/{id}/activer")
    public ResponseEntity<UtilisateurInterneReponse> activer(@PathVariable Long id) {
        return ok(service.activer(id));
    }

    @Operation(summary = "Réinitialiser le second facteur d'un compte (perte du téléphone)",
            description = "Efface le secret et les codes de secours, révoque les sessions, prévient le titulaire par e-mail.")
    @ReauthentificationRequise
    @PostMapping("/{id}/reinitialiser-second-facteur")
    public ResponseEntity<UtilisateurInterneReponse> reinitialiserSecondFacteur(@AuthenticationPrincipal Jwt jwt,
                                                                                @PathVariable Long id) {
        return ok(service.reinitialiserSecondFacteur(Long.valueOf(jwt.getSubject()), id));
    }

    private ResponseEntity<UtilisateurInterneReponse> ok(Utilisateur u) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reponse(u));
    }

    private UtilisateurInterneReponse reponse(Utilisateur u) {
        return new UtilisateurInterneReponse(u.getId(), u.getEmail(), u.getPrenom(), u.getNom(), u.getRole(), u.isActif(),
                u.secondFacteurActif(), u.invitationEnAttente(), service.codesSecoursRestants(u.getId()), u.getDateCreation());
    }
}
