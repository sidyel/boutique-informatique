package sn.boutique.marketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.marketing.dto.ImageBanniereReponse;
import sn.boutique.marketing.modele.FormatBanniere;
import sn.boutique.media.EnvoiImages;
import sn.boutique.media.EnvoiImages.ImageEnvoyee;
import sn.boutique.media.UsageImage;

/** Envoi des images de bannière (ordinateur, mobile) : contrôlées, stockées, avec avertissements de format. */
@Service
@RequiredArgsConstructor
public class ImagesBannieresService {

    private final EnvoiImages envoiImages;

    public ImageBanniereReponse envoyer(MultipartFile fichier, FormatBanniere format) {
        ImageEnvoyee image = envoiImages.envoyer(fichier, UsageImage.BANNIERE,
                "banniere-" + format.name().toLowerCase(java.util.Locale.ROOT));
        return new ImageBanniereReponse(image.idPublic(), image.url(), image.largeur(), image.hauteur(), format,
                format.libelle(), format.avertissements(image.largeur(), image.hauteur()));
    }
}
