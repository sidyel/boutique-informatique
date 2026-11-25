package sn.boutique.media;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.noyau.erreur.ParametreInvalideException;

/**
 * Envoi d'une image isolée (paramètres du site, bannières) : vérification, idPublic généré, envoi hors
 * transaction. L'appelant enregistre ensuite l'URL et appelle {@link #annuler} si l'enregistrement échoue.
 */
@Component
@RequiredArgsConstructor
public class EnvoiImages {

    private final VerificationFichiers verification;
    private final IdentifiantsMedias identifiants;
    private final StockageMedia stockage;
    private final NettoyageStockage nettoyage;

    /** @param nom base de l'idPublic (« logo », « banniere-ordinateur »), jamais le nom du fichier envoyé */
    public ImageEnvoyee envoyer(MultipartFile fichier, UsageImage usage, String nom) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ParametreInvalideException("fichier", "Aucun fichier envoyé.");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new ParametreInvalideException("fichier", "Fichier illisible.");
        }
        FichierVerifie verifie = verification.verifierImage(contenu, usage);
        StockageMedia.MediaStocke stocke = stockage.envoyerImage(verifie, identifiants.nouvelIdPublic(usage, nom));
        return new ImageEnvoyee(stocke.idPublic(), stockage.urlImage(stocke), verifie.largeur(), verifie.hauteur(),
                verifie.estSvg());
    }

    /** Compensation : l'image envoyée n'a pas pu être enregistrée. */
    public void annuler(ImageEnvoyee image, String raison) {
        nettoyage.supprimer(image.idPublic(), StockageMedia.TypeRessource.IMAGE, "compensation : " + raison);
    }

    /** @param largeur null pour un SVG (dimensions sans objet) */
    public record ImageEnvoyee(String idPublic, String url, Integer largeur, Integer hauteur, boolean svg) {
    }
}
