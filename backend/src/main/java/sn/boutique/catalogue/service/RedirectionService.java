package sn.boutique.catalogue.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.CategorieDepot;
import sn.boutique.catalogue.depot.HistoriqueSlugDepot;
import sn.boutique.catalogue.depot.MarqueDepot;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.dto.LienReponse;
import sn.boutique.catalogue.dto.RedirectionReponse;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.HistoriqueSlug;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeEntiteSlug;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Réponse définitive pour une page qui n'existe plus à cette adresse (ADR 0007). L'historique pointe vers
 * l'entité, pas vers le slug suivant : jamais de chaîne de redirections.
 * <ul>
 *   <li>Slug modifié : 301 vers l'adresse actuelle.</li>
 *   <li>Produit masqué temporairement ({@code actif = false}, sans date de retrait) : 404, Google réessaiera.</li>
 *   <li>Produit retiré : 301 vers le premier successeur publié, sinon 410 (avec son rayon pour la page).</li>
 *   <li>Catégorie désactivée : 301 vers la catégorie parente active la plus proche, sinon 410.</li>
 *   <li>Page publiée à cette adresse, inconnue ou chemin invalide : 404 (aucune réponse à appliquer).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RedirectionService {

    private static final Pattern CHEMIN =
            Pattern.compile("^/(produit|categorie|marque)/([a-z0-9][a-z0-9-]{0,219})/?$");
    /** Garde-fou contre une boucle de remplaçants saisie par erreur (A → B → A). */
    private static final int SUCCESSEURS_MAX = 10;

    private final HistoriqueSlugDepot historiqueDepot;
    private final ProduitDepot produitDepot;
    private final CategorieDepot categorieDepot;
    private final MarqueDepot marqueDepot;

    public RedirectionReponse resoudre(String chemin) {
        Matcher correspondance = CHEMIN.matcher(chemin == null ? "" : chemin);
        if (!correspondance.matches()) {
            throw introuvable(chemin);
        }
        String slug = correspondance.group(2);
        Optional<RedirectionReponse> reponse = switch (correspondance.group(1)) {
            case "produit" -> produit(slug);
            case "categorie" -> categorie(slug);
            default -> marque(slug);
        };
        return reponse.orElseThrow(() -> introuvable(chemin));
    }

    private Optional<RedirectionReponse> produit(String slug) {
        Optional<Produit> trouve = produitDepot.findBySlug(slug)
                .or(() -> historique(TypeEntiteSlug.PRODUIT, slug).flatMap(produitDepot::findById));
        if (trouve.isEmpty()) {
            return Optional.empty();
        }
        Produit produit = trouve.get();
        if (produit.isActif()) {
            return renomme(produit.getSlug(), slug, "/produit/");
        }
        if (!produit.estRetire()) {
            // Masqué temporairement : 404, l'adresse redeviendra valide
            return Optional.empty();
        }
        Produit successeur = successeurPublie(produit);
        if (successeur != null) {
            return Optional.of(RedirectionReponse.vers("/produit/" + successeur.getSlug()));
        }
        Categorie rayon = premiereActive(produit.getCategorie());
        return Optional.of(RedirectionReponse.disparue(produit.getNom(),
                rayon == null ? null : new LienReponse(rayon.getNom(), rayon.getSlug())));
    }

    private Optional<RedirectionReponse> categorie(String slug) {
        Optional<Categorie> trouvee = categorieDepot.findBySlug(slug)
                .or(() -> historique(TypeEntiteSlug.CATEGORIE, slug).flatMap(categorieDepot::findById));
        if (trouvee.isEmpty()) {
            return Optional.empty();
        }
        Categorie categorie = trouvee.get();
        if (categorie.isActif()) {
            return renomme(categorie.getSlug(), slug, "/categorie/");
        }
        Categorie parente = premiereActive(categorie.getParent());
        return Optional.of(parente != null
                ? RedirectionReponse.vers("/categorie/" + parente.getSlug())
                : RedirectionReponse.disparue(categorie.getNom(), null));
    }

    private Optional<RedirectionReponse> marque(String slug) {
        return historique(TypeEntiteSlug.MARQUE, slug)
                .flatMap(marqueDepot::findById)
                .flatMap(marque -> renomme(marque.getSlug(), slug, "/marque/"));
    }

    /** 301 si l'adresse demandée est un ancien slug ; rien si c'est déjà l'adresse actuelle. */
    private static Optional<RedirectionReponse> renomme(String actuel, String demande, String prefixe) {
        return actuel.equals(demande) ? Optional.empty() : Optional.of(RedirectionReponse.vers(prefixe + actuel));
    }

    private Optional<Long> historique(TypeEntiteSlug type, String ancienSlug) {
        return historiqueDepot.findByTypeEntiteAndAncienSlug(type, ancienSlug).map(HistoriqueSlug::getEntiteId);
    }

    /** Premier remplaçant publié en suivant la chaîne des retraits (A remplacé par B, lui-même retiré pour C…). */
    private static Produit successeurPublie(Produit produit) {
        Set<Long> vus = new HashSet<>();
        Produit courant = produit.getProduitRemplacant();
        for (int i = 0; courant != null && i < SUCCESSEURS_MAX && vus.add(courant.getId()); i++) {
            if (courant.isActif()) {
                return courant;
            }
            if (!courant.estRetire()) {
                return null;
            }
            courant = courant.getProduitRemplacant();
        }
        return null;
    }

    /** La catégorie elle-même si elle est active, sinon sa parente active la plus proche. */
    private static Categorie premiereActive(Categorie categorie) {
        Categorie courante = categorie;
        for (int i = 0; courante != null && i < SUCCESSEURS_MAX; i++) {
            if (courante.isActif()) {
                return courante;
            }
            courante = courante.getParent();
        }
        return null;
    }

    private static RessourceIntrouvableException introuvable(String chemin) {
        return new RessourceIntrouvableException("Redirection", chemin);
    }
}
