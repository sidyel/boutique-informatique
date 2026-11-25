package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.EmplacementBanniere;
import tools.jackson.databind.JsonNode;

/** Cache Redis des lectures publiques : zéro requête SQL au second appel, invalidation, panne du cache. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheCatalogueTest extends CatalogueTestBase {

    @Autowired
    ApplicationEventPublisher evenements;
    @Autowired
    GenericContainer<?> conteneurRedisCache;

    private Categorie categorie;
    private Marque marque;
    private Produit produit;
    private String sku;
    private List<String> routes;

    @BeforeAll
    void creerLeCatalogue() {
        categorie = categorie("Cartes graphiques", null, 0, true);
        DefinitionCaracteristique memoire = definition(categorie, "vram_go", "Mémoire vidéo",
                TypeCaracteristique.NOMBRE, "Mémoire", 1, "Go", true);
        marque = marque("Gigavolt");
        sku = unique("GV-4060");
        produit = produit("Gigavolt RTX 4060 Eagle", marque, categorie, p -> {
            p.ajouterVariante(sku, "8 Go", 245_000, 6);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/rtx.jpg", "Carte graphique Gigavolt");
        });
        produit("Gigavolt RTX 4070 Windforce", marque, categorie, p -> {
            p.ajouterVariante(unique("GV-4070"), "12 Go", 420_000, 2);
            p.ajouterMedia(TypeMedia.IMAGE, "https://exemple.sn/rtx2.jpg", "Carte graphique Gigavolt");
        });
        caracteristiques(produit, (em, p) -> valeur(em, p, memoire, null, 8));
        enTransaction(em -> {
            Banniere banniere = new Banniere(EmplacementBanniere.ENTETE_CATEGORIE, "https://exemple.sn/o.jpg",
                    "https://exemple.sn/m.jpg", "Cartes graphiques en promotion");
            banniere.setCategorie(em.getReference(Categorie.class, categorie.getId()));
            banniere.setDateDebut(horloge.instant().plus(Duration.ofHours(1)));
            em.persist(banniere);
            return banniere;
        });
        routes = List.of(
                "/api/categories",
                "/api/categories/" + categorie.getSlug(),
                "/api/categories/" + categorie.getSlug() + "/filtres?f.vram_go=8",
                "/api/marques",
                "/api/marques/" + marque.getSlug(),
                "/api/parametres",
                "/api/bannieres?emplacement=ENTETE_CATEGORIE&categorie=" + categorie.getSlug(),
                "/api/produits/" + produit.getSlug(),
                "/api/produits/" + produit.getSlug() + "/similaires",
                "/api/produits?categorie=" + categorie.getSlug() + "&tri=prix-croissant",
                "/api/produits?categorie=" + categorie.getSlug() + "&marque=" + marque.getSlug() + "&f.vram_go=8");
    }

    @Test
    void unSecondAppelNeCouteAucuneRequeteSqlEtRendLaMemeReponse() throws Exception {
        for (String route : routes) {
            String premiere = lire(route).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            RequeteComptee seconde = lireEnComptant(route);

            seconde.resultat().andExpect(status().isOk());
            assertThat(seconde.requetesSql()).as("requêtes SQL au second appel de %s", route).isZero();
            assertThat(seconde.resultat().andReturn().getResponse().getContentAsString()).as(route).isEqualTo(premiere);
        }
    }

    @Test
    void lesMemesCriteresDansUnAutreOrdreUtilisentLaMemeEntree() throws Exception {
        lire("/api/produits?marque={m}&categorie={c}&f.vram_go=8.0", marque.getSlug(), categorie.getSlug());

        assertThat(lireEnComptant("/api/produits?f.vram_go=8&categorie={c}&marque={m}", categorie.getSlug(),
                marque.getSlug()).requetesSql()).isZero();
    }

    @Test
    void laPeriodeDesBannieresEstVerifieeApresLeCache() throws Exception {
        String url = "/api/bannieres?emplacement=ENTETE_CATEGORIE&categorie=" + categorie.getSlug();
        assertThat(corps(lire(url)).size()).isZero();

        horloge.avancer(Duration.ofHours(2));
        RequeteComptee apres = lireEnComptant(url);

        assertThat(corps(apres.resultat()).size()).isEqualTo(1);
        assertThat(apres.requetesSql()).as("liste lue dans le cache").isZero();
    }

    @Test
    void uneModificationValideeVideLesCachesEtUneModificationAnnuleeNon() throws Exception {
        String fiche = "/api/produits/" + produit.getSlug();
        long prix = corps(lire(fiche)).get("variantes").get(0).get("prix").asLong();
        lire("/api/produits?categorie={c}&tri=prix-croissant", categorie.getSlug());

        // Annulée : ni la base ni le cache ne changent
        transaction.executeWithoutResult(statut -> {
            changerLePrix(prix - 6_000);
            statut.setRollbackOnly();
        });
        assertThat(lireEnComptant(fiche).requetesSql()).isZero();

        // Validée : le cache est vidé après la validation, la fiche et la liste montrent le nouveau prix
        transaction.executeWithoutResult(statut -> changerLePrix(prix - 16_000));
        RequeteComptee apres = lireEnComptant(fiche);
        assertThat(apres.requetesSql()).isPositive();
        assertThat(corps(apres.resultat()).get("variantes").get(0).get("prix").asLong()).isEqualTo(prix - 16_000);
        assertThat(corps(lire("/api/produits?categorie={c}&tri=prix-croissant", categorie.getSlug()))
                .get("elements").get(0).get("prix").asLong()).isEqualTo(prix - 16_000);
    }

    @Test
    void sansTransactionLeVidageEstImmediat() throws Exception {
        lire("/api/marques");
        evenements.publishEvent(new CatalogueModifie("test hors transaction"));

        assertThat(lireEnComptant("/api/marques").requetesSql()).isPositive();
    }

    @Test
    void cacheArreteLesRoutesDuCatalogueRepondentToujours() throws Exception {
        DockerClient docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(conteneurRedisCache.getContainerId()).exec();
        try {
            for (String route : routes) {
                lire(route).andExpect(status().isOk());
            }
            JsonNode liste = corps(lire("/api/produits?categorie={c}&tri=prix-croissant", categorie.getSlug()));
            assertThat(liste.get("totalElements").asLong()).isEqualTo(2);
            // Une écriture reste possible : l'échec du vidage est journalisé, pas propagé
            transaction.executeWithoutResult(statut -> changerLePrix(240_000));
        } finally {
            docker.unpauseContainerCmd(conteneurRedisCache.getContainerId()).exec();
        }
        // Cache revenu : il resert dès la deuxième lecture
        lire("/api/marques");
        lire("/api/marques");
        assertThat(lireEnComptant("/api/marques").requetesSql()).isZero();
    }

    private void changerLePrix(long prix) {
        VarianteProduit variante = em.createQuery("select v from VarianteProduit v where v.sku = :sku",
                VarianteProduit.class).setParameter("sku", sku).getSingleResult();
        variante.setPrix(prix);
        evenements.publishEvent(new CatalogueModifie("variante " + variante.getId()));
    }
}
