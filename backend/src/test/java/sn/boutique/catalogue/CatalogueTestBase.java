package sn.boutique.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.ValeurCaracteristique;
import sn.boutique.support.CompteurRequetesSql;
import sn.boutique.support.HorlogeReglable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Socle des tests de l'API publique du catalogue : vraie base, vrai Redis, horloge réglable.
 * <p>
 * La base est partagée par toutes les classes de test du même contexte : chaque test crée son propre
 * jeu de données avec des slugs uniques et ne vérifie que ses données.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, HorlogeReglable.Configuration.class})
abstract class CatalogueTestBase {

    private static final AtomicInteger COMPTEUR = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    HorlogeReglable horloge;
    @Autowired
    EntityManager em;
    @Autowired
    TransactionTemplate transaction;

    @BeforeEach
    void remettreLHorlogeALHeure() {
        horloge.reinitialiser();
    }

    static String unique(String prefixe) {
        return prefixe + "-" + System.nanoTime() % 1_000_000 + "-" + COMPTEUR.incrementAndGet();
    }

    <T> T enTransaction(Function<EntityManager, T> action) {
        return transaction.execute(statut -> action.apply(em));
    }

    Categorie categorie(String nom, Categorie parent, int position, boolean actif) {
        return enTransaction(em -> {
            Categorie categorie = new Categorie(nom, unique("cat"));
            categorie.setParent(parent == null ? null : em.getReference(Categorie.class, parent.getId()));
            categorie.setPosition(position);
            categorie.setActif(actif);
            em.persist(categorie);
            return categorie;
        });
    }

    Marque marque(String nom) {
        return enTransaction(em -> {
            Marque marque = new Marque(nom, unique("marque"));
            em.persist(marque);
            return marque;
        });
    }

    Produit produit(String nom, Marque marque, Categorie categorie, boolean actif) {
        return enTransaction(em -> {
            Produit produit = new Produit(nom, unique("produit"), em.getReference(Marque.class, marque.getId()),
                    em.getReference(Categorie.class, categorie.getId()));
            produit.setActif(actif);
            em.persist(produit);
            return produit;
        });
    }

    /**
     * Produit actif complété par {@code configuration} (variantes, médias…) avant son enregistrement.
     * Les valeurs de caractéristiques propres à une variante s'ajoutent ensuite avec {@link #caracteristiques}.
     */
    Produit produit(String nom, Marque marque, Categorie categorie, Consumer<Produit> configuration) {
        // L'horloge des tests est figée : on l'avance pour que l'ordre de création (tri « nouveauté ») soit net
        horloge.avancer(Duration.ofSeconds(1));
        Produit cree = enTransaction(em -> {
            Produit produit = new Produit(nom, unique("produit"), em.getReference(Marque.class, marque.getId()),
                    em.getReference(Categorie.class, categorie.getId()));
            configuration.accept(produit);
            em.persist(produit);
            return produit;
        });
        // Relu après le commit : prixMinimum et stockTotal sont calculés par le trigger (ADR 0004)
        return enTransaction(em -> em.find(Produit.class, cree.getId()));
    }

    /** Ajoute des valeurs de caractéristiques à un produit déjà enregistré (avec ses variantes). */
    void caracteristiques(Produit produit, BiConsumer<EntityManager, Produit> ajout) {
        enTransaction(em -> {
            Produit charge = em.find(Produit.class, produit.getId());
            ajout.accept(em, charge);
            return null;
        });
    }

    DefinitionCaracteristique definition(Categorie categorie, String cle, String libelle, TypeCaracteristique type,
                                         String groupe, int ordre, String unite, boolean filtrable) {
        return enTransaction(em -> {
            DefinitionCaracteristique definition = new DefinitionCaracteristique(
                    em.getReference(Categorie.class, categorie.getId()), cle, libelle, type, groupe);
            definition.setOrdre(ordre);
            definition.setUnite(unite);
            definition.setFiltrable(filtrable);
            em.persist(definition);
            return definition;
        });
    }

    /** Valeur d'une caractéristique pour le produit, ou pour une variante si {@code sku} est renseigné. */
    static void valeur(EntityManager em, Produit produit, DefinitionCaracteristique definition, String sku, Object valeur) {
        ValeurCaracteristique v = produit.ajouterCaracteristique(em.getReference(DefinitionCaracteristique.class,
                definition.getId()));
        if (sku != null) {
            v.setVariante(produit.getVariantes().stream().filter(var -> var.getSku().equals(sku)).findFirst()
                    .orElseThrow());
        }
        switch (valeur) {
            case Number nombre -> v.setValeurNombre(new BigDecimal(nombre.toString()));
            case Boolean booleen -> v.setValeurBooleen(booleen);
            default -> v.setValeurTexte(valeur.toString());
        }
    }

    ResultActions lire(String url, Object... variables) throws Exception {
        return mockMvc.perform(get(url, variables));
    }

    /** Exécute la requête en comptant les requêtes SQL envoyées à la base. */
    RequeteComptee lireEnComptant(String url, Object... variables) throws Exception {
        CompteurRequetesSql.reinitialiser();
        ResultActions resultat = lire(url, variables);
        return new RequeteComptee(resultat, CompteurRequetesSql.nombre());
    }

    record RequeteComptee(ResultActions resultat, int requetesSql) {
    }

    JsonNode corps(ResultActions resultat) throws Exception {
        return jsonMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }

    /** Nœud de l'arbre (ou d'une liste) dont le slug est donné, cherché récursivement dans les enfants. */
    static JsonNode noeud(JsonNode liste, String slug) {
        for (JsonNode element : liste) {
            if (slug.equals(element.path("slug").asString())) {
                return element;
            }
            JsonNode trouve = element.has("enfants") ? noeud(element.get("enfants"), slug) : null;
            if (trouve != null) {
                return trouve;
            }
        }
        return null;
    }
}
