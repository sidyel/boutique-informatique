package sn.boutique.catalogue.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.dto.AccueilReponse;
import sn.boutique.catalogue.dto.AccueilReponse.Section;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.noyau.cache.CachesPublics;

/**
 * Sections de produits de l'accueil, en une seule réponse (un seul aller-retour sur mobile).
 * <ul>
 *   <li><strong>Nouveautés</strong> : produits créés depuis moins de {@code anciennete-nouveautes} ;</li>
 *   <li><strong>Meilleures ventes</strong> si au moins {@code seuil-meilleures-ventes} produits ont été vendus,
 *       sinon <strong>Sélection du moment</strong> (produits mis en avant) ;</li>
 *   <li><strong>Promotions</strong> : variante retenue avec un prix barré.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccueilService {

    public static final String NOUVEAUTES = "nouveautes";
    public static final String MEILLEURES_VENTES = "meilleures-ventes";
    public static final String SELECTION = "selection";
    public static final String PROMOTIONS = "promotions";

    private final JdbcClient jdbc;
    private final ResumesProduits resumesProduits;
    private final ProprietesAccueil proprietes;
    private final Clock horloge;

    @Cacheable(cacheNames = CachesPublics.ACCUEIL, key = "'sections'")
    public AccueilReponse sections() {
        List<Section> sections = new ArrayList<>();
        Timestamp depuis = Timestamp.from(horloge.instant().minus(proprietes.ancienneteNouveautes()));
        ajouter(sections, NOUVEAUTES, "Nouveautés",
                selectionner(false, "p.date_creation >= :depuis", Map.of("depuis", depuis), TriProduits.NOUVEAUTE));
        if (produitsVendus() >= proprietes.seuilMeilleuresVentes()) {
            ajouter(sections, MEILLEURES_VENTES, "Meilleures ventes",
                    selectionner(false, "p.nombre_ventes > 0", Map.of(), TriProduits.POPULARITE));
        } else {
            ajouter(sections, SELECTION, "Sélection du moment",
                    selectionner(false, "p.mis_en_avant", Map.of(), TriProduits.RECOMMANDE));
        }
        ajouter(sections, PROMOTIONS, "Promotions", selectionner(true, null, Map.of(), TriProduits.RECOMMANDE));
        return new AccueilReponse(List.copyOf(sections));
    }

    private long produitsVendus() {
        return jdbc.sql("select count(*) from produit where actif and nombre_ventes > 0").query(Long.class).single();
    }

    /** {@code condition} : fragment SQL fixe sur {@code p}, jamais saisi par l'utilisateur. */
    private List<ProduitResumeReponse> selectionner(boolean promotion, String condition, Map<String, Object> valeurs,
                                                    TriProduits tri) {
        CriteresProduits criteres = new CriteresProduits(null, Set.of(), Set.of(), null, null, false, promotion,
                Map.of(), tri, 1, proprietes.produitsParSection());
        FiltresSql filtres = new FiltresSql(criteres, null, null, Map.of());
        Map<String, Object> parametres = new HashMap<>(filtres.parametres());
        parametres.putAll(valeurs);
        parametres.put("limite", proprietes.produitsParSection());
        List<ResumesProduits.Selection> selections = new ArrayList<>();
        jdbc.sql("select p.id as produit_id, v.id as variante_id " + filtres.produitsAvecVarianteRetenue()
                        + (condition == null ? "" : "\n and " + condition)
                        + "\norder by " + tri.getOrdreSql() + ", p.id\nlimit :limite")
                .params(parametres)
                .query(ligne -> {
                    selections.add(new ResumesProduits.Selection(ligne.getLong("produit_id"),
                            ligne.getLong("variante_id")));
                });
        return resumesProduits.lire(selections);
    }

    private static void ajouter(List<Section> sections, String code, String titre, List<ProduitResumeReponse> produits) {
        if (!produits.isEmpty()) {
            sections.add(new Section(code, titre, produits));
        }
    }
}
