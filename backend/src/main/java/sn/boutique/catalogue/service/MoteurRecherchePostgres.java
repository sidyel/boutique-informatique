package sn.boutique.catalogue.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.dto.FiltresReponse.Option;
import sn.boutique.catalogue.dto.LienReponse;
import sn.boutique.catalogue.dto.PageReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.catalogue.dto.RechercheReponse;
import sn.boutique.catalogue.dto.SuggestionsReponse;
import sn.boutique.catalogue.service.CriteresProduits.Famille;
import sn.boutique.noyau.erreur.ParametreInvalideException;

/**
 * Recherche avec PostgreSQL : index plein texte pondéré (V7, insensible aux accents) et, à défaut de
 * résultat, trigrammes (fautes de frappe : « lenvo ideapad »).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoteurRecherchePostgres implements MoteurRecherche {

    private static final String PLEIN_TEXTE =
            "p.vecteur_recherche @@ websearch_to_tsquery('francais_sans_accents', :texte)";
    private static final String PERTINENCE =
            "ts_rank_cd(p.vecteur_recherche, websearch_to_tsquery('francais_sans_accents', :texte), 32) desc";
    private static final String APPROCHANT = """
            (sans_accents(lower(:texte)) <% sans_accents(lower(p.nom))
             or exists (select 1 from marque mr where mr.id = p.marque_id
                         and sans_accents(lower(:texte)) <% sans_accents(lower(mr.nom))))""";
    private static final String PROXIMITE = """
            greatest(word_similarity(sans_accents(lower(:texte)), sans_accents(lower(p.nom))),
                     (select word_similarity(sans_accents(lower(:texte)), sans_accents(lower(mr.nom)))
                        from marque mr where mr.id = p.marque_id)) desc""";
    /** Autocomplétion : début de mot dans le nom du produit ou de la marque, ou proximité (fautes). */
    private static final String SUGGESTION = """
            (sans_accents(lower(p.nom)) ~ ('(^|[^a-z0-9])' || :motif)
             or exists (select 1 from marque mr where mr.id = p.marque_id
                         and (sans_accents(lower(mr.nom)) ~ ('^' || :motif)
                              or sans_accents(lower(:texte)) <% sans_accents(lower(mr.nom))))
             or sans_accents(lower(:texte)) <% sans_accents(lower(p.nom)))""";
    /**
     * Seuil des opérateurs {@code <%} (0,6 par défaut dans pg_trgm). Mesuré sur des fautes courantes :
     * « lenvo » / Lenovo 0,50, « samsng » / Samsung 0,57, « lenvo ideapad » / IdeaPad 0,57, alors que des mots
     * sans rapport restent sous 0,2 (« asus » / Acer). Fixé pour la seule transaction en cours, et pour la
     * recherche approchante seulement (pas pour l'autocomplétion).
     */
    private static final String SEUIL_APPROCHANT = "0.45";
    private static final int SUGGESTIONS_PRODUITS = 6;
    private static final int SUGGESTIONS_AUTRES = 3;
    private static final int PRODUITS_SI_AUCUN_RESULTAT = 8;

    private final JdbcClient jdbc;
    private final ListeProduitsService listeProduitsService;
    private final CategorieService categorieService;
    private final MarqueService marqueService;
    private final ResumesProduits resumesProduits;

    @Override
    public RechercheReponse rechercher(String saisie, CriteresProduits criteres) {
        String texte = nettoyer(saisie);
        List<Long> categories = listeProduitsService.categories(criteres);
        Map<String, Object> parametres = Map.of("texte", texte);

        boolean approximatif = false;
        PageReponse<ProduitResumeReponse> page = listeProduitsService.paginer(
                new FiltresSql(criteres, categories, PLEIN_TEXTE, parametres), ordre(criteres, PERTINENCE), criteres);
        String condition = PLEIN_TEXTE;
        if (page.totalElements() == 0) {
            appliquerSeuilApprochant();
            approximatif = true;
            condition = APPROCHANT;
            page = listeProduitsService.paginer(
                    new FiltresSql(criteres, categories, APPROCHANT, parametres), ordre(criteres, PROXIMITE), criteres);
        }
        RechercheReponse.Suggestions suggestions = null;
        List<Option> facettesCategories = List.of();
        List<Option> facettesMarques = List.of();
        if (page.totalElements() == 0) {
            approximatif = false;
            suggestions = suggestionsSiAucunResultat();
        } else {
            // Chaque facette ignore son propre filtre : toutes les catégories, ou toutes les marques
            facettesCategories = facette(criteres.avecCategorie(null), null, condition,
                    parametres, "c.slug", "c.nom", "join categorie c on c.id = p.categorie_id", Set.of());
            facettesMarques = facette(criteres.sans(Famille.MARQUE, null), categories, condition, parametres,
                    "m.slug", "m.nom", "join marque m on m.id = p.marque_id", criteres.marques());
        }
        return new RechercheReponse(texte, approximatif, page.elements(), page.page(), page.taille(),
                page.totalElements(), page.totalPages(), facettesCategories, facettesMarques, suggestions);
    }

    @Override
    public SuggestionsReponse suggerer(String saisie) {
        // Seuil par défaut de pg_trgm (0,6) : pendant la frappe, le début de mot suffit et un seuil bas
        // proposerait des produits voisins sans rapport (« quillvue » → « Quillbook »)
        String texte = nettoyer(saisie);
        List<ResumesProduits.Selection> selections = jdbc.sql("""
                        select p.id as produit_id, v.id as variante_id
                          from produit p
                          cross join lateral (select vv.id from variante_produit vv
                                               where vv.produit_id = p.id and vv.actif
                                               order by vv.prix, vv.id limit 1) v
                         where p.actif and %s
                         order by %s, p.nombre_ventes desc, p.id
                         limit :limite
                        """.formatted(SUGGESTION, PROXIMITE))
                .param("texte", texte)
                .param("motif", echapperRegex(sansAccents(texte)))
                .param("limite", SUGGESTIONS_PRODUITS)
                .query((ligne, n) -> new ResumesProduits.Selection(ligne.getLong("produit_id"), ligne.getLong("variante_id")))
                .list();
        String recherche = sansAccents(texte);
        List<LienReponse> categories = new ArrayList<>();
        parcourir(categorieService.chargerArbre().racines(), recherche, categories);
        List<LienReponse> marques = marqueService.lister().stream()
                .filter(marque -> commencePar(marque.nom(), recherche))
                .limit(SUGGESTIONS_AUTRES)
                .map(marque -> new LienReponse(marque.nom(), marque.slug()))
                .toList();
        return new SuggestionsReponse(resumesProduits.lire(selections),
                categories.stream().limit(SUGGESTIONS_AUTRES).toList(), marques);
    }

    private void appliquerSeuilApprochant() {
        // set_config(..., true) : équivalent de SET LOCAL, annulé à la fin de la transaction
        jdbc.sql("select set_config('pg_trgm.word_similarity_threshold', :seuil, true)")
                .param("seuil", SEUIL_APPROCHANT).query(String.class).single();
    }

    /** Nombre de produits trouvés par catégorie ou par marque (facette de la page de résultats). */
    private List<Option> facette(CriteresProduits criteres, List<Long> categories, String condition,
                                 Map<String, Object> parametresRecherche, String slug, String nom, String jointure,
                                 Set<String> selection) {
        FiltresSql filtres = new FiltresSql(criteres, categories, condition, parametresRecherche);
        return jdbc.sql("select %s as slug, %s as nom, count(*) as nombre %s".formatted(slug, nom,
                                filtres.produitsAvecVarianteRetenue(jointure))
                        + "\ngroup by " + slug + ", " + nom + "\norder by nombre desc, nom")
                .params(new HashMap<>(filtres.parametres()))
                .query((ligne, n) -> new Option(ligne.getString("slug"), ligne.getString("nom"), ligne.getLong("nombre"),
                        selection.contains(ligne.getString("slug"))))
                .list();
    }

    private RechercheReponse.Suggestions suggestionsSiAucunResultat() {
        List<LienReponse> categories = categorieService.chargerArbre().racines().stream()
                .filter(noeud -> noeud.nombreProduits() > 0)
                .map(noeud -> new LienReponse(noeud.nom(), noeud.slug()))
                .toList();
        CriteresProduits recommandes = new CriteresProduits(null, Set.of(), Set.of(), null, null, true, false, Map.of(),
                TriProduits.RECOMMANDE, 1, PRODUITS_SI_AUCUN_RESULTAT);
        return new RechercheReponse.Suggestions(categories, listeProduitsService.lister(recommandes).elements());
    }

    private static String ordre(CriteresProduits criteres, String pertinence) {
        return criteres.tri() == null || criteres.tri() == TriProduits.PERTINENCE ? pertinence : criteres.tri().getOrdreSql();
    }

    private static void parcourir(List<ArbreCategories.Noeud> noeuds, String recherche, List<LienReponse> resultat) {
        for (ArbreCategories.Noeud noeud : noeuds) {
            if (noeud.nombreProduits() > 0 && commencePar(noeud.nom(), recherche)) {
                resultat.add(new LienReponse(noeud.nom(), noeud.slug()));
            }
            parcourir(noeud.enfants(), recherche, resultat);
        }
    }

    /** Un mot du nom commence par le texte saisi (« portab » → « PC portables »), sans accents ni casse. */
    private static boolean commencePar(String nom, String recherche) {
        String normalise = sansAccents(nom);
        return normalise.startsWith(recherche) || normalise.contains(" " + recherche);
    }

    static String nettoyer(String saisie) {
        String texte = saisie == null ? "" : saisie.strip().replaceAll("\\s+", " ");
        if (texte.length() < LONGUEUR_MIN || texte.length() > LONGUEUR_MAX) {
            throw new ParametreInvalideException("q", "La recherche doit contenir entre %d et %d caractères."
                    .formatted(LONGUEUR_MIN, LONGUEUR_MAX));
        }
        return texte;
    }

    private static String sansAccents(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Le texte saisi devient un motif d'expression régulière littéral (pas de « .* » injecté). */
    private static String echapperRegex(String texte) {
        return texte.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }
}
