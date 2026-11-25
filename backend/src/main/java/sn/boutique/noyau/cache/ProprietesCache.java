package sn.boutique.noyau.cache;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cache des lectures publiques (préfixe {@code boutique.cache}).
 * <p>
 * Le cache a <strong>sa propre instance Redis</strong>, distincte de l'instance principale (compteurs de
 * limitation de débit) : {@code maxmemory} et la politique d'éviction valent pour toute une instance,
 * toutes bases confondues. Le cache peut ainsi évincer librement ({@code allkeys-lru}) sans jamais
 * toucher aux compteurs, que l'instance principale garde ({@code noeviction}).
 *
 * @param duree durée de vie des entrées (l'invalidation après modification est immédiate)
 */
@Validated
@ConfigurationProperties(prefix = "boutique.cache")
public record ProprietesCache(@NotNull Duration duree, @Valid @NotNull Redis redis) {

    /**
     * @param delai délai maximal d'une commande et d'une connexion : court, car une panne du cache ne doit
     *              pas ralentir le site (la requête lit alors la base)
     */
    public record Redis(@NotBlank String hote, int port, String motDePasse, @NotNull Duration delai) {
    }
}
