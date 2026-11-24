package sn.boutique.compte.depot;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.compte.modele.Utilisateur;

public interface UtilisateurDepot extends JpaRepository<Utilisateur, Long> {

    /** Attend un e-mail déjà normalisé : utiliser {@link #trouverParEmail(String)}. */
    Optional<Utilisateur> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Recherche insensible à la casse et aux espaces autour de la saisie. */
    default Optional<Utilisateur> trouverParEmail(String saisie) {
        return findByEmail(Utilisateur.normaliserEmail(saisie));
    }

    default boolean emailExiste(String saisie) {
        return existsByEmail(Utilisateur.normaliserEmail(saisie));
    }
}
