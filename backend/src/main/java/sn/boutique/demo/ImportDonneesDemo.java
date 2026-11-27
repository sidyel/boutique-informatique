package sn.boutique.demo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.CategorieDepot;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.catalogue.depot.DefinitionCaracteristiqueDepot;
import sn.boutique.catalogue.depot.MarqueDepot;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeMedia;
import sn.boutique.catalogue.modele.ValeurCaracteristique;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.depot.ZoneLivraisonDepot;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.contenu.depot.ArticleDepot;
import sn.boutique.contenu.depot.PageStatiqueDepot;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.Article;
import sn.boutique.contenu.modele.PageStatique;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.demo.DonneesDemo.ArticleDemo;
import sn.boutique.demo.DonneesDemo.BanniereDemo;
import sn.boutique.demo.DonneesDemo.Catalogue;
import sn.boutique.demo.DonneesDemo.CategorieDemo;
import sn.boutique.demo.DonneesDemo.CodePromoDemo;
import sn.boutique.demo.DonneesDemo.CollectionDemo;
import sn.boutique.demo.DonneesDemo.DefinitionDemo;
import sn.boutique.demo.DonneesDemo.PageDemo;
import sn.boutique.demo.DonneesDemo.ProduitDemo;
import sn.boutique.demo.DonneesDemo.Site;
import sn.boutique.demo.DonneesDemo.VarianteDemo;
import sn.boutique.demo.DonneesDemo.ZoneDemo;
import sn.boutique.marketing.depot.BanniereDepot;
import sn.boutique.marketing.depot.CodePromoDepot;
import sn.boutique.marketing.depot.CollectionDepot;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.modele.Collection;
import sn.boutique.stock.depot.MouvementStockDepot;
import sn.boutique.stock.modele.MouvementStock;
import sn.boutique.stock.modele.TypeMouvementStock;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Charge les données de démonstration de {@code classpath:demo/} (profil « demo »).
 * <p>
 * <strong>Idempotent</strong> : chaque objet est retrouvé par sa clé naturelle (slug, SKU, code, nom, e-mail)
 * et n'est créé que s'il manque. Relancer l'application ne duplique rien et ne modifie pas ce que
 * l'équipe a changé depuis l'admin.
 * <p>
 * Les images sont des WebP neutres servis par le frontend ({@code /demo/images/…}) : aucune photo de fabricant
 * (sources SVG dans {@code infra/demo/images-sources}, rendues en WebP comme les photos de production).
 */
@Slf4j
@Service
@Profile("demo")
@RequiredArgsConstructor
public class ImportDonneesDemo {

    static final String DOSSIER_IMAGES = "/demo/images/";
    static final String AUTEUR = "SYSTEME";
    private static final String CONTENU_PAGE_PAR_DEFAUT =
            "<p><em>Contenu de démonstration : à remplacer par le texte validé par la boutique.</em></p>";
    private static final int LONGUEUR_MIN_MOT_DE_PASSE_ADMIN = 12;

    private final JsonMapper jsonMapper;
    private final ProprietesDemo proprietes;
    private final PasswordEncoder encodeurMotDePasse;
    private final Clock horloge;
    private final MarqueDepot marqueDepot;
    private final CategorieDepot categorieDepot;
    private final DefinitionCaracteristiqueDepot definitionDepot;
    private final ProduitDepot produitDepot;
    private final MouvementStockDepot mouvementDepot;
    private final ZoneLivraisonDepot zoneDepot;
    private final PageStatiqueDepot pageDepot;
    private final BanniereDepot banniereDepot;
    private final CodePromoDepot codePromoDepot;
    private final ArticleDepot articleDepot;
    private final CollectionDepot collectionDepot;
    private final ParametresSiteDepot parametresDepot;
    private final UtilisateurDepot utilisateurDepot;
    private final ApplicationEventPublisher evenements;

    /** Résumé de ce qui a été créé lors d'un chargement. */
    public record Bilan(int marques, int categories, int produits, int variantes, int autres, boolean admin) {

        public boolean rienCree() {
            return marques + categories + produits + variantes + autres == 0 && !admin;
        }
    }

    @Transactional
    public Bilan charger() {
        Catalogue catalogue = lire("demo/catalogue.json", Catalogue.class);
        Site site = lire("demo/site.json", Site.class);
        Compteurs compteurs = new Compteurs();

        Map<String, Marque> marques = importerMarques(catalogue, compteurs);
        Map<String, Categorie> categories = importerCategories(catalogue, compteurs);
        importerProduits(catalogue, marques, categories, compteurs);
        importerSite(site, categories, compteurs);
        boolean admin = creerAdmin();
        creerClient();

        Bilan bilan = new Bilan(compteurs.marques, compteurs.categories, compteurs.produits, compteurs.variantes,
                compteurs.autres, admin);
        log.info("Données de démonstration : {}", bilan.rienCree() ? "déjà présentes, rien à créer" : bilan);
        if (!bilan.rienCree()) {
            // Caches publics vidés après la validation : une liste vide mise en cache avant l'import disparaît
            evenements.publishEvent(new CatalogueModifie("import des données de démonstration"));
        }
        return bilan;
    }

    // ------------------------------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------------------------------

    private Map<String, Marque> importerMarques(Catalogue catalogue, Compteurs compteurs) {
        Map<String, Marque> marques = new HashMap<>();
        for (var demo : catalogue.marques()) {
            Marque marque = marqueDepot.findBySlug(demo.slug()).orElseGet(() -> {
                compteurs.marques++;
                return marqueDepot.save(new Marque(demo.nom(), demo.slug()));
            });
            marques.put(demo.slug(), marque);
        }
        return marques;
    }

    private Map<String, Categorie> importerCategories(Catalogue catalogue, Compteurs compteurs) {
        Map<String, Categorie> categories = new HashMap<>();
        // Les parents sont déclarés avant leurs enfants dans le fichier
        for (CategorieDemo demo : catalogue.categories()) {
            Categorie categorie = categorieDepot.findBySlug(demo.slug()).orElseGet(() -> {
                Categorie nouvelle = new Categorie(demo.nom(), demo.slug());
                nouvelle.setParent(demo.parent() == null ? null : exiger(categories, demo.parent(), "catégorie"));
                nouvelle.setPosition(demo.position());
                nouvelle.setDescription(demo.description());
                nouvelle.setImageUrl(image(demo.image()));
                nouvelle.setCategorieGoogle(demo.categorieGoogle());
                compteurs.categories++;
                return categorieDepot.save(nouvelle);
            });
            categories.put(demo.slug(), categorie);
            importerDefinitions(categorie, demo.caracteristiques());
        }
        return categories;
    }

    private void importerDefinitions(Categorie categorie, List<DefinitionDemo> definitions) {
        Map<String, DefinitionCaracteristique> existantes = definitionsParCle(categorie);
        int ordre = 0;
        for (DefinitionDemo demo : definitions) {
            if (!existantes.containsKey(demo.cle())) {
                var definition = new DefinitionCaracteristique(categorie, demo.cle(), demo.libelle(), demo.type(), demo.groupe());
                definition.setUnite(demo.unite());
                definition.setFiltrable(demo.filtrable());
                definition.setCleRecherche(demo.cleRecherche());
                definition.setOrdre(ordre);
                affecterCarte(definition, demo);
                definitionDepot.save(definition);
            } else {
                // Base importée avant la V13 : complète la carte, sans écraser un réglage de l'admin
                DefinitionCaracteristique existante = existantes.get(demo.cle());
                if (existante.getOrdreCarte() == null && existante.getLibelleCourt() == null
                        && demo.ordreCarte() != null) {
                    affecterCarte(existante, demo);
                }
            }
            ordre++;
        }
    }

    private static void affecterCarte(DefinitionCaracteristique definition, DefinitionDemo demo) {
        definition.setOrdreCarte(demo.ordreCarte() == null ? null : demo.ordreCarte().shortValue());
        definition.setLibelleCourt(demo.libelleCourt());
    }

    private Map<String, DefinitionCaracteristique> definitionsParCle(Categorie categorie) {
        Map<String, DefinitionCaracteristique> parCle = new HashMap<>();
        if (categorie.getId() != null) {
            definitionDepot.findByCategorieIdOrderByGroupeAscOrdreAsc(categorie.getId())
                    .forEach(definition -> parCle.put(definition.getCle(), definition));
        }
        return parCle;
    }

    private void importerProduits(Catalogue catalogue, Map<String, Marque> marques, Map<String, Categorie> categories,
                                  Compteurs compteurs) {
        for (ProduitDemo demo : catalogue.produits()) {
            if (produitDepot.existsBySlug(demo.slug())) {
                continue;
            }
            Categorie categorie = exiger(categories, demo.categorie(), "catégorie");
            Map<String, DefinitionCaracteristique> definitions = definitionsParCle(categorie);

            Produit produit = new Produit(demo.nom(), demo.slug(), exiger(marques, demo.marque(), "marque"), categorie);
            produit.setEtat(demo.etat());
            produit.setGarantieMois(demo.garantieMois());
            produit.setMpn(demo.mpn());
            produit.setMisEnAvant(demo.misEnAvant());
            produit.setDescriptionCourte(demo.descriptionCourte());
            produit.setDescriptionLongue(demo.descriptionLongue());
            produit.ajouterMedia(TypeMedia.IMAGE, image(demo.image()), demo.nom() + " (image de démonstration)");
            Map<VarianteProduit, VarianteDemo> variantes = new LinkedHashMap<>();
            for (VarianteDemo varianteDemo : demo.variantes()) {
                VarianteProduit variante = produit.ajouterVariante(
                        varianteDemo.sku(), varianteDemo.libelle(), varianteDemo.prix(), varianteDemo.stock());
                variante.setAttributs(new LinkedHashMap<>(varianteDemo.attributs()));
                variante.setPrixBarre(varianteDemo.prixBarre());
                variante.setPoidsGrammes(varianteDemo.poidsGrammes());
                variantes.put(variante, varianteDemo);
                compteurs.variantes++;
            }
            // Produit et variantes d'abord : une valeur propre à une variante doit viser une variante
            // déjà en base (voir Produit.ajouterCaracteristique)
            produitDepot.save(produit);
            affecterCaracteristiques(produit, null, demo.caracteristiques(), definitions, demo.slug());
            variantes.forEach((variante, varianteDemo) -> affecterCaracteristiques(
                    produit, variante, varianteDemo.caracteristiques(), definitions, varianteDemo.sku()));
            enregistrerStockInitial(produit);
            compteurs.produits++;
        }
    }

    private void affecterCaracteristiques(Produit produit, VarianteProduit variante, Map<String, Object> valeurs,
                                          Map<String, DefinitionCaracteristique> definitions, String reference) {
        if (valeurs == null) {
            return;
        }
        valeurs.forEach((cle, valeur) -> {
            DefinitionCaracteristique definition = definitions.get(cle);
            if (definition == null) {
                throw new IllegalStateException("Données de démonstration : caractéristique « %s » inconnue pour %s (catégorie %s)"
                        .formatted(cle, reference, produit.getCategorie().getSlug()));
            }
            ValeurCaracteristique cible = produit.ajouterCaracteristique(definition);
            cible.setVariante(variante);
            switch (definition.getType()) {
                case NOMBRE -> cible.setValeurNombre(new BigDecimal(valeur.toString()));
                case BOOLEEN -> cible.setValeurBooleen((Boolean) valeur);
                case TEXTE, LISTE -> cible.setValeurTexte(valeur.toString());
            }
        });
    }

    /** Journal du stock cohérent dès le départ : une entrée par variante en stock. */
    private void enregistrerStockInitial(Produit produit) {
        for (VarianteProduit variante : produit.getVariantes()) {
            if (variante.getStock() > 0) {
                mouvementDepot.save(new MouvementStock(variante, variante.getStock(), variante.getStock(),
                        TypeMouvementStock.ENTREE, "Stock initial (données de démonstration)", null, AUTEUR));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Site : paramètres, livraison, pages, bannières, codes promo, articles, collections
    // ------------------------------------------------------------------------------------------

    private void importerSite(Site site, Map<String, Categorie> categories, Compteurs compteurs) {
        ParametresSite parametres = parametresDepot.lire();
        // Ne remplace pas des paramètres déjà saisis dans l'admin
        if (parametres.getWhatsapp() == null) {
            var demo = site.parametres();
            parametres.setTelephone(demo.telephone());
            parametres.setWhatsapp(demo.whatsapp());
            parametres.setEmail(demo.email());
            parametres.setAdresse(demo.adresse());
            parametres.setHoraires(demo.horaires());
            parametres.getLiensReseauxSociaux().putAll(demo.liensReseauxSociaux());
            compteurs.autres++;
        }
        // Promesses commerciales (V13) : seulement si l'équipe n'en a saisi aucune
        if (parametres.getMessageBarreSuperieure() == null && parametres.getElementsConfiance().isEmpty()) {
            parametres.setMessageBarreSuperieure(site.parametres().messageBarreSuperieure());
            parametres.getElementsConfiance().addAll(site.parametres().elementsConfiance());
            compteurs.autres++;
        }
        // Délai de la fiche produit (V14) : seulement s'il n'a pas été saisi
        if (parametres.getDelaiLivraison() == null) {
            parametres.setDelaiLivraison(site.parametres().delaiLivraison());
            compteurs.autres++;
        }

        for (ZoneDemo demo : site.zones()) {
            if (!zoneDepot.existsByNom(demo.nom())) {
                ZoneLivraison zone = new ZoneLivraison(demo.nom(), demo.tarif(), demo.delaiJours());
                zone.getVillesQuartiers().addAll(demo.villesQuartiers());
                zoneDepot.save(zone);
                compteurs.autres++;
            }
        }

        for (PageDemo demo : site.pages()) {
            if (!pageDepot.existsBySlug(demo.slug())) {
                String contenu = demo.contenu() != null ? demo.contenu() : CONTENU_PAGE_PAR_DEFAUT;
                pageDepot.save(new PageStatique(demo.slug(), demo.titre(), contenu));
                compteurs.autres++;
            }
        }

        for (BanniereDemo demo : site.bannieres()) {
            String imageOrdinateur = image(demo.image() + "-ordinateur");
            if (!banniereDepot.existsByImageOrdinateurUrl(imageOrdinateur)) {
                Banniere banniere = new Banniere(demo.emplacement(), imageOrdinateur, image(demo.image() + "-mobile"),
                        demo.texteAlternatif());
                banniere.setTitre(demo.titre());
                banniere.setSousTitre(demo.sousTitre());
                banniere.setTexteBouton(demo.texteBouton());
                banniere.setLien(demo.lien());
                banniere.setPosition(demo.position());
                banniereDepot.save(banniere);
                compteurs.autres++;
            }
        }

        for (CodePromoDemo demo : site.codesPromo()) {
            if (codePromoDepot.trouverParCode(demo.code()).isEmpty()) {
                CodePromo code = new CodePromo(demo.code(), demo.type(), demo.valeur());
                code.setMontantMinimum(demo.montantMinimum());
                code.setUtilisationsMax(demo.utilisationsMax());
                code.setUtilisationsParClient(demo.utilisationsParClient());
                codePromoDepot.save(code);
                compteurs.autres++;
            }
        }

        for (ArticleDemo demo : site.articles()) {
            if (!articleDepot.existsBySlug(demo.slug())) {
                Article article = new Article(demo.titre(), demo.slug(), "La rédaction");
                article.setExtrait(demo.extrait());
                article.setContenu(demo.contenu());
                article.setImageCouverture(image(demo.image()));
                article.setCategorie(exiger(categories, demo.categorie(), "catégorie"));
                demo.produitsLies().forEach(slug -> article.getProduitsLies().add(produit(slug)));
                article.publier(horloge.instant());
                articleDepot.save(article);
                compteurs.autres++;
            }
        }

        for (CollectionDemo demo : site.collections()) {
            if (!collectionDepot.existsBySlug(demo.slug())) {
                Collection collection = new Collection(demo.titre(), demo.slug());
                collection.setDescription(demo.description());
                collection.setIndexable(demo.indexable());
                demo.produits().forEach(slug -> collection.getProduits().add(produit(slug)));
                collectionDepot.save(collection);
                compteurs.autres++;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Compte administrateur
    // ------------------------------------------------------------------------------------------

    private boolean creerAdmin() {
        String motDePasse = proprietes.adminMotDePasse();
        if (motDePasse == null || motDePasse.isBlank()) {
            log.warn("DEMO_ADMIN_MOT_DE_PASSE absent : le compte administrateur de démonstration n'est pas créé.");
            return false;
        }
        if (motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE_ADMIN) {
            throw new IllegalStateException("DEMO_ADMIN_MOT_DE_PASSE doit contenir au moins %d caractères."
                    .formatted(LONGUEUR_MIN_MOT_DE_PASSE_ADMIN));
        }
        if (utilisateurDepot.emailExiste(proprietes.adminEmail())) {
            return false;
        }
        Utilisateur admin = new Utilisateur(proprietes.adminEmail(), encodeurMotDePasse.encode(motDePasse),
                "Admin", "Démo");
        admin.setRole(Role.SUPER_ADMIN);
        admin.setEmailVerifie(true);
        utilisateurDepot.save(admin);
        log.info("Compte administrateur de démonstration créé : {}", admin.getEmail());
        return true;
    }

    /** Compte client vérifié, dédié aux mesures Lighthouse connectées : jamais utilisé pour autre chose. */
    private void creerClient() {
        String motDePasse = proprietes.clientMotDePasse();
        if (motDePasse == null || motDePasse.isBlank() || utilisateurDepot.emailExiste(proprietes.clientEmail())) {
            return;
        }
        if (motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE_ADMIN) {
            throw new IllegalStateException("DEMO_CLIENT_MOT_DE_PASSE doit contenir au moins %d caractères."
                    .formatted(LONGUEUR_MIN_MOT_DE_PASSE_ADMIN));
        }
        Utilisateur client = new Utilisateur(proprietes.clientEmail(), encodeurMotDePasse.encode(motDePasse),
                "Awa", "Démo");
        client.setEmailVerifie(true);
        utilisateurDepot.save(client);
        log.info("Compte client de démonstration créé : {}", client.getEmail());
    }

    // ------------------------------------------------------------------------------------------

    private Produit produit(String slug) {
        return produitDepot.findBySlug(slug).orElseThrow(
                () -> new IllegalStateException("Données de démonstration : produit « %s » introuvable".formatted(slug)));
    }

    private static <T> T exiger(Map<String, T> index, String slug, String nature) {
        T valeur = index.get(slug);
        if (valeur == null) {
            throw new IllegalStateException("Données de démonstration : %s « %s » introuvable".formatted(nature, slug));
        }
        return valeur;
    }

    static String image(String nom) {
        return DOSSIER_IMAGES + nom + ".webp";
    }

    private <T> T lire(String chemin, Class<T> type) {
        // Strict : une faute de frappe dans un nom de champ doit arrêter le chargement
        JsonMapper strict = jsonMapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        try (InputStream flux = new ClassPathResource(chemin).getInputStream()) {
            return strict.readValue(flux, type);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible de " + chemin, e);
        }
    }

    private static final class Compteurs {
        private int marques;
        private int categories;
        private int produits;
        private int variantes;
        private int autres;
    }
}
