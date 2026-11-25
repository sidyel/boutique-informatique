package sn.boutique.catalogue.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TexteAlternatifValidateurTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "IMG_1234.jpg", "IMG_1234", "img-20240101-143022", "DSC0001", "DSCN0042.JPG", "DSCF1234",
            "PXL_20240101_143022123", "PXL_20240101_143022123.MP.jpg", "MVIMG_20230405_101010", "P1000123",
            "GOPR0001", "photo.png", "vivobook face.JPG", "Asus Vivobook 15.webp", "image001", "photo_12",
            "Screenshot_20240101-101010", "Screenshot 2024-01-01 at 10.00.00", "Capture d'écran 2025-01-01 à 10.22.33",
            "Capture d’écran 2025-01-01 à 10.22.33", "WhatsApp Image 2024-05-01 at 10.00.00",
            "20240101_143022", "1234 5678", "3f2b9c1e-8a7d-4c6b-9e5f-1a2b3c4d5e6f", "a3f9c2e17b4d8e60",
            "produit_final_v2", "asus_tuf_a15",
            "abcd", "    ", ""})
    void refuseLesNomsDeFichierEtLesTextesTropCourts(String texte) {
        assertThat(TexteAlternatifValidateur.estValide(texte)).as(texte).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Asus Vivobook 15 vu de face", "Photo 15 pouces du Vivobook", "Écran 27 pouces, vue arrière",
            "Clavier AZERTY rétroéclairé", "HP 250 G9 – 8 Go / 256 Go", "Image du port USB-C 3.2",
            "Capture d'écran de Windows 11 sur le Vivobook", "  Souris sans fil Logitech M185  ", "PC i5"})
    void accepteLesDescriptions(String texte) {
        assertThat(TexteAlternatifValidateur.estValide(texte)).as(texte).isTrue();
    }

    @Test
    void lesLimitesDeLongueurPortentSurLeTexteSansEspacesDeBord() {
        assertThat(TexteAlternatifValidateur.estValide("Écran" + " ".repeat(10))).isTrue();
        assertThat(TexteAlternatifValidateur.estValide("Écran de PC " + "x".repeat(138))).as("150").isTrue();
        assertThat(TexteAlternatifValidateur.estValide("Écran de PC " + "x".repeat(139))).as("151").isFalse();
        assertThat(TexteAlternatifValidateur.estValide(null)).isFalse();
    }

    @Test
    void laSuggestionAssocieProduitEtVarianteSansDepasser150Caracteres() {
        assertThat(TexteAlternatifValidateur.suggestion("Asus Vivobook 15", "16 Go / 512 Go"))
                .isEqualTo("Asus Vivobook 15 – 16 Go / 512 Go");
        assertThat(TexteAlternatifValidateur.suggestion(" Asus Vivobook 15 ", null)).isEqualTo("Asus Vivobook 15");
        assertThat(TexteAlternatifValidateur.suggestion("Asus Vivobook 15", " ")).isEqualTo("Asus Vivobook 15");

        String longue = TexteAlternatifValidateur.suggestion("Ordinateur portable ".repeat(6), "Variante ".repeat(6));
        assertThat(longue).hasSizeLessThanOrEqualTo(150).doesNotEndWith(" ");
        assertThat(TexteAlternatifValidateur.estValide(longue)).isTrue();
    }
}
