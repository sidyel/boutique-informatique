package sn.boutique.authentification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import sn.boutique.authentification.config.ProprietesAuthentification;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Émet les jetons d'accès (JWT HS256, 15 minutes par défaut).
 * <p>
 * Revendications : {@code iss}, {@code sub} (identifiant de l'utilisateur), {@code iat}, {@code exp},
 * {@code email}, {@code roles} (liste) et {@code emailVerifie}. Aucune autre donnée personnelle :
 * le JWT est lisible par quiconque le détient (il est signé, pas chiffré).
 */
@Service
@RequiredArgsConstructor
public class JetonAccesService {

    public static final String REVENDICATION_ROLES = "roles";
    public static final String REVENDICATION_EMAIL = "email";
    public static final String REVENDICATION_EMAIL_VERIFIE = "emailVerifie";

    private final JwtEncoder encodeurJwt;
    private final ProprietesAuthentification proprietes;
    private final Clock horloge;

    public JetonAcces emettre(Utilisateur utilisateur) {
        Instant maintenant = horloge.instant();
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .issuer(proprietes.emetteur())
                .subject(String.valueOf(utilisateur.getId()))
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(proprietes.dureeJetonAcces()))
                .claim(REVENDICATION_EMAIL, utilisateur.getEmail())
                .claim(REVENDICATION_ROLES, List.of(utilisateur.getRole().name()))
                .claim(REVENDICATION_EMAIL_VERIFIE, utilisateur.isEmailVerifie())
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        String valeur = encodeurJwt.encode(JwtEncoderParameters.from(entete, revendications)).getTokenValue();
        return new JetonAcces(valeur, proprietes.dureeJetonAcces().toSeconds());
    }

    /** @param expireDans durée de validité en secondes */
    public record JetonAcces(String valeur, long expireDans) {
    }
}
