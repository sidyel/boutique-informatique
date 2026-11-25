package sn.boutique.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.catalogue.validation.TexteAlternatif;

/**
 * Modification partielle d'un média (PATCH). Un champ absent n'est pas modifié ;
 * {@code "varianteId": null} détache la photo de sa variante (elle vaut alors pour tout le produit).
 */
@Getter
@NoArgsConstructor
public class ModificationMediaRequete {

    /** Absent : inchangé ; présent : validé (pas de null ni de nom de fichier). */
    @TexteAlternatif
    private String texteAlternatif;
    @Getter(AccessLevel.NONE)
    private boolean texteAlternatifFourni;

    private Long varianteId;
    @JsonIgnore
    private boolean varianteIdFourni;

    public void setTexteAlternatif(String texteAlternatif) {
        this.texteAlternatif = texteAlternatif;
        this.texteAlternatifFourni = true;
    }

    public void setVarianteId(Long varianteId) {
        this.varianteId = varianteId;
        this.varianteIdFourni = true;
    }

    public static ModificationMediaRequete de(String texteAlternatif, boolean varianteFournie, Long varianteId) {
        ModificationMediaRequete requete = new ModificationMediaRequete();
        if (texteAlternatif != null) {
            requete.setTexteAlternatif(texteAlternatif);
        }
        if (varianteFournie) {
            requete.setVarianteId(varianteId);
        }
        return requete;
    }

    /** Le texte alternatif n'est validé que s'il est fourni. */
    @JsonIgnore
    public boolean isTexteAlternatifFourni() {
        return texteAlternatifFourni;
    }

    @JsonIgnore
    @AssertTrue(message = "Le texte alternatif ne peut pas être vide.")
    public boolean isTexteAlternatifNonNul() {
        return !texteAlternatifFourni || texteAlternatif != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Indiquer au moins un champ à modifier (texteAlternatif, varianteId).")
    public boolean isNonVide() {
        return texteAlternatifFourni || varianteIdFourni;
    }
}
