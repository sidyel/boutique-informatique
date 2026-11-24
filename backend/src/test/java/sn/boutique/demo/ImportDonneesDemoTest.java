package sn.boutique.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.TestcontainersConfiguration;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.contenu.depot.ParametresSiteDepot;

/**
 * Le profil « demo » charge le catalogue au démarrage (ChargeurDonneesDemo) ; ces tests vérifient
 * le résultat, l'idempotence et la cohérence des données.
 */
@SpringBootTest(properties = "boutique.demo.admin-mot-de-passe=MotDePasseDemo-2026")
@ActiveProfiles({"dev", "demo"})
@Import(TestcontainersConfiguration.class)
class ImportDonneesDemoTest {

    private static final Path IMAGES_FRONTEND = Path.of("..", "frontend", "public");

    @Autowired
    private ImportDonneesDemo importDonneesDemo;

    @Autowired
    private ProduitDepot produitDepot;

    @Autowired
    private UtilisateurDepot utilisateurDepot;

    @Autowired
    private ParametresSiteDepot parametresDepot;

    @Autowired
    private PasswordEncoder encodeur;

    @Autowired
    private EntityManager em;

    @Test
    void unSecondChargementNeCreeRien() {
        long produitsAvant = produitDepot.count();

        ImportDonneesDemo.Bilan bilan = importDonneesDemo.charger();

        assertThat(bilan.rienCree()).as("bilan %s", bilan).isTrue();
        assertThat(produitDepot.count()).isEqualTo(produitsAvant);
    }

    @Test
    @Transactional
    void chaqueProduitEstCompletEtSesAgregatsSontCalcules() {
        List<Produit> produits = produitDepot.findAll();

        assertThat(produits).hasSizeGreaterThanOrEqualTo(30);
        assertThat(produits).allSatisfy(produit -> {
            assertThat(produit.getVariantes()).as(produit.getSlug()).isNotEmpty();
            assertThat(produit.getMedias()).as(produit.getSlug())
                    .filteredOn(MediaProduit::isPrincipal).hasSize(1);
            assertThat(produit.getPrixMinimum()).as(produit.getSlug()).isPositive();
            assertThat(produit.getCaracteristiques()).as(produit.getSlug()).isNotEmpty();
        });
        // Au moins un produit en rupture, un reconditionné et un prix barré, pour les écrans à tester
        assertThat(produits).anySatisfy(produit -> assertThat(produit.getStockTotal()).isZero());
        assertThat(produits).anySatisfy(produit -> assertThat(produit.getEtat().name()).isEqualTo("RECONDITIONNE"));
        assertThat(produits).flatExtracting(Produit::getVariantes)
                .anySatisfy(variante -> assertThat(variante.getPrixBarre()).isNotNull());
    }

    @Test
    void laRechercheFonctionneSurLeCatalogueDeDemonstration() {
        assertThat(produitDepot.rechercher("asus portable", 20)).extracting(Produit::getSlug)
                .contains("asus-vivobook-15-x1504va", "asus-zenbook-14-oled-ux3405ma")
                .doesNotContain("asus-tuf-gaming-a15-fa507nv".replace("asus-", "lenovo-"));
        assertThat(produitDepot.rechercher("onduleur", 20)).extracting(Produit::getSlug)
                .contains("apc-back-ups-700va-bx700ui", "apc-back-ups-1200va-bx1200mi");
        // Caractéristique clé (processeur) et unité
        assertThat(produitDepot.rechercher("ryzen 7", 20)).extracting(Produit::getSlug)
                .contains("asus-tuf-gaming-a15-fa507nv", "amd-ryzen-7-7800x3d");
        assertThat(produitDepot.rechercher("ecran 27 pouces", 20)).extracting(Produit::getSlug)
                .contains("lg-ultragear-27gs60f");
    }

    @Test
    void leCompteAdministrateurEstCreeAvecUnMotDePasseHache() {
        Utilisateur admin = utilisateurDepot.trouverParEmail("admin@exemple.sn").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(admin.isEmailVerifie()).isTrue();
        assertThat(admin.getMotDePasseHash()).startsWith("$2a$12$").doesNotContain("MotDePasseDemo");
        assertThat(encodeur.matches("MotDePasseDemo-2026", admin.getMotDePasseHash())).isTrue();
    }

    @Test
    void lesParametresDuSiteSontRenseignes() {
        assertThat(parametresDepot.lire().getWhatsapp()).isEqualTo("+221770000000");
    }

    /** Toute URL d'image enregistrée correspond à un fichier SVG servi par le frontend. */
    @Test
    @SuppressWarnings("unchecked")
    void toutesLesImagesReferenceesExistent() throws Exception {
        assumeThat(Files.isDirectory(IMAGES_FRONTEND)).as("dossier frontend/public présent").isTrue();
        List<String> urls = new ArrayList<>();
        Stream.of(
                "select url from media_produit",
                "select image_url from categorie where image_url is not null",
                "select image_ordinateur_url from banniere",
                "select image_mobile_url from banniere",
                "select image_couverture from article where image_couverture is not null")
                .forEach(requete -> urls.addAll(em.createNativeQuery(requete).getResultList()));

        assertThat(urls).isNotEmpty().allSatisfy(url -> {
            assertThat(url).startsWith(ImportDonneesDemo.DOSSIER_IMAGES).endsWith(".svg");
            assertThat(IMAGES_FRONTEND.resolve(url.substring(1))).as(url).isRegularFile();
        });
    }
}
