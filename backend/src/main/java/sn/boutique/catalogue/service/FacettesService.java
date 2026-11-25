package sn.boutique.catalogue.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.dto.FiltresReponse;
import sn.boutique.catalogue.dto.FiltresReponse.Option;
import sn.boutique.catalogue.modele.EtatProduit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.service.CriteresProduits.Famille;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.erreur.ParametreInvalideException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Filtres d'une catégorie avec leurs décomptes (facettes « à sélection multiple »).
 * <p>
 * Coût : arbre des catégories, définitions filtrables, une requête pour toutes les facettes avec tous les
 * filtres, puis <strong>une requête par famille de filtres active</strong>, calculée sans son propre filtre.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacettesService {

    /** Parties de la requête des facettes, calculées sur les variantes qui conviennent (CTE {@code eligibles}). */
    private enum Partie { TOTAL, MARQUE, ETAT, CATEGORIE, DISPONIBLE, PRIX, CARACTERISTIQUE }

    private record Ligne(Partie partie, String cle, String valeur, long nombre) {
    }

    private static final Map<EtatProduit, String> LIBELLES_ETATS = Map.of(
            EtatProduit.NEUF, "Neuf", EtatProduit.RECONDITIONNE, "Reconditionné", EtatProduit.OCCASION, "Occasion");

    private final JdbcClient jdbc;
    private final CategorieService categorieService;
    private final CaracteristiquesFiltrables caracteristiquesFiltrables;
    private final MarqueService marqueService;

    @Cacheable(cacheNames = CachesPublics.FILTRES, key = "#slugCategorie + '?' + #demandes.cleCache(false)")
    public FiltresReponse filtres(String slugCategorie, CriteresProduits demandes) {
        CriteresProduits criteres = demandes.avecCategorie(slugCategorie);
        ArbreCategories.Noeud noeud = categorieService.chargerArbre().trouver(slugCategorie)
                .orElseThrow(() -> new RessourceIntrouvableException("Catégorie", slugCategorie));
        List<Long> categories = noeud.idsSousArbre();
        List<CaracteristiquesFiltrables.Definition> definitions = caracteristiquesFiltrables.communes(categories);
        CaracteristiquesFiltrables.verifier(criteres, definitions);
        List<String> cles = definitions.stream().map(CaracteristiquesFiltrables.Definition::cle).toList();

        // Toutes les facettes avec tous les filtres…
        List<Ligne> lignes = new ArrayList<>(executer(criteres, categories, EnumSet.allOf(Partie.class), cles));
        // … puis chaque famille active, recalculée sans son propre filtre
        if (!criteres.marques().isEmpty()) {
            remplacer(lignes, Partie.MARQUE, null,
                    executer(criteres.sans(Famille.MARQUE, null), categories, EnumSet.of(Partie.MARQUE), cles));
        }
        if (!criteres.etats().isEmpty()) {
            remplacer(lignes, Partie.ETAT, null,
                    executer(criteres.sans(Famille.ETAT, null), categories, EnumSet.of(Partie.ETAT), cles));
        }
        if (criteres.prixMin() != null || criteres.prixMax() != null) {
            remplacer(lignes, Partie.PRIX, null,
                    executer(criteres.sans(Famille.PRIX, null), categories, EnumSet.of(Partie.PRIX), cles));
        }
        if (criteres.disponible()) {
            remplacer(lignes, Partie.DISPONIBLE, null,
                    executer(criteres.sans(Famille.DISPONIBLE, null), categories, EnumSet.of(Partie.DISPONIBLE), cles));
        }
        for (String cle : criteres.caracteristiques().keySet()) {
            remplacer(lignes, Partie.CARACTERISTIQUE, cle, executer(criteres.sans(Famille.CARACTERISTIQUE, cle),
                    categories, EnumSet.of(Partie.CARACTERISTIQUE), List.of(cle)));
        }
        return assembler(lignes, criteres, noeud, definitions);
    }

    /**
     * Filtres de la page marque : catégories, état, prix et disponibilité, avec leurs décomptes. Les
     * caractéristiques dépendent de la catégorie : elles ne sont pas proposées (liste vide) et un filtre
     * {@code f.cle} est refusé. Le paramètre {@code categorie} limite à cette catégorie et à ses descendants ;
     * la facette des catégories, elle, l'ignore (chaque facette ignore son propre filtre).
     */
    @Cacheable(cacheNames = CachesPublics.FILTRES, key = "'marque:' + #slugMarque + '?' + #demandes.cleCache(false)")
    public FiltresReponse filtresMarque(String slugMarque, CriteresProduits demandes) {
        marqueService.trouverParSlug(slugMarque);
        if (!demandes.caracteristiques().isEmpty()) {
            throw new ParametreInvalideException("f", "Les filtres de caractéristiques demandent une catégorie.");
        }
        CriteresProduits criteres = demandes.avecMarque(slugMarque);
        ArbreCategories arbre = categorieService.chargerArbre();
        List<Long> categories = null;
        if (criteres.categorie() != null) {
            categories = arbre.trouver(criteres.categorie())
                    .orElseThrow(() -> new RessourceIntrouvableException("Catégorie", criteres.categorie()))
                    .idsSousArbre();
        }
        Set<Partie> parties = EnumSet.of(Partie.TOTAL, Partie.ETAT, Partie.CATEGORIE, Partie.DISPONIBLE, Partie.PRIX);
        List<Ligne> lignes = new ArrayList<>(executer(criteres, categories, parties, List.of()));
        if (categories != null) {
            remplacer(lignes, Partie.CATEGORIE, null,
                    executer(criteres, null, EnumSet.of(Partie.CATEGORIE), List.of()));
        }
        if (!criteres.etats().isEmpty()) {
            remplacer(lignes, Partie.ETAT, null,
                    executer(criteres.sans(Famille.ETAT, null), categories, EnumSet.of(Partie.ETAT), List.of()));
        }
        if (criteres.prixMin() != null || criteres.prixMax() != null) {
            remplacer(lignes, Partie.PRIX, null,
                    executer(criteres.sans(Famille.PRIX, null), categories, EnumSet.of(Partie.PRIX), List.of()));
        }
        if (criteres.disponible()) {
            remplacer(lignes, Partie.DISPONIBLE, null, executer(criteres.sans(Famille.DISPONIBLE, null),
                    categories, EnumSet.of(Partie.DISPONIBLE), List.of()));
        }

        long total = 0;
        long enStock = 0;
        FiltresReponse.Prix prix = null;
        Map<String, Long> etats = new HashMap<>();
        List<Option> optionsCategories = new ArrayList<>();
        for (Ligne ligne : lignes) {
            switch (ligne.partie()) {
                case TOTAL -> total = ligne.nombre();
                case DISPONIBLE -> enStock = ligne.nombre();
                case PRIX -> prix = ligne.cle() == null ? null
                        : new FiltresReponse.Prix(Long.valueOf(ligne.cle()), Long.valueOf(ligne.valeur()));
                case ETAT -> etats.put(ligne.cle(), ligne.nombre());
                // Catégories où sont rangés les produits de la marque (une catégorie masquée n'est pas proposée)
                case CATEGORIE -> arbre.trouverParId(Long.parseLong(ligne.cle())).ifPresent(noeud ->
                        optionsCategories.add(new Option(noeud.slug(), noeud.nom(), ligne.nombre(),
                                noeud.slug().equals(criteres.categorie()))));
                default -> {
                }
            }
        }
        // Une catégorie choisie reste affichée (cochée) même si les produits sont rangés dans ses descendants
        if (criteres.categorie() != null
                && optionsCategories.stream().noneMatch(option -> option.valeur().equals(criteres.categorie()))) {
            ArbreCategories.Noeud choisie = arbre.trouver(criteres.categorie()).orElseThrow();
            optionsCategories.add(new Option(choisie.slug(), choisie.nom(), total, true));
        }
        optionsCategories.sort(Comparator.comparingLong(Option::nombre).reversed().thenComparing(Option::libelle));
        return new FiltresReponse(total, prix, optionsCategories, List.of(), optionsEtats(etats, criteres),
                new FiltresReponse.Disponible(enStock, criteres.disponible()), List.of());
    }

    private List<Ligne> executer(CriteresProduits criteres, List<Long> categories, Set<Partie> parties,
                                 List<String> cles) {
        FiltresSql filtres = new FiltresSql(criteres, categories, null, Map.of());
        List<String> requetes = new ArrayList<>();
        for (Partie partie : parties) {
            String requete = switch (partie) {
                case TOTAL -> "select 'TOTAL', null::text, null::text, count(distinct e.produit_id) from eligibles e";
                case MARQUE -> """
                        select 'MARQUE', m.slug, m.nom, count(distinct e.produit_id)
                          from eligibles e join marque m on m.id = e.marque_id group by m.slug, m.nom""";
                case ETAT -> "select 'ETAT', e.etat, null, count(distinct e.produit_id) from eligibles e group by e.etat";
                case CATEGORIE -> """
                        select 'CATEGORIE', e.categorie_id::text, null, count(distinct e.produit_id)
                          from eligibles e group by e.categorie_id""";
                case DISPONIBLE -> "select 'DISPONIBLE', null, null, count(distinct e.produit_id) from eligibles e where e.stock > 0";
                case PRIX -> "select 'PRIX', min(e.prix)::text, max(e.prix)::text, count(*) from eligibles e";
                case CARACTERISTIQUE -> cles.isEmpty() ? null : """
                        select 'CARACTERISTIQUE', d.cle, %s, count(distinct e.produit_id)
                          from eligibles e
                          join produit p on p.id = e.produit_id
                          join variante_produit v on v.id = e.variante_id
                          join valeur_caracteristique vc on vc.produit_id = e.produit_id
                          join definition_caracteristique d on d.id = vc.definition_id
                         where d.cle in (:clesFacettes) and %s
                         group by d.cle, %s""".formatted(FiltresSql.VALEUR_BRUTE, FiltresSql.VALEUR_APPLICABLE,
                        FiltresSql.VALEUR_BRUTE);
            };
            if (requete != null) {
                requetes.add(requete);
            }
        }
        Map<String, Object> parametres = new HashMap<>(filtres.parametres());
        parametres.put("clesFacettes", cles.isEmpty() ? List.of("") : cles);
        String sql = "with eligibles as (select p.id as produit_id, v.id as variante_id, v.prix, v.stock, "
                + "p.marque_id, p.etat, p.categorie_id " + filtres.variantesQuiConviennent() + ")\n"
                + String.join("\nunion all\n", requetes);
        return jdbc.sql(sql).params(parametres)
                .query((ligne, n) -> new Ligne(Partie.valueOf(ligne.getString(1)), ligne.getString(2),
                        ligne.getString(3), ligne.getLong(4)))
                .list();
    }

    private static void remplacer(List<Ligne> lignes, Partie partie, String cle, List<Ligne> nouvelles) {
        lignes.removeIf(ligne -> ligne.partie() == partie && (cle == null || cle.equals(ligne.cle())));
        lignes.addAll(nouvelles);
    }

    private static FiltresReponse assembler(List<Ligne> lignes, CriteresProduits criteres, ArbreCategories.Noeud noeud,
                                            List<CaracteristiquesFiltrables.Definition> definitions) {
        long total = 0;
        long enStock = 0;
        FiltresReponse.Prix prix = null;
        List<Option> marques = new ArrayList<>();
        Map<String, Long> etats = new HashMap<>();
        Map<Long, Long> parCategorie = new HashMap<>();
        Map<String, Map<String, Long>> parCaracteristique = new HashMap<>();
        for (Ligne ligne : lignes) {
            switch (ligne.partie()) {
                case TOTAL -> total = ligne.nombre();
                case DISPONIBLE -> enStock = ligne.nombre();
                case PRIX -> prix = ligne.cle() == null ? null
                        : new FiltresReponse.Prix(Long.valueOf(ligne.cle()), Long.valueOf(ligne.valeur()));
                case MARQUE -> marques.add(new Option(ligne.cle(), ligne.valeur(), ligne.nombre(),
                        criteres.marques().contains(ligne.cle())));
                case ETAT -> etats.put(ligne.cle(), ligne.nombre());
                case CATEGORIE -> parCategorie.put(Long.valueOf(ligne.cle()), ligne.nombre());
                case CARACTERISTIQUE -> parCaracteristique.computeIfAbsent(ligne.cle(), c -> new HashMap<>())
                        .put(ligne.valeur(), ligne.nombre());
            }
        }
        // Une marque cochée reste affichée même sans produit avec les autres filtres
        for (String slug : criteres.marques()) {
            if (marques.stream().noneMatch(option -> option.valeur().equals(slug))) {
                marques.add(new Option(slug, slug, 0, true));
            }
        }
        marques.sort(Comparator.comparingLong(Option::nombre).reversed().thenComparing(Option::libelle));

        List<Option> optionsEtats = optionsEtats(etats, criteres);

        List<Option> sousCategories = new ArrayList<>();
        for (ArbreCategories.Noeud enfant : noeud.enfants()) {
            long nombre = enfant.idsSousArbre().stream().mapToLong(id -> parCategorie.getOrDefault(id, 0L)).sum();
            if (nombre > 0) {
                sousCategories.add(new Option(enfant.slug(), enfant.nom(), nombre, false));
            }
        }

        List<FiltresReponse.Caracteristique> caracteristiques = new ArrayList<>();
        for (CaracteristiquesFiltrables.Definition definition : definitions) {
            Map<String, Long> comptes = new LinkedHashMap<>(parCaracteristique.getOrDefault(definition.cle(), Map.of()));
            Set<String> selection = criteres.caracteristiques().getOrDefault(definition.cle(), Set.of());
            selection.forEach(valeur -> comptes.putIfAbsent(valeur, 0L));
            if (comptes.isEmpty()) {
                continue;
            }
            List<Option> valeurs = comptes.entrySet().stream()
                    .sorted(ordreValeurs(definition.type()))
                    .map(entree -> new Option(entree.getKey(), libelle(definition, entree.getKey()), entree.getValue(),
                            selection.contains(entree.getKey())))
                    .toList();
            caracteristiques.add(new FiltresReponse.Caracteristique(definition.cle(), definition.libelle(),
                    definition.unite(), definition.type(), definition.groupe(), valeurs));
        }
        return new FiltresReponse(total, prix, sousCategories, marques, optionsEtats,
                new FiltresReponse.Disponible(enStock, criteres.disponible()), caracteristiques);
    }

    private static List<Option> optionsEtats(Map<String, Long> etats, CriteresProduits criteres) {
        List<Option> options = new ArrayList<>();
        for (EtatProduit etat : EtatProduit.values()) {
            long nombre = etats.getOrDefault(etat.name(), 0L);
            if (nombre > 0 || criteres.etats().contains(etat)) {
                options.add(new Option(etat.name(), LIBELLES_ETATS.get(etat), nombre, criteres.etats().contains(etat)));
            }
        }
        return options;
    }

    private static Comparator<Map.Entry<String, Long>> ordreValeurs(TypeCaracteristique type) {
        return switch (type) {
            case NOMBRE -> Comparator.comparing(entree -> new BigDecimal(entree.getKey()));
            case BOOLEEN -> Comparator.comparing(entree -> !Boolean.parseBoolean(entree.getKey()));
            default -> Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private static String libelle(CaracteristiquesFiltrables.Definition definition, String valeur) {
        String affichee = MiseEnFormeCatalogue.valeurAffichee(definition.type(), valeur);
        return definition.unite() == null || definition.type() != TypeCaracteristique.NOMBRE
                ? affichee
                : affichee + " " + definition.unite();
    }
}
