package sn.boutique.authentification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.boutique.authentification.validation.MotDePasseRobuste;
import sn.boutique.noyau.telephone.TelephoneSenegalais;

public record InscriptionRequete(
        @NotBlank(message = "L'e-mail est obligatoire")
        @Email(message = "L'e-mail n'est pas valide")
        @Size(max = 254, message = "L'e-mail ne doit pas dépasser 254 caractères")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @MotDePasseRobuste
        String motDePasse,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 80, message = "Le prénom ne doit pas dépasser 80 caractères")
        String prenom,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 80, message = "Le nom ne doit pas dépasser 80 caractères")
        String nom,

        /** Facultatif : « 77 123 45 67 », « +221771234567 »… */
        @TelephoneSenegalais
        String telephone,

        /** Case à cocher facultative : absente = refus. */
        Boolean consentementMarketing) {

    public boolean consentementMarketingDonne() {
        return Boolean.TRUE.equals(consentementMarketing);
    }

    @Override
    public String toString() {
        // Jamais de mot de passe dans les journaux
        return "InscriptionRequete[email=%s]".formatted(email);
    }
}
