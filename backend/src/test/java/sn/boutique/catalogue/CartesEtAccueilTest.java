package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.service.AccueilService;
import sn.boutique.catalogue.service.ProprietesAccueil;
import sn.boutique.contenu.modele.ElementConfiance;
import sn.boutique.contenu.modele.ElementConfiance.IconeConfiance;
import sn.boutique.contenu.modele.ParametresSite;
import tools.jackson.databind.JsonNode;

/**
 * Phase 3a : caractéristiques clés des cartes, filtre {@code promotion}, sections de l'accueil et promesses
 * commerciales des paramètres publics.
 */
class CartesEtAccueilTest extends CatalogueTestBase {

    private static final String INSECABLE = " ";

    @Autowired
    ProprietesAccueil proprietesAccueil;

    // --- Caractéristiques clés de la carte ------------------------------------------------------------

    @Test
    void laCarteAfficheAuPlusQuatreCaracteristiquesAvecLaValeurDeLaVarianteRetenue() throws Exception {
        Categorie categorie = categorie("Portables", null, 0, true);
        Marque marque = marque("Asus");
        DefinitionCaracteristique cpu = carte(definition(categorie, "cpu", "Processeur", TypeCaracteristique.TEXTE,
                "Processeur", 0, null, false), 1, "CPU");
        DefinitionCaracteristique ram = carte(definition(categorie, "ram_go", "Mémoire vive", TypeCaracteristique.NOMBRE,
                "Mémoire", 1, "Go", true), 2, "RAM");
        DefinitionCaracteristique ecran = carte(definition(categorie, "ecran_pouces", "Taille d'écran",
                TypeCaracteristique.NOMBRE, "Écran", 2, "pouces", false), 3, null);
        DefinitionCaracteristique azerty = carte(definition(categorie, "azerty", "Clavier AZERTY",
                TypeCaracteristique.BOOLEEN, "Clavier", 3, null, false), 4, "AZERTY");
        DefinitionCaracteristique poids = definition(categorie, "poids_kg", "Poids", TypeCaracteristique.NOMBRE,
                "Général", 4, "kg", false);
        String sku8 = unique("VIVO-8");
        String sku16 = unique("VIVO-16");
        Produit produit = produit("Vivobook 15", marque, categorie, p -> {
            p.ajouterVariante(sku8, "8 Go", 389_000, 3);
            p.ajouterVariante(sku16, "16 Go", 449_000, 3);
        });
        caracteristiques(produit, (em, p) -> {
            valeur(em, p, cpu, null, "Ryzen 5 7530U");
            valeur(em, p, ram, sku8, 8);
            valeur(em, p, ram, sku16, 16);
            valeur(em, p, ecran, null, 15.6);
            valeur(em, p, azerty, null, true);
            valeur(em, p, poids, null, 1.7);
        });

        JsonNode carte = corps(lire("/api/produits?categorie={c}", categorie.getSlug()).andExpect(status().isOk()))
                .get("elements").get(0);
        assertThat(carte.get("skuParDefaut").asString()).isEqualTo(sku8);
        assertThat(lignes(carte)).containsExactly("CPU=Ryzen 5 7530U", "RAM=8" + INSECABLE + "Go",
                "Taille d'écran=15,6" + INSECABLE + "pouces", "AZERTY=Oui");

        // Filtre sur la RAM : la variante retenue est celle de 16 Go, sa carte montre sa propre valeur
        JsonNode filtree = corps(lire("/api/produits?categorie={c}&f.ram_go=16", categorie.getSlug()))
                .get("elements").get(0);
        assertThat(filtree.get("skuParDefaut").asString()).isEqualTo(sku16);
        assertThat(lignes(filtree)).contains("RAM=16" + INSECABLE + "Go");
    }

    @Test
    void laBaseRefuseUnCinquiemeEmplacementOuDeuxCaracteristiquesAuMemeRang() {
        Categorie categorie = categorie("Écrans", null, 0, true);
        DefinitionCaracteristique taille = definition(categorie, "taille", "Taille", TypeCaracteristique.NOMBRE,
                "Écran", 0, "pouces", true);
        DefinitionCaracteristique dalle = definition(categorie, "dalle", "Dalle", TypeCaracteristique.TEXTE,
                "Écran", 1, null, true);
        carte(taille, 1, "Taille");

        assertThatThrownBy(() -> carte(dalle, 5, "Dalle")).rootCause().hasMessageContaining("ck_definition_ordre_carte");
        assertThatThrownBy(() -> carte(dalle, 1, "Dalle")).rootCause().hasMessageContaining("uk_definition_ordre_carte");
    }

    // --- Filtre promotion ---------------------------------------------------------------------------------

    @Test
    void leFiltrePromotionRetientLaVarianteEnPromotion() throws Exception {
        Categorie categorie = categorie("Onduleurs", null, 0, true);
        Marque marque = marque("APC");
        String normal = unique("APC-700");
        String promo = unique("APC-1200");
        produit("Back-UPS 650", marque, categorie, p -> p.ajouterVariante(unique("APC-650"), "650 VA", 55_000, 4));
        Produit enPromotion = produit("Back-UPS Pro", marque, categorie, p -> {
            p.ajouterVariante(normal, "700 VA", 70_000, 4);
            p.ajouterVariante(promo, "1200 VA", 99_000, 4).setPrixBarre(119_000L);
        });

        lire("/api/produits?categorie={c}&promotion=true", categorie.getSlug())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.elements[0].id").value(enPromotion.getId()))
                .andExpect(jsonPath("$.elements[0].skuParDefaut").value(promo))
                .andExpect(jsonPath("$.elements[0].reduction").value(17));
        lire("/api/produits?promotion=oui").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parametre").value("promotion"));
    }

    // --- Sections de l'accueil -------------------------------------------------------------------------

    @Test
    void lAccueilNAnnonceDesMeilleuresVentesQueSIlYAAssezDeProduitsVendus() throws Exception {
        Categorie categorie = categorie("Réseau", null, 0, true);
        Marque marque = marque("TP-Link");
        // Plus récent que tous les produits des autres tests
        horloge.avancer(Duration.ofDays(1));
        Produit ancien = produit("Routeur ancien", marque, categorie, p -> p.ajouterVariante(unique("ANC"), "", 20_000, 5));
        enTransaction(em -> em.createNativeQuery("update produit set date_creation = date_creation - interval '91 days'"
                + " where id = " + ancien.getId()).executeUpdate());
        Produit misEnAvant = produit("Archer AX55", marque, categorie, p -> {
            p.setMisEnAvant(true);
            p.ajouterVariante(unique("AX55"), "", 45_000, 5).setPrixBarre(52_000L);
        });
        // Le plus récent de tous, quel que soit l'état laissé par les autres tests (horloge avancée, dates décalées)
        enTransaction(em -> em.createNativeQuery("update produit set date_creation = "
                + "(select max(date_creation) from produit) + interval '1 minute' where id = " + misEnAvant.getId())
                .executeUpdate());

        int seuil = proprietesAccueil.seuilMeilleuresVentes();
        long dejaVendus = enTransaction(em -> ((Number) em.createNativeQuery(
                "select count(*) from produit where actif and nombre_ventes > 0").getSingleResult()).longValue());
        List<Produit> vendus = new ArrayList<>();
        try {
            if (dejaVendus < seuil) {
                JsonNode sections = corps(lire("/api/accueil").andExpect(status().isOk())).get("sections");
                assertThat(codes(sections)).contains(AccueilService.SELECTION).doesNotContain(AccueilService.MEILLEURES_VENTES);
                assertThat(section(sections, AccueilService.SELECTION).get("titre").asString()).isEqualTo("Sélection du moment");
                assertThat(ids(section(sections, AccueilService.SELECTION))).contains(misEnAvant.getId());
            }

            JsonNode sections = corps(lire("/api/accueil")).get("sections");
            assertThat(codes(sections)).startsWith(AccueilService.NOUVEAUTES);
            assertThat(ids(section(sections, AccueilService.NOUVEAUTES)).getFirst()).isEqualTo(misEnAvant.getId());
            assertThat(ids(section(sections, AccueilService.NOUVEAUTES))).doesNotContain(ancien.getId());
            assertThat(ids(section(sections, AccueilService.PROMOTIONS))).contains(misEnAvant.getId());
            assertThat(sections.size()).isLessThanOrEqualTo(3);

            // Assez de produits vendus : « Meilleures ventes », le plus vendu en tête
            for (int i = 0; i < seuil; i++) {
                vendus.add(produit("Switch " + i, marque, categorie, p -> p.ajouterVariante(unique("SW"), "", 15_000, 5)));
            }
            // Plus vendus que tous les autres produits, quel que soit l'état laissé par les autres tests
            enTransaction(em -> em.createNativeQuery("update produit set nombre_ventes = "
                            + "(select coalesce(max(nombre_ventes), 0) from produit) + 1 + id where id in (:ids)")
                    .setParameter("ids", vendus.stream().map(Produit::getId).toList()).executeUpdate());
            sections = corps(lire("/api/accueil")).get("sections");
            assertThat(codes(sections)).contains(AccueilService.MEILLEURES_VENTES).doesNotContain(AccueilService.SELECTION);
            JsonNode meilleures = section(sections, AccueilService.MEILLEURES_VENTES);
            assertThat(meilleures.get("titre").asString()).isEqualTo("Meilleures ventes");
            assertThat(ids(meilleures).getFirst()).isEqualTo(vendus.getLast().getId());
            assertThat(meilleures.get("produits").size()).isLessThanOrEqualTo(proprietesAccueil.produitsParSection());
        } finally {
            // Base partagée : les autres tests ne doivent pas hériter de ces ventes
            if (!vendus.isEmpty()) {
                enTransaction(em -> em.createNativeQuery("update produit set nombre_ventes = 0 where id in (:ids)")
                        .setParameter("ids", vendus.stream().map(Produit::getId).toList()).executeUpdate());
            }
        }
    }

    // --- Promesses commerciales ---------------------------------------------------------------------------

    @Test
    void lesPromessesCommercialesSontLuesDepuisLesParametresEtUnElementVideEstMasque() throws Exception {
        record Sauvegarde(String message, List<ElementConfiance> elements) {
        }
        Sauvegarde avant = enTransaction(em -> {
            ParametresSite p = em.find(ParametresSite.class, ParametresSite.ID_UNIQUE);
            Sauvegarde s = new Sauvegarde(p.getMessageBarreSuperieure(), List.copyOf(p.getElementsConfiance()));
            p.setMessageBarreSuperieure("  Livraison à Dakar en 24 h  ");
            p.setElementsConfiance(new ArrayList<>(List.of(
                    new ElementConfiance(IconeConfiance.LIVRAISON, "Livraison à Dakar en 24 h", "Régions : 2 à 4 jours"),
                    new ElementConfiance(IconeConfiance.GARANTIE, "  ", "texte sans titre"),
                    new ElementConfiance(null, "Retrait en boutique", null))));
            return s;
        });
        try {
            lire("/api/parametres").andExpect(status().isOk())
                    .andExpect(jsonPath("$.messageBarreSuperieure").value("Livraison à Dakar en 24 h"))
                    .andExpect(jsonPath("$.elementsConfiance.length()").value(2))
                    .andExpect(jsonPath("$.elementsConfiance[0].icone").value("LIVRAISON"))
                    .andExpect(jsonPath("$.elementsConfiance[0].texte").value("Régions : 2 à 4 jours"))
                    .andExpect(jsonPath("$.elementsConfiance[1].titre").value("Retrait en boutique"));

            enTransaction(em -> {
                ParametresSite p = em.find(ParametresSite.class, ParametresSite.ID_UNIQUE);
                p.setMessageBarreSuperieure(" ");
                p.getElementsConfiance().clear();
                return null;
            });
            lire("/api/parametres")
                    .andExpect(jsonPath("$.messageBarreSuperieure").doesNotExist())
                    .andExpect(jsonPath("$.elementsConfiance").isEmpty());
        } finally {
            enTransaction(em -> {
                ParametresSite p = em.find(ParametresSite.class, ParametresSite.ID_UNIQUE);
                p.setMessageBarreSuperieure(avant.message());
                p.setElementsConfiance(new ArrayList<>(avant.elements()));
                return null;
            });
        }
    }

    // ------------------------------------------------------------------------------------------------------

    private DefinitionCaracteristique carte(DefinitionCaracteristique definition, int ordre, String libelleCourt) {
        return enTransaction(em -> {
            DefinitionCaracteristique chargee = em.find(DefinitionCaracteristique.class, definition.getId());
            chargee.setOrdreCarte((short) ordre);
            chargee.setLibelleCourt(libelleCourt);
            em.flush();
            return chargee;
        });
    }

    private static List<String> lignes(JsonNode carte) {
        List<String> lignes = new ArrayList<>();
        carte.get("caracteristiquesCles").forEach(c -> lignes.add(c.get("libelle").asString() + "=" + c.get("valeur").asString()));
        return lignes;
    }

    private static List<String> codes(JsonNode sections) {
        List<String> codes = new ArrayList<>();
        sections.forEach(s -> codes.add(s.get("code").asString()));
        return codes;
    }

    private static JsonNode section(JsonNode sections, String code) {
        for (JsonNode s : sections) {
            if (code.equals(s.get("code").asString())) {
                return s;
            }
        }
        throw new AssertionError("Section absente : " + code);
    }

    private static List<Long> ids(JsonNode section) {
        List<Long> ids = new ArrayList<>();
        section.get("produits").forEach(p -> ids.add(p.get("id").asLong()));
        return ids;
    }
}
