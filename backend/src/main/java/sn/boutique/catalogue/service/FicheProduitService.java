package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.MediaProduitDepot;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.depot.ValeurCaracteristiqueDepot;
import sn.boutique.catalogue.depot.VarianteProduitDepot;
import sn.boutique.catalogue.dto.Disponibilite;
import sn.boutique.catalogue.dto.FicheProduitReponse;
import sn.boutique.catalogue.dto.FicheProduitReponse.Caracteristique;
import sn.boutique.catalogue.dto.FicheProduitReponse.GroupeCaracteristiques;
import sn.boutique.catalogue.dto.FicheProduitReponse.Media;
import sn.boutique.catalogue.dto.FicheProduitReponse.ValeurVariante;
import sn.boutique.catalogue.dto.FicheProduitReponse.Variante;
import sn.boutique.catalogue.dto.LienReponse;
import sn.boutique.catalogue.dto.ProduitResumeReponse;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.ValeurCaracteristique;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.media.LienYoutube;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Fiche produit publique et produits similaires.
 * <p>
 * La fiche est lue en cinq requêtes : produit (avec marque et catégorie), arbre des catégories (fil
 * d'Ariane), variantes, médias, caractéristiques. Les collections sont lues séparément : Hibernate ne
 * peut pas charger plusieurs listes dans une même jointure, et des jointures multiples
 * multiplieraient les lignes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FicheProduitService {

    private static final int NOMBRE_SIMILAIRES = 8;

    private final ProduitDepot produitDepot;
    private final VarianteProduitDepot varianteDepot;
    private final MediaProduitDepot mediaDepot;
    private final ValeurCaracteristiqueDepot valeurDepot;
    private final CategorieService categorieService;
    private final ResumesProduits resumesProduits;
    private final JdbcClient jdbc;

    @Cacheable(cacheNames = CachesPublics.FICHE_PRODUIT, key = "#slug")
    public FicheProduitReponse trouverParSlug(String slug) {
        Produit produit = produitDepot.trouverPublieParSlug(slug)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", slug));
        List<VarianteProduit> variantes = varianteDepot.findByProduitIdAndActifTrueOrderByPrixAscIdAsc(produit.getId());
        Set<Long> variantesActives = variantes.stream().map(VarianteProduit::getId).collect(Collectors.toSet());
        List<ValeurCaracteristique> valeurs = valeurDepot.trouverParProduit(produit.getId());

        Map<Long, List<ValeurVariante>> valeursParVariante = new HashMap<>();
        for (ValeurCaracteristique valeur : valeurs) {
            if (valeur.getVariante() != null && variantesActives.contains(valeur.getVariante().getId())) {
                DefinitionCaracteristique definition = valeur.getDefinition();
                valeursParVariante.computeIfAbsent(valeur.getVariante().getId(), id -> new ArrayList<>())
                        .add(new ValeurVariante(definition.getCle(), MiseEnFormeCatalogue.valeurAffichee(
                                definition.getType(), MiseEnFormeCatalogue.valeurBrute(valeur)), definition.getUnite()));
            }
        }

        Disponibilite disponibilite = Disponibilite.RUPTURE;
        List<Variante> reponsesVariantes = new ArrayList<>();
        for (VarianteProduit variante : variantes) {
            Disponibilite dispo = Disponibilite.depuisStock(variante.getStock(), variante.getSeuilAlerte());
            disponibilite = disponibilite.meilleure(dispo);
            reponsesVariantes.add(new Variante(variante.getId(), variante.getSku(), variante.getLibelle(),
                    Map.copyOf(variante.getAttributs()), variante.getPrix(), variante.getPrixBarre(),
                    MiseEnFormeCatalogue.reduction(variante.getPrix(), variante.getPrixBarre()), dispo,
                    variante.getPoidsGrammes(), valeursParVariante.getOrDefault(variante.getId(), List.of())));
        }

        List<LienReponse> filAriane = new ArrayList<>();
        LienReponse categorie = new LienReponse(produit.getCategorie().getNom(), produit.getCategorie().getSlug());
        categorieService.chargerArbre().trouver(categorie.slug())
                .ifPresent(noeud -> filAriane.addAll(noeud.filAriane()));
        filAriane.add(categorie);

        return new FicheProduitReponse(produit.getId(), produit.getSlug(), produit.getNom(),
                produit.getDescriptionCourte(), produit.getDescriptionLongue(), produit.getEtat(),
                produit.getGarantieMois(), produit.getGtin(), produit.getMpn(), produit.getMetaTitre(),
                produit.getMetaDescription(),
                new FicheProduitReponse.Marque(produit.getMarque().getNom(), produit.getMarque().getSlug(),
                        produit.getMarque().getLogoUrl()),
                categorie, filAriane, produit.getPrixMinimum(), disponibilite, produit.getNoteMoyenne(),
                produit.getNombreAvis(), reponsesVariantes, medias(produit.getId(), variantesActives),
                tableau(valeurs, variantes), produit.getDateModification());
    }

    /**
     * Produits de la même catégorie : ceux en stock d'abord, puis les prix les plus proches.
     * La carte montre la variante active la moins chère de chaque produit.
     */
    @Cacheable(cacheNames = CachesPublics.PRODUITS_SIMILAIRES, key = "#slug")
    public List<ProduitResumeReponse> similaires(String slug) {
        Long produitId = jdbc.sql("select id from produit where slug = :slug and actif")
                .param("slug", slug).query(Long.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Produit", slug));
        List<ResumesProduits.Selection> selections = jdbc.sql("""
                        select p.id as produit_id, v.id as variante_id
                          from produit reference
                          join produit p on p.categorie_id = reference.categorie_id and p.id <> reference.id and p.actif
                          cross join lateral (select vv.id from variante_produit vv
                                               where vv.produit_id = p.id and vv.actif
                                               order by vv.prix, vv.id limit 1) v
                         where reference.id = :id
                         order by (p.stock_total > 0) desc,
                                  abs(coalesce(p.prix_minimum, 0) - coalesce(reference.prix_minimum, 0)), p.id
                         limit :limite
                        """)
                .param("id", produitId)
                .param("limite", NOMBRE_SIMILAIRES)
                .query((ligne, n) -> new ResumesProduits.Selection(ligne.getLong("produit_id"), ligne.getLong("variante_id")))
                .list();
        return resumesProduits.lire(selections);
    }

    private List<Media> medias(Long produitId, Set<Long> variantesActives) {
        return mediaDepot.findByProduitIdOrderByPositionAsc(produitId).stream()
                // Les photos d'une variante désactivée ne sont plus montrées
                .filter(media -> media.getVariante() == null || variantesActives.contains(media.getVariante().getId()))
                .map(media -> new Media(media.getId(), media.getType(), media.getUrl(), media.getUrlMiniature(),
                        media.getTexteAlternatif(), media.isPrincipal(),
                        media.getVariante() == null ? null : media.getVariante().getId(), idYoutube(media)))
                .toList();
    }

    private static String idYoutube(MediaProduit media) {
        return media.getType() == TypeMedia.VIDEO_YOUTUBE
                ? LienYoutube.extraireIdentifiant(media.getUrl()).orElse(null)
                : null;
    }

    /**
     * Tableau de caractéristiques par groupe, dans l'ordre des définitions. Une caractéristique propre aux
     * variantes affiche la valeur de la première variante (la moins chère) : le site la remplace quand
     * le client choisit une autre variante.
     */
    private static List<GroupeCaracteristiques> tableau(List<ValeurCaracteristique> valeurs,
                                                         List<VarianteProduit> variantes) {
        Long premiereVariante = variantes.isEmpty() ? null : variantes.getFirst().getId();
        Map<DefinitionCaracteristique, Caracteristique> elements = new HashMap<>();
        for (ValeurCaracteristique valeur : valeurs) {
            DefinitionCaracteristique definition = valeur.getDefinition();
            boolean parVariante = valeur.getVariante() != null;
            if (parVariante && !valeur.getVariante().getId().equals(premiereVariante)) {
                continue;
            }
            elements.put(definition, new Caracteristique(definition.getCle(), definition.getLibelle(),
                    MiseEnFormeCatalogue.valeurAffichee(definition.getType(), MiseEnFormeCatalogue.valeurBrute(valeur)),
                    definition.getUnite(), parVariante));
        }
        Map<String, List<Caracteristique>> groupes = new LinkedHashMap<>();
        elements.keySet().stream()
                .sorted(Comparator.comparingInt(DefinitionCaracteristique::getOrdre)
                        .thenComparing(DefinitionCaracteristique::getLibelle))
                .forEach(definition -> groupes.computeIfAbsent(definition.getGroupe(), g -> new ArrayList<>())
                        .add(elements.get(definition)));
        return groupes.entrySet().stream()
                .map(groupe -> new GroupeCaracteristiques(groupe.getKey(), List.copyOf(groupe.getValue())))
                .toList();
    }
}
