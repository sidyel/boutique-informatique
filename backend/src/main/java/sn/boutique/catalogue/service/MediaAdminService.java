package sn.boutique.catalogue.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.catalogue.depot.MediaProduitDepot;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.dto.MediasProduitAdminReponse;
import sn.boutique.catalogue.dto.ModificationMediaRequete;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.catalogue.validation.TexteAlternatifValidateur;
import sn.boutique.media.FichierVerifie;
import sn.boutique.media.IdentifiantsMedias;
import sn.boutique.media.MediaRetire;
import sn.boutique.media.NettoyageStockage;
import sn.boutique.media.ProprietesMedias;
import sn.boutique.media.StockageMedia;
import sn.boutique.media.StockageMedia.MediaStocke;
import sn.boutique.media.StockageMedia.TypeRessource;
import sn.boutique.media.UsageImage;
import sn.boutique.media.VerificationFichiers;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.ParametreInvalideException;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Images des produits dans le back-office (ADR 0005).
 * <p>
 * Envoi : contrôles, vérification des fichiers, envoi à Cloudinary <strong>hors transaction</strong>, puis
 * enregistrement sous verrou du produit ; si l'enregistrement échoue, les fichiers envoyés sont supprimés
 * (compensation). Suppression : en base d'abord, puis sur Cloudinary après la validation.
 * Les médias sans idPublic (images de démonstration servies par le site) ne déclenchent aucun appel.
 */
@Service
@RequiredArgsConstructor
public class MediaAdminService {

    private final ProduitDepot produitDepot;
    private final MediaProduitDepot mediaDepot;
    private final VerificationFichiers verification;
    private final IdentifiantsMedias identifiants;
    private final StockageMedia stockage;
    private final NettoyageStockage nettoyage;
    private final ProprietesMedias proprietes;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher evenements;

    @Transactional(readOnly = true)
    public MediasProduitAdminReponse lister(Long produitId) {
        return reponse(trouverProduit(produitId));
    }

    /**
     * @param textesAlternatifs un par fichier, dans le même ordre
     * @param varianteId        variante commune aux images envoyées (facultative)
     */
    public MediasProduitAdminReponse ajouterImages(Long produitId, List<MultipartFile> fichiers,
                                                   List<String> textesAlternatifs, Long varianteId) {
        List<String> textes = verifierTextes(fichiers, textesAlternatifs);

        // 1. Contrôles rapides avant tout travail coûteux
        String slug = transaction.execute(statut -> {
            Produit produit = trouverProduit(produitId);
            varianteDuProduit(produit, varianteId);
            verifierLimite(produit, fichiers.size());
            return produit.getSlug();
        });

        // 2. Tous les fichiers sont vérifiés avant le premier envoi : un fichier refusé, rien n'est envoyé
        List<FichierVerifie> verifies = new ArrayList<>();
        for (int i = 0; i < fichiers.size(); i++) {
            try {
                verifies.add(verification.verifierImage(contenu(fichiers.get(i), i), UsageImage.PRODUIT));
            } catch (RegleMetierException e) {
                throw new RegleMetierException(e.getCode(), "Fichier n° " + (i + 1) + " : " + e.getMessage());
            }
        }

        // 3. Envoi, hors transaction
        List<MediaStocke> envoyes = new ArrayList<>();
        try {
            for (FichierVerifie fichier : verifies) {
                envoyes.add(stockage.envoyerImage(fichier, identifiants.nouvelIdPublic(UsageImage.PRODUIT, slug)));
            }
        } catch (RuntimeException e) {
            compenser(envoyes, "envoi interrompu, produit " + produitId);
            throw e;
        }

        // 4. Enregistrement ; en cas d'échec, les fichiers envoyés ne doivent pas rester orphelins
        try {
            return transaction.execute(statut -> enregistrer(produitId, envoyes, textes, varianteId));
        } catch (RuntimeException e) {
            compenser(envoyes, "enregistrement impossible, produit " + produitId);
            throw e;
        }
    }

    private MediasProduitAdminReponse enregistrer(Long produitId, List<MediaStocke> envoyes, List<String> textes,
                                                  Long varianteId) {
        // Verrou : deux envois simultanés ne dépassent pas la limite à eux deux
        Produit produit = produitDepot.verrouiller(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", String.valueOf(produitId)));
        VarianteProduit variante = varianteDuProduit(produit, varianteId);
        verifierLimite(produit, envoyes.size());
        boolean sansPrincipale = produit.getMedias().stream().noneMatch(MediaProduit::isPrincipal);
        for (int i = 0; i < envoyes.size(); i++) {
            MediaStocke stocke = envoyes.get(i);
            MediaProduit media = produit.ajouterMedia(TypeMedia.IMAGE, stockage.urlImage(stocke), textes.get(i));
            media.setIdPublic(stocke.idPublic());
            media.setVariante(variante);
            media.setPrincipal(sansPrincipale && i == 0);
        }
        produitDepot.flush();
        publierModification(produit);
        return reponse(produit);
    }

    /** Ordre d'affichage : la liste doit citer chacun des médias du produit, une seule fois. */
    @Transactional
    public MediasProduitAdminReponse ordonner(Long produitId, List<Long> ids) {
        Produit produit = trouverProduit(produitId);
        Map<Long, MediaProduit> parId = produit.getMedias().stream()
                .collect(Collectors.toMap(MediaProduit::getId, Function.identity()));
        if (ids.size() != parId.size() || !new HashSet<>(ids).equals(parId.keySet())) {
            throw new RegleMetierException("ORDRE_INCOMPLET",
                    "L'ordre doit citer chacun des %d médias du produit une seule fois.".formatted(parId.size()));
        }
        for (int position = 0; position < ids.size(); position++) {
            parId.get(ids.get(position)).setPosition(position);
        }
        produit.getMedias().sort(Comparator.comparingInt(MediaProduit::getPosition));
        publierModification(produit);
        return reponse(produit);
    }

    @Transactional
    public MediasProduitAdminReponse definirPrincipal(Long produitId, Long mediaId) {
        Produit produit = trouverProduit(produitId);
        MediaProduit media = mediaDuProduit(produit, mediaId);
        if (media.getType() != TypeMedia.IMAGE) {
            throw new RegleMetierException("PRINCIPAL_IMAGE_SEULEMENT",
                    "Seule une image peut être l'image principale du produit.");
        }
        if (!media.isPrincipal()) {
            // Index unique partiel « une seule principale » : retirer l'ancienne avant de poser la nouvelle
            produit.getMedias().stream().filter(MediaProduit::isPrincipal)
                    .forEach(ancienne -> ancienne.setPrincipal(false));
            mediaDepot.flush();
            media.setPrincipal(true);
            publierModification(produit);
        }
        return reponse(produit);
    }

    @Transactional
    public MediasProduitAdminReponse modifier(Long produitId, Long mediaId, ModificationMediaRequete requete) {
        Produit produit = trouverProduit(produitId);
        MediaProduit media = mediaDuProduit(produit, mediaId);
        if (requete.isTexteAlternatifFourni()) {
            media.setTexteAlternatif(requete.getTexteAlternatif().strip());
        }
        if (requete.isVarianteIdFourni()) {
            media.setVariante(varianteDuProduit(produit, requete.getVarianteId()));
        }
        publierModification(produit);
        return reponse(produit);
    }

    /**
     * Retire le média en base ; son fichier est supprimé du stockage après la validation (plusieurs essais,
     * puis {@code media_orphelin}). La dernière image d'un produit publié ne peut pas être retirée.
     */
    @Transactional
    public MediasProduitAdminReponse supprimer(Long produitId, Long mediaId) {
        Produit produit = trouverProduit(produitId);
        MediaProduit media = mediaDuProduit(produit, mediaId);
        if (media.getType() == TypeMedia.IMAGE && produit.isActif() && nombreImages(produit) == 1) {
            throw new ConflitException("DERNIERE_IMAGE",
                    "C'est la dernière image d'un produit publié : ajouter une autre image ou désactiver d'abord "
                            + "le produit.");
        }
        boolean etaitPrincipale = media.isPrincipal();
        produit.getMedias().remove(media);
        // La suppression part avant la nouvelle image principale (index unique partiel)
        mediaDepot.flush();
        for (int position = 0; position < produit.getMedias().size(); position++) {
            produit.getMedias().get(position).setPosition(position);
        }
        if (etaitPrincipale) {
            produit.getMedias().stream().filter(autre -> autre.getType() == TypeMedia.IMAGE).findFirst()
                    .ifPresent(suivante -> suivante.setPrincipal(true));
        }
        if (media.getIdPublic() != null) {
            evenements.publishEvent(new MediaRetire(media.getIdPublic(), typeRessource(media),
                    "suppression du média " + mediaId + " du produit " + produit.getSlug()));
        }
        publierModification(produit);
        return reponse(produit);
    }

    // ------------------------------------------------------------------------------------------------

    private static List<String> verifierTextes(List<MultipartFile> fichiers, List<String> textesAlternatifs) {
        if (fichiers == null || fichiers.isEmpty()) {
            throw new ParametreInvalideException("fichiers", "Aucun fichier envoyé.");
        }
        List<String> textes = textesAlternatifs == null ? List.of() : textesAlternatifs;
        if (textes.size() != fichiers.size()) {
            throw new ParametreInvalideException("textesAlternatifs", "Un texte alternatif par fichier : "
                    + fichiers.size() + " fichier(s), " + textes.size() + " texte(s).");
        }
        List<String> nettoyes = new ArrayList<>();
        for (int i = 0; i < textes.size(); i++) {
            String texte = textes.get(i);
            if (!TexteAlternatifValidateur.estValide(texte)) {
                throw new ParametreInvalideException("textesAlternatifs[" + i + "]", "Texte alternatif du fichier n° "
                        + (i + 1) + " : il doit décrire l'image en " + TexteAlternatifValidateur.LONGUEUR_MIN + " à "
                        + TexteAlternatifValidateur.LONGUEUR_MAX + " caractères, pas reprendre un nom de fichier.");
            }
            nettoyes.add(texte.strip());
        }
        return nettoyes;
    }

    private void verifierLimite(Produit produit, int nouvelles) {
        int max = proprietes.images().maxParProduit();
        long actuelles = nombreImages(produit);
        if (actuelles + nouvelles > max) {
            throw new RegleMetierException("TROP_D_IMAGES",
                    "Un produit peut avoir au plus %d images : %d déjà enregistrée(s), %d envoyée(s)."
                            .formatted(max, actuelles, nouvelles));
        }
    }

    private static long nombreImages(Produit produit) {
        return produit.getMedias().stream().filter(media -> media.getType() == TypeMedia.IMAGE).count();
    }

    private static byte[] contenu(MultipartFile fichier, int index) {
        try {
            return fichier.getBytes();
        } catch (IOException e) {
            throw new ParametreInvalideException("fichiers", "Fichier n° " + (index + 1) + " illisible.");
        }
    }

    private void compenser(List<MediaStocke> envoyes, String raison) {
        for (MediaStocke stocke : envoyes) {
            nettoyage.supprimer(stocke.idPublic(), TypeRessource.IMAGE, "compensation : " + raison);
        }
    }

    private Produit trouverProduit(Long produitId) {
        return produitDepot.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", String.valueOf(produitId)));
    }

    private static MediaProduit mediaDuProduit(Produit produit, Long mediaId) {
        return produit.getMedias().stream().filter(media -> media.getId().equals(mediaId)).findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Média", String.valueOf(mediaId)));
    }

    private static VarianteProduit varianteDuProduit(Produit produit, Long varianteId) {
        if (varianteId == null) {
            return null;
        }
        return produit.getVariantes().stream().filter(variante -> variante.getId().equals(varianteId)).findFirst()
                .orElseThrow(() -> new RegleMetierException("VARIANTE_AUTRE_PRODUIT",
                        "La variante " + varianteId + " n'appartient pas à ce produit."));
    }

    /** Vidéo envoyée : ressource vidéo ; image ou miniature d'une vidéo YouTube : ressource image. */
    private static TypeRessource typeRessource(MediaProduit media) {
        return media.getType() == TypeMedia.VIDEO_FICHIER ? TypeRessource.VIDEO : TypeRessource.IMAGE;
    }

    private void publierModification(Produit produit) {
        evenements.publishEvent(new CatalogueModifie("médias du produit " + produit.getSlug()));
    }

    private MediasProduitAdminReponse reponse(Produit produit) {
        return MediasProduitAdminReponse.depuis(produit, proprietes.images().maxParProduit());
    }
}
