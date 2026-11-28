package sn.boutique.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Jeton simulé d'un compte de l'équipe pour les tests de routes d'administration : le compte existe réellement en base,
 * actif et du bon rôle (le serveur le revérifie à chaque appel, ADR 0009).
 */
public final class JwtEquipe {

    private JwtEquipe() {
    }

    public static JwtRequestPostProcessor pour(JdbcClient jdbc, String role) {
        Long id = jdbc.sql("""
                        insert into utilisateur (email, mot_de_passe_hash, prenom, nom, role)
                        values (:email, :hash, 'Équipe', 'Test', :role) returning id""")
                .param("email", "equipe-" + UUID.randomUUID() + "@exemple.sn")
                .param("hash", FabriqueComptes.HASH_FACTICE)
                .param("role", role)
                .query(Long.class).single();
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
