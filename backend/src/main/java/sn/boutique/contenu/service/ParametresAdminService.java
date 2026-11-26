package sn.boutique.contenu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.contenu.modele.ImageParametre;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.media.EnvoiImages;
import sn.boutique.media.EnvoiImages.ImageEnvoyee;
import sn.boutique.media.MediaRetire;
import sn.boutique.media.StockageMedia.TypeRessource;

/**
 * Logo, logo sombre, favicon et image de partage. L'image remplacée est supprimée du stockage après la
 * validation ; les caches publics (dont celui de {@code /api/parametres}) sont vidés.
 */
@Service
@RequiredArgsConstructor
public class ParametresAdminService {

    private final ParametresSiteDepot parametresDepot;
    private final EnvoiImages envoiImages;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher evenements;
    private final ParametresService parametresService;

    public ParametresPublicsReponse remplacerImage(ImageParametre image, MultipartFile fichier) {
        ImageEnvoyee envoyee = envoiImages.envoyer(fichier, image.usage(), image.chemin());
        try {
            return transaction.execute(statut -> {
                ParametresSite parametres = parametresDepot.lire();
                String ancienIdPublic = image.idPublicActuel(parametres);
                image.remplacer(parametres, envoyee.url(), envoyee.idPublic());
                parametresDepot.saveAndFlush(parametres);
                if (ancienIdPublic != null) {
                    evenements.publishEvent(new MediaRetire(ancienIdPublic, TypeRessource.IMAGE,
                            "remplacement de l'image « " + image.chemin() + " » des paramètres"));
                }
                // Vide aussi le cache des paramètres publics, après la validation
                evenements.publishEvent(new CatalogueModifie("paramètres du site : " + image.chemin()));
                return ParametresPublicsReponse.depuis(parametres, parametresService.site());
            });
        } catch (RuntimeException e) {
            envoiImages.annuler(envoyee, "image « " + image.chemin() + " » des paramètres non enregistrée");
            throw e;
        }
    }
}
