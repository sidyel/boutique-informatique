package sn.boutique.administration.utilisateur;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.authentification.depot.CodeSecoursDepot;
import sn.boutique.authentification.service.CodeSecondFacteurInvalideException;
import sn.boutique.authentification.service.SecondFacteurService;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.compte.service.VerificationMotDePasse;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * « Mon compte » côté équipe : identité, avertissements permanents (un seul {@code SUPER_ADMIN}, codes de secours presque
 * épuisés) et régénération des codes de secours (mot de passe et code TOTP exigés).
 */
@Tag(name = "Administration — mon compte")
@RestController
@RequestMapping("/api/admin/moi")
@RequiredArgsConstructor
public class AdminMoiControleur {

    /** En dessous de ce nombre de codes de secours restants, un avertissement invite à en générer de nouveaux. */
    static final int SEUIL_CODES_SECOURS = 3;

    public record Avertissement(String code, String message) {
    }

    public record MoiReponse(Long id, String email, String prenom, String nom, Role role, boolean secondFacteurActif,
                             long codesSecoursRestants, List<Avertissement> avertissements) {
    }

    public record RegenerationRequete(@NotBlank @Size(max = 200) String motDePasse, @NotBlank @Size(max = 20) String code) {
    }

    public record CodesReponse(List<String> codesSecours) {
    }

    private final UtilisateurDepot utilisateurs;
    private final CodeSecoursDepot codesSecours;
    private final SecondFacteurService secondFacteur;
    private final VerificationMotDePasse verificationMotDePasse;
    private final JournalAdministration journal;
    private final Clock horloge;

    @Operation(summary = "Mon identité et les avertissements permanents de l'administration")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<MoiReponse> moi(@AuthenticationPrincipal Jwt jwt) {
        Utilisateur u = charger(jwt);
        long restants = codesSecours.compterRestants(u.getId());
        List<Avertissement> avertissements = new ArrayList<>();
        if (u.getRole() == Role.SUPER_ADMIN && utilisateurs.compterActifsParRole(Role.SUPER_ADMIN) <= 1) {
            avertissements.add(new Avertissement("UN_SEUL_SUPER_ADMIN",
                    "Vous êtes le seul super-administrateur. Si vous perdez votre téléphone et vos codes de secours, "
                            + "l'accès ne se récupère que par la base de données. Invitez un second super-administrateur."));
        }
        if (u.secondFacteurActif() && restants <= SEUIL_CODES_SECOURS) {
            avertissements.add(new Avertissement("CODES_SECOURS_BAS", restants == 0
                    ? "Vous n'avez plus de code de secours. Générez-en de nouveaux dans « Mon compte »."
                    : "Il ne vous reste que " + restants + " code(s) de secours. Générez-en de nouveaux dans « Mon compte »."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MoiReponse(u.getId(), u.getEmail(), u.getPrenom(), u.getNom(), u.getRole(), u.secondFacteurActif(),
                        restants, avertissements));
    }

    @Operation(summary = "Générer de nouveaux codes de secours (les anciens sont invalidés)",
            description = "Mot de passe et code TOTP exigés. Les codes ne sont affichés qu'une fois.")
    @PostMapping("/codes-secours")
    @Transactional(noRollbackFor = CodeSecondFacteurInvalideException.class)
    public ResponseEntity<CodesReponse> regenerer(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody RegenerationRequete requete,
                                                  HttpServletRequest http) {
        Utilisateur u = charger(jwt);
        verificationMotDePasse.exiger(u, requete.motDePasse());
        if (!secondFacteur.verifierCodeTotp(u, requete.code(),
                new Contexte(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT)))) {
            throw new CodeSecondFacteurInvalideException("code TOTP faux pour la régénération des codes de secours");
        }
        List<String> nouveaux = secondFacteur.regenererCodes(u);
        journal.enregistrer("CODES_SECOURS_REGENERES", "Utilisateur", u.getId(), null, Map.of("nombre", nouveaux.size()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CodesReponse(nouveaux));
    }

    @Operation(summary = "Confirmer que les nouveaux codes de secours sont enregistrés")
    @PostMapping("/codes-secours/confirmer")
    @Transactional
    public ResponseEntity<Void> confirmer(@AuthenticationPrincipal Jwt jwt) {
        Utilisateur u = charger(jwt);
        u.confirmerCodesSecours(horloge.instant());
        journal.enregistrer("CODES_SECOURS_CONFIRMES", "Utilisateur", u.getId(), null, null);
        return ResponseEntity.noContent().build();
    }

    private Utilisateur charger(Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", id));
    }
}
