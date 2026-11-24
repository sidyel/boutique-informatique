package sn.boutique.compte.dto;

import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;

public record ProfilReponse(
        Long id,
        String email,
        String prenom,
        String nom,
        String telephone,
        Role role,
        boolean emailVerifie,
        boolean consentementMarketing) {

    public static ProfilReponse de(Utilisateur utilisateur) {
        return new ProfilReponse(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getPrenom(),
                utilisateur.getNom(), utilisateur.getTelephone(), utilisateur.getRole(),
                utilisateur.isEmailVerifie(), utilisateur.isConsentementMarketing());
    }
}
