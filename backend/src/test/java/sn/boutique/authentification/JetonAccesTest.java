package sn.boutique.authentification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import sn.boutique.compte.modele.Role;

/** Jetons d'accès refusés (expirés, falsifiés, mal signés) et contrôle des rôles. */
class JetonAccesTest extends AuthentificationTestBase {

    @Autowired
    private JwtEncoder encodeurJwt;

    @Test
    void unJetonExpireEstRefuse() throws Exception {
        String jeton = jetonAcces(creerEtConnecter(Role.CLIENT));
        profil(jeton).andExpect(status().isOk());

        // 15 minutes de validité + 30 secondes de tolérance d'horloge
        horloge.avancer(Duration.ofMinutes(15).plusSeconds(31));
        profil(jeton).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
    }

    @Test
    void unJetonModifieEstRefuse() throws Exception {
        String jeton = jetonAcces(creerEtConnecter(Role.CLIENT));
        String[] parties = jeton.split("\\.");

        // Charge utile réécrite (rôle SUPER_ADMIN) avec la signature d'origine
        String chargeFalsifiee = base64Url(new String(Base64.getUrlDecoder().decode(parties[1]), StandardCharsets.UTF_8)
                .replace("\"CLIENT\"", "\"SUPER_ADMIN\""));
        profil(parties[0] + "." + chargeFalsifiee + "." + parties[2]).andExpect(status().isUnauthorized());

        // Signature retirée et algorithme « none »
        String enteteNone = base64Url("{\"alg\":\"none\"}");
        profil(enteteNone + "." + parties[1] + ".").andExpect(status().isUnauthorized());
        profil(parties[0] + "." + parties[1] + ".").andExpect(status().isUnauthorized());
    }

    @Test
    void unJetonSigneAvecUneAutreCleOuUnAutreEmetteurEstRefuse() throws Exception {
        long id = idUtilisateur(creerEtConnecter(Role.CLIENT));

        JwtEncoder autreCle = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("une-autre-cle-de-trente-deux-octets-au-moins".getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256")));
        profil(encoder(autreCle, id, "boutique-informatique")).andExpect(status().isUnauthorized());

        profil(encoder(encodeurJwt, id, "https://autre-emetteur.example")).andExpect(status().isUnauthorized());
        // Contrôle : le même jeton avec le bon émetteur est accepté
        profil(encoder(encodeurJwt, id, "boutique-informatique")).andExpect(status().isOk());
    }

    @Test
    void lAdministrationEstReserveeAuxRolesInternes() throws Exception {
        mockMvc.perform(get("/api/admin/produits"))
                .andExpect(status().isUnauthorized());

        MvcResult client = creerEtConnecter(Role.CLIENT);
        mockMvc.perform(get("/api/admin/produits").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces(client)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        // Rôle interne : l'accès est autorisé (404 car la route n'existe pas encore)
        for (Role role : List.of(Role.SERVICE_CLIENT, Role.ADMIN)) {
            MvcResult interne = creerEtConnecter(role);
            mockMvc.perform(get("/api/admin/produits").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces(interne)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void lesRoutesPubliquesRestentAccessiblesSansJeton() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String encoder(JwtEncoder encodeur, long id, String emetteur) {
        Instant maintenant = horloge.instant();
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .issuer(emetteur)
                .subject(String.valueOf(id))
                .issuedAt(maintenant)
                .expiresAt(maintenant.plusSeconds(600))
                .claim("roles", List.of("CLIENT"))
                .build();
        return encodeur.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), revendications))
                .getTokenValue();
    }

    private static String base64Url(String texte) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texte.getBytes(StandardCharsets.UTF_8));
    }
}
