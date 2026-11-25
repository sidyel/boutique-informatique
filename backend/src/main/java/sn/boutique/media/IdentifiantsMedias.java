package sn.boutique.media;

import java.security.SecureRandom;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * idPublic Cloudinary des médias : {@code <racine>/<dossier>/<nom>-<aléatoire>}, par exemple
 * {@code boutique/produits/asus-vivobook-15-x1504va-k3f9q2mz}.
 * <p>
 * Le nom d'origine du fichier n'est <strong>jamais</strong> utilisé : il peut contenir des caractères
 * spéciaux ou des données personnelles (« photo-awa-diop.jpg ») qui se retrouveraient dans une URL
 * publique. Le nom vient du slug du produit (ou d'un libellé fixe), le suffixe aléatoire évite les
 * collisions et empêche de deviner les adresses des autres fichiers.
 */
@Component
@RequiredArgsConstructor
public class IdentifiantsMedias {

    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int LONGUEUR_ALEATOIRE = 8;
    private static final int LONGUEUR_NOM_MAX = 80;
    private static final Pattern NON_AUTORISE = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern TIRETS = Pattern.compile("-{2,}");

    private final SecureRandom aleatoire = new SecureRandom();
    private final ProprietesMedias proprietes;

    /** @param nom slug du produit ou libellé fixe (« logo », « banniere-accueil »), jamais le nom du fichier */
    public String nouvelIdPublic(UsageImage usage, String nom) {
        return nouvelIdPublic(usage.dossier(), nom);
    }

    public String nouvelIdPublic(String dossier, String nom) {
        return proprietes.dossierRacine() + "/" + dossier + "/" + nettoyer(nom) + "-" + suffixe();
    }

    /** Défense en profondeur : un slug est déjà sûr, mais rien d'autre qu'a-z, 0-9 et « - » ne passe. */
    static String nettoyer(String nom) {
        String propre = NON_AUTORISE.matcher(nom == null ? "" : nom.toLowerCase(java.util.Locale.ROOT)).replaceAll("-");
        propre = TIRETS.matcher(propre).replaceAll("-").replaceAll("^-|-$", "");
        if (propre.length() > LONGUEUR_NOM_MAX) {
            propre = propre.substring(0, LONGUEUR_NOM_MAX).replaceAll("-$", "");
        }
        return propre.isEmpty() ? "media" : propre;
    }

    private String suffixe() {
        StringBuilder suffixe = new StringBuilder(LONGUEUR_ALEATOIRE);
        for (int i = 0; i < LONGUEUR_ALEATOIRE; i++) {
            suffixe.append(ALPHABET.charAt(aleatoire.nextInt(ALPHABET.length())));
        }
        return suffixe.toString();
    }
}
