package sn.boutique.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.concurrent.atomic.AtomicInteger;
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
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
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
