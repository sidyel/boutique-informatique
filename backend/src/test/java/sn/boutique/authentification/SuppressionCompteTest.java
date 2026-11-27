package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.ClientMailpit;
import sn.boutique.support.FabriqueCatalogue;
import tools.jackson.databind.JsonNode;

/**
 * Droit à l'effacement et droit d'accès (phase 5d) : suppression du compte selon l'ADR 0004 §3, téléchargement des
 * données, liste des avis. Vraie base, vrai Redis, vrai Mailpit.
 */
class SuppressionCompteTest extends AuthentificationTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(7_000_000 + System.nanoTime() % 1_000_000);

    @Value("${test.mailpit.url}")
    private String urlMailpit;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private PlatformTransactionManager transactions;
    @PersistenceContext
    private EntityManager em;

    private ClientMailpit mailpit;

    @BeforeEach
    void connecterMailpit() {
        mailpit = new ClientMailpit(urlMailpit);
    }

    // ------------------------------------------------------------------ fixtures

    /** Compte client vérifié, avec téléphone, adresse, consentement et un changement d'e-mail passé. */
    private Utilisateur client(String email) {
        Utilisateur u = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Awa", "Diop");
        u.setEmailVerifie(true);
        u.setTelephone("+221771234567");
        u = utilisateurDepot.save(u);
        jdbc.update("insert into adresse (utilisateur_id, libelle, nom_complet, telephone, ville, quartier, par_defaut)"
                + " values (?, 'Maison', 'Awa Diop', '+221771234567', 'Dakar', 'Plateau', true)", u.getId());
        jdbc.update("insert into historique_consentement (utilisateur_id, consentement, source, date_decision)"
                + " values (?, true, 'INSCRIPTION', now()), (?, false, 'COMPTE', now())", u.getId(), u.getId());
        jdbc.update("insert into changement_email (utilisateur_id, ancien_email, nouvel_email, empreinte_confirmation,"
                + " empreinte_signalement, statut, date_expiration) values (?, ?, ?, ?, ?, 'CONFIRME', now())",
                u.getId(), "ancienne." + email, email, hex(), hex());
        return u;
    }

    private static String hex() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    /** Commande avec tout le suivi publicitaire renseigné ; retourne son identifiant. */
    private long commande(Utilisateur u, String statut) {
        String numero = "CMD-2026-" + COMPTEUR.incrementAndGet();
        jdbc.update("""
                insert into commande (numero, utilisateur_id, email_client, telephone_client, nom_client,
                    livraison_nom_complet, livraison_telephone, livraison_ville, livraison_quartier,
                    livraison_adresse_ligne, livraison_point_de_repere, mode_livraison, sous_total, frais_livraison,
                    remise, total, mode_paiement, statut, utm_source, utm_medium, utm_campagne, utm_contenu, utm_terme,
                    gclid, fbclid, fbp, fbc, page_arrivee, referent, adresse_ip, user_agent, premiere_source,
                    premier_medium, premiere_campagne, premiere_page_arrivee, empreinte_jeton_suivi)
                values (?, ?, ?, '+221771234567', 'Awa Diop', 'Awa Diop', '+221771234567', 'Dakar', 'Plateau',
                    'Rue 10', 'Près du marché', 'RETRAIT_MAGASIN', 10000, 0, 0, 10000, 'A_LA_LIVRAISON', ?, 'facebook',
                    'cpc', 'promo', 'a', 'b', 'gclid-1', 'fbclid-1', 'fb.1.2.3', 'fb.1.2.4', '/produit/x?utm=1',
                    'https://exemple.sn', '41.82.0.1', 'Mozilla/5.0', 'google', 'cpc', 'c0', '/', ?)""",
                numero, u.getId(), u.getEmail(), statut, hex());
        long id = jdbc.queryForObject("select id from commande where numero = ?", Long.class, numero);
        jdbc.update("insert into historique_statut_commande (commande_id, nouveau_statut, auteur) values (?, ?, ?)",
                id, statut, u.getEmail());
        jdbc.update("insert into ligne_commande (commande_id, variante_id, nom_produit, sku, libelle_variante,"
                + " prix_unitaire, quantite, total_ligne) values (?, (select min(id) from variante_produit), 'Sacoche',"
                + " 'SKU-' || ?, '15 pouces', 10000, 1, 10000)", id, id);
        jdbc.update("insert into paiement (commande_id, fournisseur, reference_externe, montant, statut, donnees_brutes)"
                + " values (?, 'A_LA_LIVRAISON', ?, 10000, 'REUSSI', '{\"telephone\":\"771234567\"}'::jsonb)",
                id, "ref-" + id);
        return id;
    }

    private String facturer(long commandeId) {
        String numero = "FAC-2026-" + COMPTEUR.incrementAndGet();
        jdbc.update("""
                insert into facture (numero, commande_id, date_emission, regime_tva, taux_tva_bp, mention_tva,
                    client_nom, client_telephone, client_email, client_adresse, mode_paiement, total_ttc, total_ht,
                    total_tva, pdf, pdf_sha256)
                values (?, ?, now(), 'NON_APPLICABLE', 0, 'TVA non applicable', 'Awa Diop', '+221771234567',
                    'awa@exemple.sn', 'Rue 10, Plateau, Dakar', 'A_LA_LIVRAISON', 10000, 10000, 0, ?, ?)""",
                numero, commandeId, "%PDF-1.4".getBytes(), hex());
        return numero;
    }

    private long produit() {
        return new TransactionTemplate(transactions).execute(statut -> {
            var produit = FabriqueCatalogue.produit(em);
            em.persist(produit);
            em.flush();
            return produit.getId();
        });
    }

    private void avis(Utilisateur u, long produitId, boolean verifie) {
        jdbc.update("insert into avis (produit_id, utilisateur_id, nom_auteur, note, titre, commentaire, achat_verifie,"
                + " statut) values (?, ?, 'Awa D.', 5, 'Très bien', 'Livraison rapide', ?, 'APPROUVE')",
                produitId, u.getId(), verifie);
    }

    private ResultActions poster(String chemin, String acces, Object corps) throws Exception {
        var requete = post(chemin).with(depuisIpClient()).contentType(MediaType.APPLICATION_JSON).content(json(corps));
        return mockMvc.perform(acces == null ? requete : requete.header(HttpHeaders.AUTHORIZATION, "Bearer " + acces));
    }

    private String acces(String email) throws Exception {
        return jetonAcces(connecter(email, MOT_DE_PASSE).andReturn());
    }

    private static Map<String, String> suppression(String motDePasse, String confirmation) {
        return Map.of("motDePasse", motDePasse, "confirmation", confirmation);
    }

    private void assurerCatalogueMinimal() {
        long produit = produit();
        jdbc.update("insert into variante_produit (produit_id, sku, libelle, prix, stock) values (?, ?, 'Standard', 10000, 5)",
                produit, "SUP-" + COMPTEUR.incrementAndGet());
    }

    // ------------------------------------------------------------------ refus

    @Test
    void uneCommandeEnCoursRefuseLaSuppressionEtRienNEstModifie() throws Exception {
        assurerCatalogueMinimal();
        String email = emailUnique();
        Utilisateur u = client(email);
        long enCours = commande(u, "EN_ATTENTE_CONFIRMATION");
        commande(u, "LIVREE");
        String acces = acces(email);

        poster("/api/compte/suppression", acces, suppression(MOT_DE_PASSE, "SUPPRIMER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMANDE_EN_COURS"))
                .andExpect(jsonPath("$.detail").value(containsString(
                        jdbc.queryForObject("select numero from commande where id = ?", String.class, enCours))));

        assertThat(utilisateurDepot.findById(u.getId())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from commande where utilisateur_id = ? and adresse_ip is not null",
                Integer.class, u.getId())).isEqualTo(2);
    }

    @Test
    void unPaiementARembourserOuUneDemandeApresVenteOuverteRefuseAussi() throws Exception {
        assurerCatalogueMinimal();
        String email = emailUnique();
        Utilisateur u = client(email);
        long annulee = commande(u, "ANNULEE");
        jdbc.update("update paiement set statut = 'A_REMBOURSER' where commande_id = ?", annulee);
        String acces = acces(email);
        poster("/api/compte/suppression", acces, suppression(MOT_DE_PASSE, "SUPPRIMER"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("COMMANDE_EN_COURS"));

        jdbc.update("update paiement set statut = 'REUSSI' where commande_id = ?", annulee);
        jdbc.update("insert into demande_sav (commande_id, motif, description, statut) values (?, 'GARANTIE', 'x', 'OUVERTE')",
                annulee);
        poster("/api/compte/suppression", acces, suppression(MOT_DE_PASSE, "SUPPRIMER"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("COMMANDE_EN_COURS"));
        assertThat(utilisateurDepot.findById(u.getId())).isPresent();
    }

    @Test
    void leMotDePasseEtLeMotDeConfirmationSontExigesEtLaSessionAussi() throws Exception {
        String email = emailUnique();
        Utilisateur u = client(email);
        String acces = acces(email);

        poster("/api/compte/suppression", acces, suppression("Mauvais-mot-de-passe-1", "SUPPRIMER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_ACTUEL_INCORRECT"));
        poster("/api/compte/suppression", acces, suppression(MOT_DE_PASSE, "oui"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CONFIRMATION_INCORRECTE"));
        poster("/api/compte/suppression", null, suppression(MOT_DE_PASSE, "SUPPRIMER")).andExpect(status().isUnauthorized());
        assertThat(utilisateurDepot.findById(u.getId())).isPresent();
    }

    @Test
    void unCompteDeLEquipeNeSeSupprimePasParCetteRoute() throws Exception {
        var resultat = creerEtConnecter(sn.boutique.compte.modele.Role.ADMIN);
        poster("/api/compte/suppression", jetonAcces(resultat), suppression(MOT_DE_PASSE, "SUPPRIMER"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("COMPTE_NON_SUPPRIMABLE"));
    }

    // ------------------------------------------------------------------ anonymisation

    @Test
    void laSuppressionAnonymiseGardeLaFactureEtNeLaisseRienDansRedis() throws Exception {
        assurerCatalogueMinimal();
        String email = emailUnique();
        Utilisateur u = client(email);
        long id = u.getId();
        long facturee = commande(u, "LIVREE");
        String facture = facturer(facturee);
        long sansFacture = commande(u, "LIVREE");
        long annulee = commande(u, "ANNULEE");
        jdbc.update("insert into panier (utilisateur_id) values (?)", id);
        long produitVerifie = produit();
        long produitSimple = produit();
        avis(u, produitVerifie, true);
        avis(u, produitSimple, false);
        jdbc.update("insert into demande_sav (commande_id, motif, description, statut) values (?, 'GARANTIE', 'x', 'RESOLUE')",
                facturee);
        jdbc.update("insert into message_sav (demande_id, auteur_id, nom_auteur, de_la_boutique, contenu)"
                + " values ((select max(id) from demande_sav where commande_id = ?), ?, 'Awa Diop', false, 'Bonjour')",
                facturee, id);
        jdbc.update("insert into alerte_produit (email, variante_id, type) values (?, (select min(id) from variante_produit),"
                + " 'RETOUR_EN_STOCK')", email);
        int consentementsAvant = jdbc.queryForObject("select count(*) from historique_consentement where utilisateur_id = ?",
                Integer.class, id);

        // Connexions : des compteurs de limitation (clés hachées) existent maintenant dans Redis
        var connexion = connecter(email, MOT_DE_PASSE).andReturn();
        String acces = jetonAcces(connexion);
        String jetonRafraichissement = jetonRafraichissement(connexion);
        connecter(email, "Mauvais-mot-de-passe-1");
        assertThat(clesRedisContenant(GenerateurJetons.empreinte(email))).as("compteurs avant").isNotEmpty();

        poster("/api/compte/suppression", acces, suppression(MOT_DE_PASSE, " supprimer "))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        // Le compte, ses adresses, son panier et ses sessions ont disparu
        assertThat(utilisateurDepot.findById(id)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from adresse where utilisateur_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from panier where utilisateur_id = ?", Integer.class, id)).isZero();
        assertThat(jetonsActifs(id)).isZero();
        rafraichir(jetonRafraichissement).andExpect(status().isUnauthorized());
        profil(acces).andExpect(status().isNotFound());   // jeton d'accès encore valide : plus de compte
        assertThat(jdbc.queryForObject("select count(*) from alerte_produit where email = ?", Integer.class, email)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from echec_connexion where empreinte_email = ?", Integer.class,
                GenerateurJetons.empreinte(email))).isZero();

        // Commande facturée : facture et coordonnées gardées, tout le suivi effacé
        Map<String, Object> c = jdbc.queryForMap("select * from commande where id = ?", facturee);
        assertThat(c.get("utilisateur_id")).isNull();
        assertThat(c.get("email_client")).isEqualTo(email);
        assertThat(c.get("telephone_client")).isEqualTo("+221771234567");
        assertThat(c.get("livraison_nom_complet")).isEqualTo("Awa Diop");
        assertThat(c.get("livraison_adresse_ligne")).isEqualTo("Rue 10");
        assertThat(c.get("total")).isEqualTo(10000L);
        assertThat(c.get("anonymisee_le")).isNotNull();
        for (String colonne : List.of("adresse_ip", "user_agent", "fbp", "fbc", "gclid", "fbclid", "utm_source",
                "utm_medium", "utm_campagne", "utm_contenu", "utm_terme", "page_arrivee", "referent", "premiere_source",
                "premier_medium", "premiere_campagne", "premiere_page_arrivee", "empreinte_jeton_suivi")) {
            assertThat(c.get(colonne)).as(colonne).isNull();
        }
        assertThat(c.get("livraison_point_de_repere")).as("repère absent de la facture").isNull();
        assertThat(jdbc.queryForObject("select numero from facture where commande_id = ?", String.class, facturee))
                .isEqualTo(facture);
        assertThat(jdbc.queryForObject("select donnees_brutes from paiement where commande_id = ?", String.class, facturee))
                .isNull();
        assertThat(jdbc.queryForObject("select montant from paiement where commande_id = ?", Long.class, facturee))
                .isEqualTo(10000L);
        assertThat(jdbc.queryForObject("select auteur from historique_statut_commande where commande_id = ?", String.class,
                facturee)).isEqualTo("Client supprimé");
        assertThat(jdbc.queryForObject("select count(*) from ligne_commande where commande_id = ?", Integer.class, facturee))
                .isEqualTo(1);

        // Commandes sans facture : plus aucune coordonnée
        for (long sans : List.of(sansFacture, annulee)) {
            Map<String, Object> s = jdbc.queryForMap("select * from commande where id = ?", sans);
            assertThat(s.get("livraison_nom_complet")).isEqualTo("Client supprimé");
            for (String colonne : List.of("email_client", "telephone_client", "nom_client", "livraison_telephone",
                    "livraison_ville", "livraison_quartier", "livraison_adresse_ligne", "livraison_point_de_repere")) {
                assertThat(s.get(colonne)).as(colonne).isNull();
            }
            assertThat(s.get("total")).isEqualTo(10000L);
        }

        // Avis publiés mais signés « Client vérifié » ou « Client » ; message SAV ; historiques sans identité
        assertThat(jdbc.queryForObject("select nom_auteur from avis where produit_id = ?", String.class, produitVerifie))
                .isEqualTo("Client vérifié");
        assertThat(jdbc.queryForObject("select nom_auteur from avis where produit_id = ?", String.class, produitSimple))
                .isEqualTo("Client");
        assertThat(jdbc.queryForObject("select count(*) from avis where produit_id in (?, ?) and statut = 'APPROUVE'"
                + " and utilisateur_id is null", Integer.class, produitVerifie, produitSimple)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select nom_auteur from message_sav where contenu = 'Bonjour' and demande_id ="
                + " (select max(id) from demande_sav where commande_id = ?)", String.class, facturee))
                .isEqualTo("Client supprimé");
        assertThat(jdbc.queryForObject("select count(*) from historique_consentement where utilisateur_id is null"
                + " and date_decision > now() - interval '1 hour'", Integer.class)).isGreaterThanOrEqualTo(consentementsAvant);
        assertThat(jdbc.queryForList("select ancien_email, nouvel_email from changement_email where utilisateur_id is null"
                + " and ancien_email = 'supprime@invalide.local'")).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from changement_email where nouvel_email = ?", Integer.class, email))
                .isZero();

        // Redis : plus aucune clé ne contient l'empreinte de l'e-mail, du téléphone ni l'identifiant du compte,
        // et rien n'y est stocké en clair
        assertThat(clesRedisContenant(GenerateurJetons.empreinte(email))).as("clés de l'e-mail").isEmpty();
        assertThat(clesRedisContenant(GenerateurJetons.empreinte("+221771234567"))).as("clés du téléphone").isEmpty();
        assertThat(clesRedisContenant("mot-de-passe-actuel:" + id)).as("clés du compte").isEmpty();
        assertThat(redisContientEnClair(email)).as("e-mail en clair dans Redis").isEmpty();
        assertThat(redisContientEnClair("771234567")).as("téléphone en clair dans Redis").isEmpty();

        // E-mail de confirmation, envoyé à l'adresse effacée
        var emails = mailpit.attendre(email, 1);
        assertThat(emails).anySatisfy(e -> assertThat(e.sujet()).isEqualTo("Votre compte a été supprimé"));
    }

    @Test
    void uneAdresseAnciennementUtiliseeParLeCompteEstAussiOubliee() throws Exception {
        String email = emailUnique();
        Utilisateur u = client(email);
        String ancienne = jdbc.queryForObject("select ancien_email from changement_email where utilisateur_id = ?",
                String.class, u.getId());
        jdbc.update("insert into echec_connexion (empreinte_email, adresse_ip, echecs, date_creation, date_modification)"
                + " values (?, '10.0.0.9', 2, now(), now())", GenerateurJetons.empreinte(ancienne));
        poster("/api/compte/suppression", acces(email), suppression(MOT_DE_PASSE, "SUPPRIMER")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from echec_connexion where empreinte_email = ?", Integer.class,
                GenerateurJetons.empreinte(ancienne))).isZero();
    }

    private List<String> clesRedisContenant(String fragment) {
        List<String> cles = new ArrayList<>();
        try (var curseur = redis.scan(ScanOptions.scanOptions().match("*" + fragment + "*").count(1000).build())) {
            curseur.forEachRemaining(cles::add);
        }
        return cles;
    }

    /** Clés ou valeurs de Redis contenant le texte donné (parcours complet : l'instance de test est petite). */
    private List<String> redisContientEnClair(String texte) {
        List<String> trouves = new ArrayList<>();
        try (var curseur = redis.scan(ScanOptions.scanOptions().count(1000).build())) {
            curseur.forEachRemaining(cle -> {
                if (cle.contains(texte)) {
                    trouves.add(cle);
                    return;
                }
                try {
                    Object valeur = redis.opsForValue().get(cle);
                    if (valeur != null && valeur.toString().contains(texte)) {
                        trouves.add(cle);
                    }
                } catch (RuntimeException autreType) {
                    // clé d'un autre type que « chaîne » : rien de personnel n'y est écrit par l'application
                }
            });
        }
        return trouves;
    }

    // ------------------------------------------------------------------ téléchargement des données

    @Test
    void leClientTelechargeSesDonneesAvecSonMotDePasseEtSansCache() throws Exception {
        assurerCatalogueMinimal();
        String email = emailUnique();
        Utilisateur u = client(email);
        long facturee = commande(u, "LIVREE");
        String facture = facturer(facturee);
        avis(u, produit(), true);
        String acces = acces(email);

        poster("/api/compte/mes-donnees", acces, Map.of("motDePasse", "Mauvais-mot-de-passe-1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_ACTUEL_INCORRECT"));
        poster("/api/compte/mes-donnees", null, Map.of("motDePasse", MOT_DE_PASSE)).andExpect(status().isUnauthorized());

        String corps = poster("/api/compte/mes-donnees", acces, Map.of("motDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment; filename=\"mes-donnees.json\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
                .andReturn().getResponse().getContentAsString();
        JsonNode donnees = jsonMapper.readTree(corps);

        assertThat(donnees.at("/profil/email").asString()).isEqualTo(email);
        assertThat(donnees.at("/profil/prenom").asString()).isEqualTo("Awa");
        assertThat(donnees.at("/profil/telephone").asString()).isEqualTo("+221771234567");
        assertThat(donnees.at("/adresses/0/ville").asString()).isEqualTo("Dakar");
        assertThat(donnees.at("/commandes/0/numeroFacture").asString()).isEqualTo(facture);
        assertThat(donnees.at("/commandes/0/lignes/0/produit").asString()).isEqualTo("Sacoche");
        assertThat(donnees.at("/commandes/0/total").asLong()).isEqualTo(10000L);
        assertThat(donnees.at("/commandes/0/paiements/0/montant").asLong()).isEqualTo(10000L);
        assertThat(donnees.at("/commandes/0/historique/0/statut").asString()).isEqualTo("LIVREE");
        assertThat(donnees.at("/consentements").size()).isEqualTo(2);
        assertThat(donnees.at("/changementsEmail").size()).isEqualTo(1);
        assertThat(donnees.at("/avis/0/note").asInt()).isEqualTo(5);
        assertThat(donnees.at("/genereLe").asString()).endsWith("Z");
        // Jamais de secret ni de donnée technique de suivi publicitaire dans le fichier
        assertThat(corps).doesNotContain("$2a$").doesNotContain("motDePasseHash").doesNotContain("41.82.0.1")
                .doesNotContain("gclid-1").doesNotContain("fb.1.2.3").doesNotContain("empreinte");
    }

    @Test
    void leFichierNeContientQueLesDonneesDuDemandeur() throws Exception {
        assurerCatalogueMinimal();
        String email = emailUnique();
        Utilisateur moi = client(email);
        commande(moi, "LIVREE");
        String autreEmail = emailUnique();
        Utilisateur autre = client(autreEmail);
        long saCommande = commande(autre, "LIVREE");

        String corps = poster("/api/compte/mes-donnees", acces(email), Map.of("motDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(corps).contains(email).doesNotContain(autreEmail)
                .doesNotContain(jdbc.queryForObject("select numero from commande where id = ?", String.class, saCommande));
    }

    // ------------------------------------------------------------------ mes avis

    @Test
    void laListeDesAvisEstVideSansAvisPuisMontreLesAvisDuClientSeulement() throws Exception {
        String email = emailUnique();
        Utilisateur u = client(email);
        String acces = acces(email);
        mockMvc.perform(get("/api/compte/avis").header(HttpHeaders.AUTHORIZATION, "Bearer " + acces))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        long produit = produit();
        avis(u, produit, true);
        Utilisateur autre = client(emailUnique());
        avis(autre, produit(), false);
        mockMvc.perform(get("/api/compte/avis").header(HttpHeaders.AUTHORIZATION, "Bearer " + acces))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].note").value(5)).andExpect(jsonPath("$[0].statut").value("APPROUVE"))
                .andExpect(jsonPath("$[0].achatVerifie").value(true));
        mockMvc.perform(get("/api/compte/avis")).andExpect(status().isUnauthorized());
    }
}
