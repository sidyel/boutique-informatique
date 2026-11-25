package sn.boutique.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Modification partielle d'une variante : seuls les champs présents dans le JSON changent.
 * <p>
 * Une classe et non un record : pour {@code prixBarre}, il faut distinguer le champ absent (inchangé) du
 * {@code null} explicite (retirer le prix barré). Jackson n'appelle le setter que si le champ est présent.
 */
@Getter
@NoArgsConstructor
public class ModificationVarianteRequete {

    /** Nouveau prix en FCFA (entier) ; absent = inchangé. */
    @Positive(message = "Le prix doit être positif.")
    @Max(value = 1_000_000_000L, message = "Le prix dépasse 1 milliard de FCFA.")
    private Long prix;

    /** Nouveau prix barré, supérieur au prix ; {@code null} explicite = retirer le prix barré. */
    @Positive(message = "Le prix barré doit être positif.")
    @Max(value = 1_000_000_000L, message = "Le prix barré dépasse 1 milliard de FCFA.")
    private Long prixBarre;

    /** Positionné par le setter uniquement : un client ne peut pas l'envoyer. */
    @JsonIgnore
    private boolean prixBarreFourni;

    /** Absent = inchangé. */
    private Boolean actif;

    /**
     * Pour les appels Java (tests, autres services) : {@code prixBarreFourni} précise si prixBarre s'applique.
     * Une fabrique et non un constructeur : Jackson prendrait un constructeur public pour désérialiser.
     */
    public static ModificationVarianteRequete de(Long prix, Long prixBarre, boolean prixBarreFourni, Boolean actif) {
        ModificationVarianteRequete requete = new ModificationVarianteRequete();
        requete.prix = prix;
        requete.prixBarre = prixBarre;
        requete.prixBarreFourni = prixBarreFourni;
        requete.actif = actif;
        return requete;
    }

    public void setPrix(Long prix) {
        this.prix = prix;
    }

    public void setPrixBarre(Long prixBarre) {
        this.prixBarre = prixBarre;
        this.prixBarreFourni = true;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    @AssertTrue(message = "Indiquer au moins un champ à modifier : prix, prixBarre ou actif.")
    public boolean isNonVide() {
        return prix != null || prixBarreFourni || actif != null;
    }
}
