package sn.boutique.catalogue.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.dto.AjoutYoutubeRequete;
import sn.boutique.catalogue.dto.DeclarationVideoRequete;
import sn.boutique.catalogue.dto.MediasProduitAdminReponse;
import sn.boutique.catalogue.dto.SignatureEnvoiVideoReponse;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.media.IdentifiantsMedias;
import sn.boutique.media.LienYoutube;
import sn.boutique.media.NettoyageStockage;
import sn.boutique.media.ProprietesMedias;
import sn.boutique.media.SignatureEnvoiVideo;
import sn.boutique.media.SignatureEnvoiVideoDepot;
import sn.boutique.media.StockageMedia;
import sn.boutique.media.StockageMedia.MediaStocke;
import sn.boutique.media.StockageMedia.SignatureEnvoi;
import sn.boutique.media.StockageMedia.TypeRessource;
import sn.boutique.media.UsageImage;
import sn.boutique.media.VerificationYoutube;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Vidéos des produits (ADR 0005) : envoi direct navigateur → Cloudinary avec une signature propre au produit,
 * puis déclaration vérifiée ; ou lien YouTube vérifié par oEmbed, avec sa miniature copiée sur Cloudinary.
 */
@Service
@RequiredArgsConstructor
public class VideoAdminService {

    private final ProduitDepot produitDepot;
    private final SignatureEnvoiVideoDepot signatures;
    private final StockageMedia stockage;
    private final NettoyageStockage nettoyage;
    private final IdentifiantsMedias identifiants;
    private final VerificationYoutube youtube;
    private final ProprietesMedias proprietes;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    /**
     * Signature d'envoi pour un nouvel idPublic du dossier vidéo de ce produit. L'horodatage signé est antidaté :
     * Cloudinary accepte une signature pendant 1 h, elle n'est donc valable que {@code validite-signature}.
     * Chaque signature est enregistrée (vidéos envoyées mais jamais déclarées).
     */
    @Transactional
    public SignatureEnvoiVideoReponse signer(Long produitId) {
        Produit produit = trouverProduit(produitId);
        verifierLimiteVideos(produit);
        ProprietesMedias.Videos videos = proprietes.videos();
        Instant maintenant = horloge.instant();
        Instant horodatage = maintenant.minus(ProprietesMedias.Videos.FENETRE_CLOUDINARY).plus(videos.validiteSignature());
        String idPublic = identifiants.nouvelIdPublic(dossierVideos(produitId), produit.getSlug());

        SignatureEnvoi signature = stockage.signerEnvoiVideo(idPublic, horodatage);
        SignatureEnvoiVideo trace = signatures.save(
                new SignatureEnvoiVideo(produitId, idPublic, maintenant.plus(videos.validiteSignature())));
        return new SignatureEnvoiVideoReponse(signature.urlEnvoi(), signature.parametres(), idPublic,
                trace.getDateExpiration(), videos.tailleMax().toBytes(), videos.formats());
    }

    /**
     * Déclare une vidéo envoyée avec une signature de CE produit : vérifiée sur le stockage (présente, en
     * ressource vidéo, format et taille acceptés), sinon supprimée et refusée.
     */
    public MediasProduitAdminReponse declarer(Long produitId, DeclarationVideoRequete requete) {
        String idPublic = requete.idPublic().strip();
        transaction.executeWithoutResult(statut -> {
            Produit produit = trouverProduit(produitId);
            MediaAdminService.varianteDuProduit(produit, requete.varianteId());
            verifierSignature(produitId, idPublic);
            verifierLimiteVideos(produit);
        });

        MediaStocke video = stockage.lireVideo(idPublic).orElseThrow(() -> new RegleMetierException("VIDEO_INTROUVABLE",
                "La vidéo n'est pas sur le stockage : l'envoi n'est pas terminé ou a échoué. Réessayez après la fin "
                        + "de l'envoi."));
        ProprietesMedias.Videos regles = proprietes.videos();
        String format = video.format() == null ? "" : video.format().toLowerCase(Locale.ROOT);
        if (!regles.formats().contains(format) || video.octets() > regles.tailleMax().toBytes()) {
            nettoyage.supprimer(idPublic, TypeRessource.VIDEO, "vidéo refusée à la déclaration, produit " + produitId);
            throw new RegleMetierException("VIDEO_INVALIDE", "Vidéo refusée : formats acceptés "
                    + String.join(", ", regles.formats()) + ", " + regles.tailleMax().toMegabytes()
                    + " Mo au plus. Le fichier envoyé a été supprimé.");
        }

        try {
            return transaction.execute(statut -> enregistrerVideo(produitId, video, requete));
        } catch (RegleMetierException e) {
            // Double déclaration (double clic) : la vidéo appartient déjà au produit, surtout ne pas la supprimer
            if (!"VIDEO_DEJA_DECLAREE".equals(e.getCode())) {
                nettoyage.supprimer(idPublic, TypeRessource.VIDEO, "compensation : déclaration refusée, produit " + produitId);
            }
            throw e;
        } catch (RuntimeException e) {
            nettoyage.supprimer(idPublic, TypeRessource.VIDEO, "compensation : enregistrement impossible, produit " + produitId);
            throw e;
        }
    }

    private MediasProduitAdminReponse enregistrerVideo(Long produitId, MediaStocke video, DeclarationVideoRequete requete) {
        // Verrou du produit : deux déclarations simultanées sont traitées l'une après l'autre
        Produit produit = verrouiller(produitId);
        SignatureEnvoiVideo signature = verifierSignature(produitId, video.idPublic());
        verifierLimiteVideos(produit);
        MediaProduit media = produit.ajouterMedia(TypeMedia.VIDEO_FICHIER, stockage.urlVideo(video),
                requete.texteAlternatif().strip());
        media.setIdPublic(video.idPublic());
        media.setUrlMiniature(stockage.urlMiniatureVideo(video));
        media.setVariante(MediaAdminService.varianteDuProduit(produit, requete.varianteId()));
        signature.utiliser(horloge.instant());
        produitDepot.flush();
        publierModification(produit);
        return MediasProduitAdminReponse.depuis(produit, proprietes);
    }

    /**
     * Lien YouTube : vidéo publique et intégrable (oEmbed), miniature copiée sur Cloudinary pour être servie
     * par le site (URL transformée, sans appel à YouTube à l'affichage de la fiche).
     */
    public MediasProduitAdminReponse ajouterYoutube(Long produitId, AjoutYoutubeRequete requete) {
        String identifiant = LienYoutube.extraireIdentifiant(requete.lien())
                .orElseThrow(() -> new RegleMetierException("LIEN_YOUTUBE_INVALIDE",
                        "Lien YouTube non reconnu : coller l'adresse de la vidéo (youtube.com/watch?v=…, youtu.be/…)."));
        String slug = transaction.execute(statut -> {
            Produit produit = trouverProduit(produitId);
            MediaAdminService.varianteDuProduit(produit, requete.varianteId());
            verifierLimiteVideos(produit);
            return produit.getSlug();
        });

        youtube.verifier(identifiant);
        MediaStocke miniature = stockage.envoyerImageDistante(urlMiniatureYoutube(identifiant),
                identifiants.nouvelIdPublic(UsageImage.PRODUIT, slug + "-youtube"));

        try {
            return transaction.execute(statut -> {
                Produit produit = verrouiller(produitId);
                verifierLimiteVideos(produit);
                MediaProduit media = produit.ajouterMedia(TypeMedia.VIDEO_YOUTUBE, LienYoutube.urlCanonique(identifiant),
                        requete.texteAlternatif().strip());
                media.setIdPublic(miniature.idPublic());
                media.setUrlMiniature(stockage.urlImage(miniature));
                media.setVariante(MediaAdminService.varianteDuProduit(produit, requete.varianteId()));
                produitDepot.flush();
                publierModification(produit);
                return MediasProduitAdminReponse.depuis(produit, proprietes);
            });
        } catch (RuntimeException e) {
            nettoyage.supprimer(miniature.idPublic(), TypeRessource.IMAGE,
                    "compensation : miniature YouTube non enregistrée, produit " + produitId);
            throw e;
        }
    }

    /** Miniature fixe construite par le code (jamais l'URL renvoyée par un tiers) : toujours présente. */
    static String urlMiniatureYoutube(String identifiant) {
        return "https://i.ytimg.com/vi/" + identifiant + "/hqdefault.jpg";
    }

    /** Dossier vidéo propre au produit : {@code <racine>/produits/<id>/videos}. */
    static String dossierVideos(Long produitId) {
        return "produits/" + produitId + "/videos";
    }

    // ------------------------------------------------------------------------------------------------

    /**
     * La vidéo doit avoir été signée pour CE produit, dans son dossier, n'être déclarée qu'une fois et dans les
     * délais. Un idPublic inconnu ou d'un autre produit reçoit la même réponse (rien n'est révélé).
     */
    private SignatureEnvoiVideo verifierSignature(Long produitId, String idPublic) {
        String dossier = proprietes.dossierRacine() + "/" + dossierVideos(produitId) + "/";
        SignatureEnvoiVideo signature = signatures.findByIdPublic(idPublic)
                .filter(trouvee -> trouvee.getProduitId().equals(produitId))
                .filter(trouvee -> idPublic.startsWith(dossier) && (trouvee.getDossier() + "/").equals(dossier))
                .orElseThrow(() -> new RegleMetierException("VIDEO_AUTRE_PRODUIT",
                        "Cette vidéo n'a pas été préparée pour ce produit : demander une signature d'envoi depuis "
                                + "la page de ce produit."));
        if (signature.estUtilisee()) {
            throw new RegleMetierException("VIDEO_DEJA_DECLAREE", "Cette vidéo est déjà enregistrée sur le produit.");
        }
        Duration delai = proprietes.videos().delaiDeclaration();
        if (horloge.instant().isAfter(signature.getDateExpiration().plus(delai))) {
            throw new RegleMetierException("SIGNATURE_EXPIREE",
                    "La signature d'envoi a expiré : recommencer l'envoi de la vidéo.");
        }
        return signature;
    }

    private void verifierLimiteVideos(Produit produit) {
        int max = proprietes.videos().maxParProduit();
        long actuelles = produit.getMedias().stream().filter(media -> media.getType() != TypeMedia.IMAGE).count();
        if (actuelles >= max) {
            throw new RegleMetierException("TROP_DE_VIDEOS", max == 0
                    ? "Les vidéos sont désactivées."
                    : "Un produit peut avoir au plus %d vidéo(s) : supprimer d'abord la vidéo actuelle.".formatted(max));
        }
    }

    private Produit trouverProduit(Long produitId) {
        return produitDepot.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", String.valueOf(produitId)));
    }

    private Produit verrouiller(Long produitId) {
        return produitDepot.verrouiller(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", String.valueOf(produitId)));
    }

    private void publierModification(Produit produit) {
        evenements.publishEvent(new CatalogueModifie("vidéo du produit " + produit.getSlug()));
    }
}
