package sn.boutique.catalogue.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.noyau.erreur.ParametreInvalideException;

/**
 * Caractéristiques filtrables d'une catégorie (filtres {@code f.<cle>}).
 * <p>
 * Les définitions sont portées par les catégories qui les déclarent (en général les feuilles). Pour une
 * catégorie parente (« Ordinateurs »), on propose les clés <strong>communes à toutes</strong> les catégories de
 * sa sous-arborescence qui déclarent des caractéristiques filtrables, et de même type partout.
 */
@Component
@RequiredArgsConstructor
class CaracteristiquesFiltrables {

    record Definition(String cle, String libelle, String unite, TypeCaracteristique type, String groupe) {
    }

    private record Ligne(long categorieId, Definition definition) {
    }

    private final JdbcClient jdbc;

    /** Définitions communes aux catégories données, dans l'ordre d'affichage (ordre, libellé). */
    List<Definition> communes(List<Long> categories) {
        List<Ligne> lignes = jdbc.sql("""
                        select d.categorie_id, d.cle, d.libelle, d.unite, d.type, d.groupe
                          from definition_caracteristique d
                         where d.filtrable and d.categorie_id in (:categories)
                         order by d.ordre, d.libelle, d.categorie_id
                        """)
                .param("categories", categories)
                .query((ligne, n) -> new Ligne(ligne.getLong("categorie_id"), new Definition(ligne.getString("cle"),
                        ligne.getString("libelle"), ligne.getString("unite"),
                        TypeCaracteristique.valueOf(ligne.getString("type")), ligne.getString("groupe"))))
                .list();
        Set<Long> categoriesAvecFiltres = lignes.stream().map(Ligne::categorieId).collect(Collectors.toSet());
        Map<String, List<Ligne>> parCle = new LinkedHashMap<>();
        lignes.forEach(ligne -> parCle.computeIfAbsent(ligne.definition().cle(), cle -> new ArrayList<>()).add(ligne));
        List<Definition> communes = new ArrayList<>();
        parCle.forEach((cle, definitions) -> {
            boolean partout = definitions.stream().map(Ligne::categorieId).collect(Collectors.toSet())
                    .equals(categoriesAvecFiltres);
            boolean memeType = definitions.stream().map(l -> l.definition().type()).distinct().count() == 1;
            if (partout && memeType) {
                communes.add(definitions.getFirst().definition());
            }
        });
        return communes;
    }

    /** Vérifie que chaque filtre {@code f.<cle>} existe pour la catégorie et que ses valeurs ont le bon type. */
    static void verifier(CriteresProduits criteres, List<Definition> definitions) {
        Map<String, Definition> parCle = definitions.stream()
                .collect(Collectors.toMap(Definition::cle, definition -> definition));
        criteres.caracteristiques().forEach((cle, valeurs) -> {
            Definition definition = parCle.get(cle);
            if (definition == null) {
                throw new ParametreInvalideException("f." + cle,
                        "Le filtre « %s » n'existe pas pour cette catégorie.".formatted(cle));
            }
            for (String valeur : valeurs) {
                boolean valide = switch (definition.type()) {
                    case NOMBRE -> valeur.matches("-?\\d+(\\.\\d+)?");
                    case BOOLEEN -> valeur.equals("true") || valeur.equals("false");
                    default -> true;
                };
                if (!valide) {
                    throw new ParametreInvalideException("f." + cle,
                            "Valeur « %s » invalide pour le filtre « %s ».".formatted(valeur, cle));
                }
            }
        });
    }
}
