package sn.boutique.compte.dto;

import java.time.Instant;

import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.SourceConsentement;
import sn.boutique.compte.modele.Utilisateur;

public record ProfilReponse(
        Long id,
        String email,
        String prenom,
        String nom,
        String telephone,
        Role role,
        boolean emailVerifie,
        boolean consentementMarketing,
        /** Date de la dernière décision de consentement (acceptation ou retrait), null si aucune. */
        Instant dateConsentement,
        SourceConsentement sourceConsentement) {

    public static ProfilReponse de(Utilisateur utilisateur) {
        return new ProfilReponse(utilisateur.getId(), utilisateur.getEmail(), utilisateur.getPrenom(),
                utilisateur.getNom(), utilisateur.getTelephone(), utilisateur.getRole(),
                utilisateur.isEmailVerifie(), utilisateur.isConsentementMarketing(), utilisateur.getDateConsentement(),
                utilisateur.getSourceConsentement());
    }
}
