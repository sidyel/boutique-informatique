package sn.boutique.authentification.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Jetons d'accès : JWT signés en HS256 (HMAC-SHA256) avec la clé JWT_SECRET.
 * Le décodage n'accepte que HS256 (jamais « alg: none »), vérifie la signature, l'expiration
 * (tolérance de 30 s) et l'émetteur.
 */
@Slf4j
@Configuration
public class JwtConfig {

    static final int OCTETS_MINIMUM = 32;
    private static final Duration TOLERANCE_HORLOGE = Duration.ofSeconds(30);

    @Bean
    SecretKey cleSignatureJwt(ProprietesAuthentification proprietes) {
        try {
            return lireCle(proprietes.jwtSecret());
        } catch (IllegalStateException e) {
            if (!proprietes.secretEphemereAutorise()) {
                throw e;
            }
            // Développement uniquement : on démarre quand même, avec une clé jetable
            log.warn("{} Clé de signature éphémère utilisée (développement uniquement : "
                    + "les sessions sont perdues à chaque redémarrage).", e.getMessage());
            byte[] aleatoire = new byte[48];
            new SecureRandom().nextBytes(aleatoire);
            return new SecretKeySpec(aleatoire, "HmacSHA256");
        }
    }

    /** Décode JWT_SECRET (Base64, 32 octets minimum) ; lève IllegalStateException avec un message clair sinon. */
    static SecretKey lireCle(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET est absent. Générez une clé : openssl rand -base64 48.");
        }
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT_SECRET n'est pas du Base64 valide. Générez une clé : openssl rand -base64 48.", e);
        }
        if (octets.length < OCTETS_MINIMUM) {
            throw new IllegalStateException("JWT_SECRET est trop court : %d octets, %d minimum (openssl rand -base64 48)."
                    .formatted(octets.length, OCTETS_MINIMUM));
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    JwtEncoder encodeurJwt(SecretKey cleSignatureJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cleSignatureJwt));
    }

    @Bean
    JwtDecoder decodeurJwt(SecretKey cleSignatureJwt, ProprietesAuthentification proprietes, Clock horloge) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cleSignatureJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator validateurDates = new JwtTimestampValidator(TOLERANCE_HORLOGE);
        validateurDates.setClock(horloge);
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                validateurDates, new JwtIssuerValidator(proprietes.emetteur())));
        return decodeur;
    }
}
