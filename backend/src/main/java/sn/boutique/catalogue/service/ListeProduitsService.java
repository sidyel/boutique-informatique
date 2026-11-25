package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.erreur.ParametreInvalideException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Listes de produits filtrées, triées et paginées (pages catégorie et marque).
 * <p>
 * Une page coûte au plus quatre requêtes : arbre des catégories, définitions filtrables (seulement avec
 * des filtres {@code f.*}), page d'identifiants avec le total ({@code count(*) over ()}), cartes produit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListeProduitsService {

    private final JdbcClient jdbc;
    private final CategorieService categorieService;
    private final CaracteristiquesFiltrables caracteristiquesFiltrables;
    private final ResumesProduits resumesProduits;

    @Cacheable(cacheNames = CachesPublics.LISTE_PRODUITS, key = "#criteres.cleCache(true)")
    public PageReponse<ProduitResumeReponse> lister(CriteresProduits criteres) {
        if (criteres.tri() == TriProduits.PERTINENCE) {
            throw new ParametreInvalideException("tri", "Le tri par pertinence n'existe qu'avec une recherche.");
        }
        TriProduits tri = criteres.tri() == null ? TriProduits.RECOMMANDE : criteres.tri();
        return paginer(new FiltresSql(criteres, categories(criteres), null, Map.of()), tri.getOrdreSql(), criteres);
    }

    /**
     * Identifiants de la catégorie demandée et de ses descendants ({@code null} sans catégorie). Vérifie au
     * passage les filtres de caractéristiques, qui n'ont de sens que dans une catégorie.
     */
    List<Long> categories(CriteresProduits criteres) {
        if (criteres.categorie() == null) {
            if (!criteres.caracteristiques().isEmpty()) {
                throw new ParametreInvalideException("f", "Les filtres de caractéristiques demandent une catégorie.");
            }
            return null;
        }
        List<Long> categories = categorieService.chargerArbre().trouver(criteres.categorie())
                .orElseThrow(() -> new RessourceIntrouvableException("Catégorie", criteres.categorie()))
                .idsSousArbre();
        if (!criteres.caracteristiques().isEmpty()) {
            CaracteristiquesFiltrables.verifier(criteres, caracteristiquesFiltrables.communes(categories));
        }
        return categories;
    }

    /** Page triée ; {@code ordreSql} est un fragment ORDER BY fixe (jamais saisi par l'utilisateur). */
    PageReponse<ProduitResumeReponse> paginer(FiltresSql filtres, String ordreSql, CriteresProduits criteres) {
        Map<String, Object> parametres = new HashMap<>(filtres.parametres());
        parametres.put("limite", criteres.taille());
        parametres.put("decalage", criteres.decalage());
        List<ResumesProduits.Selection> selections = new ArrayList<>();
        long[] total = {-1};
        jdbc.sql("select p.id as produit_id, v.id as variante_id, count(*) over () as total "
                        + filtres.produitsAvecVarianteRetenue()
                        + "\norder by " + ordreSql + ", p.id\nlimit :limite offset :decalage")
                .params(parametres)
                .query(ligne -> {
                    selections.add(new ResumesProduits.Selection(ligne.getLong("produit_id"), ligne.getLong("variante_id")));
                    total[0] = ligne.getLong("total");
                });
        if (total[0] < 0) {
            // Page au-delà de la dernière (ou aucun résultat) : le total vient d'un simple décompte
            total[0] = criteres.page() == 1 ? 0 : jdbc.sql("select count(*) " + filtres.produitsAvecVarianteRetenue())
                    .params(filtres.parametres()).query(Long.class).single();
        }
        return PageReponse.de(resumesProduits.lire(selections), criteres.page(), criteres.taille(), total[0]);
    }
}
