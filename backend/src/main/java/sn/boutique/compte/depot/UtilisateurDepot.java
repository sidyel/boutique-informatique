package sn.boutique.compte.depot;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;

public interface UtilisateurDepot extends JpaRepository<Utilisateur, Long> {

    /** Attend un e-mail déjà normalisé : utiliser {@link #trouverParEmail(String)}. */
    Optional<Utilisateur> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Comptes de l'équipe : tous les rôles sauf CLIENT (mot-clé Spring Data en anglais, imposé par le framework). */
    List<Utilisateur> findByRoleNotOrderByDateCreationAsc(Role role);

    @Query("select count(u) from Utilisateur u where u.role = :role and u.actif = true and u.id <> :exclu")
    long compterActifsParRoleSauf(Role role, Long exclu);

    @Query("select count(u) from Utilisateur u where u.role = :role and u.actif = true")
    long compterActifsParRole(Role role);

    /** Recherche insensible à la casse et aux espaces autour de la saisie. */
    default Optional<Utilisateur> trouverParEmail(String saisie) {
        return findByEmail(Utilisateur.normaliserEmail(saisie));
    }

    default boolean emailExiste(String saisie) {
        return existsByEmail(Utilisateur.normaliserEmail(saisie));
    }

    /**
     * Anti-rejeu du second facteur : mémorise le pas TOTP accepté seulement s'il est strictement plus récent que le dernier.
     * Une seule des requêtes simultanées portant le même code obtient 1.
     */
    @Transactional
    @Modifying
    @Query(value = "update utilisateur set totp_dernier_pas = :pas "
            + "where id = :id and (totp_dernier_pas is null or totp_dernier_pas < :pas)", nativeQuery = true)
    int avancerPasTotp(Long id, long pas);
}
