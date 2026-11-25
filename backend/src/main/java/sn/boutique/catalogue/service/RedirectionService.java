package sn.boutique.catalogue.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.CategorieDepot;
import sn.boutique.catalogue.depot.HistoriqueSlugDepot;
import sn.boutique.catalogue.depot.MarqueDepot;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.dto.RedirectionReponse;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.HistoriqueSlug;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeEntiteSlug;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Résout l'ancien chemin d'une page (slug modifié) en redirection 301 vers son chemin actuel.
 * L'historique pointe vers l'entité, pas vers le slug suivant : jamais de chaîne de redirections.
 * <p>
 * Un produit ou une catégorie désactivé ne donne pas de redirection (404) : la réponse définitive
 * (301 vers la catégorie ou 410) sera décidée en phase 3 (ADR 0004).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RedirectionService {

    private static final Pattern CHEMIN =
            Pattern.compile("^/(produit|categorie|marque)/([a-z0-9][a-z0-9-]{0,219})/?$");
    private static final int DEFINITIVE = 301;

    private final HistoriqueSlugDepot historiqueDepot;
    private final ProduitDepot produitDepot;
    private final CategorieDepot categorieDepot;
    private final MarqueDepot marqueDepot;

    public RedirectionReponse resoudre(String chemin) {
        Matcher correspondance = CHEMIN.matcher(chemin == null ? "" : chemin);
        if (!correspondance.matches()) {
            throw introuvable(chemin);
        }
        String prefixe = correspondance.group(1);
        String ancienSlug = correspondance.group(2);
        TypeEntiteSlug type = switch (prefixe) {
            case "produit" -> TypeEntiteSlug.PRODUIT;
            case "categorie" -> TypeEntiteSlug.CATEGORIE;
            default -> TypeEntiteSlug.MARQUE;
        };
        return historiqueDepot.findByTypeEntiteAndAncienSlug(type, ancienSlug)
                .flatMap(this::slugActuel)
                .filter(slug -> !slug.equals(ancienSlug))
                .map(slug -> new RedirectionReponse("/" + prefixe + "/" + slug, DEFINITIVE))
                .orElseThrow(() -> introuvable(chemin));
    }

    private Optional<String> slugActuel(HistoriqueSlug historique) {
        Long id = historique.getEntiteId();
        return switch (historique.getTypeEntite()) {
            case PRODUIT -> produitDepot.findById(id).filter(Produit::isActif).map(Produit::getSlug);
            case CATEGORIE -> categorieDepot.findById(id).filter(Categorie::isActif).map(Categorie::getSlug);
            case MARQUE -> marqueDepot.findById(id).map(Marque::getSlug);
            case ARTICLE -> Optional.empty();
        };
    }

    private static RessourceIntrouvableException introuvable(String chemin) {
        return new RessourceIntrouvableException("Redirection", chemin);
    }
}
