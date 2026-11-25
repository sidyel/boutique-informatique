package sn.boutique.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import sn.boutique.catalogue.modele.EtatProduit;
import sn.boutique.catalogue.modele.TypeMedia;

/**
 * Fiche produit complète ({@code /produit/{slug}}).
 *
 * @param filAriane       catégories de la racine à celle du produit (incluse)
 * @param prixMinimum     prix de la variante active la moins chère
 * @param variantes       variantes actives, de la moins chère à la plus chère
 * @param medias          médias dans l'ordre de la galerie
 * @param caracteristiques tableau de caractéristiques, par groupe ; une caractéristique propre aux variantes
 *                        porte la valeur de la première variante et {@code parVariante = true}
 */
public record FicheProduitReponse(
        Long id,
        String slug,
        String nom,
        String descriptionCourte,
        String descriptionLongue,
        EtatProduit etat,
        int garantieMois,
        String gtin,
        String mpn,
        String metaTitre,
        String metaDescription,
        Marque marque,
        LienReponse categorie,
        List<LienReponse> filAriane,
        Long prixMinimum,
        Disponibilite disponibilite,
        BigDecimal noteMoyenne,
        int nombreAvis,
        List<Variante> variantes,
        List<Media> medias,
        List<GroupeCaracteristiques> caracteristiques,
        Instant dateModification) {

    public record Marque(String nom, String slug, String logoUrl) {
    }

    /**
     * @param caracteristiques valeurs propres à cette variante (la RAM, le stockage…), qui remplacent
     *                         celles du tableau quand la variante est choisie
     */
    public record Variante(
            Long id,
            String sku,
            String libelle,
            Map<String, String> attributs,
            long prix,
            Long prixBarre,
            Integer reduction,
            Disponibilite disponibilite,
            Integer poidsGrammes,
            List<ValeurVariante> caracteristiques) {
    }

    public record ValeurVariante(String cle, String valeur, String unite) {
    }

    /**
     * @param varianteId photo propre à une variante (une couleur), ou {@code null}
     * @param idYoutube  identifiant de la vidéo pour une {@code VIDEO_YOUTUBE} (façade légère côté site)
     */
    public record Media(
            Long id,
            TypeMedia type,
            String url,
            String urlMiniature,
            String texteAlternatif,
            boolean principal,
            Long varianteId,
            String idYoutube) {
    }

    public record GroupeCaracteristiques(String groupe, List<Caracteristique> elements) {
    }

    /** @param valeur valeur mise en forme en français (« 15,6 », « Oui ») ; l'unité est à part (police à chasse fixe). */
    public record Caracteristique(String cle, String libelle, String valeur, String unite, boolean parVariante) {
    }
}
