package sn.boutique.apresvente.service;

import java.util.Locale;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.apresvente.depot.AvisDepot;
import sn.boutique.apresvente.dto.AvisReponse;
import sn.boutique.apresvente.modele.Avis;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.noyau.erreur.ParametreInvalideException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Avis approuvés affichés sur la fiche produit (lecture seule ; la publication arrive en phase 8). */
@Service
@RequiredArgsConstructor
public class AvisPublicService {

    public static final int TAILLE_PAR_DEFAUT = 5;
    public static final int TAILLE_MAX = 20;
    private static final int PAGE_MAX = 1000;

    static final String CLIENT_VERIFIE = "Client vérifié";
    static final String CLIENT = "Client";

    private static final Pattern ESPACES = Pattern.compile("\\s+");

    private final AvisDepot avisDepot;
    private final ProduitDepot produitDepot;

    @Transactional(readOnly = true)
    public PageReponse<AvisReponse> listerParProduit(String slug, int page, int taille) {
        if (page < 1 || page > PAGE_MAX) {
            throw new ParametreInvalideException("page", "La page doit être comprise entre 1 et " + PAGE_MAX + ".");
        }
        if (taille < 1 || taille > TAILLE_MAX) {
            throw new ParametreInvalideException("taille",
                    "La taille doit être comprise entre 1 et " + TAILLE_MAX + ".");
        }
        Long produitId = produitDepot.trouverIdPublieParSlug(slug)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", slug));
        Page<Avis> resultat = avisDepot.trouverApprouves(produitId, PageRequest.of(page - 1, taille));
        return PageReponse.de(resultat.map(AvisPublicService::versReponse).getContent(), page, taille,
                resultat.getTotalElements());
    }

    private static AvisReponse versReponse(Avis avis) {
        return new AvisReponse(avis.getId(), signature(avis), avis.isAchatVerifie(), avis.getNote(),
                avis.getTitre(), avis.getCommentaire(), avis.getDateCreation(), avis.getReponseBoutique(),
                avis.getDateReponse());
    }

    /**
     * « Prénom I. » ; compte supprimé (utilisateur à {@code null}) : « Client vérifié » si l'achat est vérifié,
     * « Client » sinon (ne jamais affirmer un achat qui n'a pas eu lieu, ADR 0004 §3). Le nom enregistré est
     * réduit à son initiale même s'il a été saisi en entier : aucun nom de famille complet n'est publié.
     * Le compte n'est pas chargé : seule la présence de la clé étrangère est testée.
     */
    static String signature(Avis avis) {
        if (avis.getUtilisateur() == null) {
            return anonyme(avis.isAchatVerifie());
        }
        return prenomEtInitiale(avis.getNomAuteur(), avis.isAchatVerifie());
    }

    static String prenomEtInitiale(String nomAuteur, boolean achatVerifie) {
        String texte = nomAuteur == null ? "" : nomAuteur.strip();
        if (texte.isEmpty()) {
            return anonyme(achatVerifie);
        }
        String[] mots = ESPACES.split(texte);
        String prenom = majusculeInitiale(mots[0]);
        if (mots.length == 1) {
            return prenom;
        }
        return prenom + " " + premierCaractere(mots[1]).toUpperCase(Locale.FRENCH) + ".";
    }

    private static String anonyme(boolean achatVerifie) {
        return achatVerifie ? CLIENT_VERIFIE : CLIENT;
    }

    private static String majusculeInitiale(String mot) {
        String premier = premierCaractere(mot);
        return premier.toUpperCase(Locale.FRENCH) + mot.substring(premier.length());
    }

    private static String premierCaractere(String mot) {
        return mot.substring(0, mot.offsetByCodePoints(0, 1));
    }
}
