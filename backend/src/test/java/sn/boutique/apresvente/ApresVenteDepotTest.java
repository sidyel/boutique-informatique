package sn.boutique.apresvente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.Contraintes.contrainteViolee;
import static sn.boutique.support.FabriqueCatalogue.unique;

import java.math.BigDecimal;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.apresvente.depot.AlerteProduitDepot;
import sn.boutique.apresvente.depot.AvisDepot;
import sn.boutique.apresvente.depot.DemandeSavDepot;
import sn.boutique.apresvente.modele.AlerteProduit;
import sn.boutique.apresvente.modele.Avis;
import sn.boutique.apresvente.modele.DemandeSav;
import sn.boutique.apresvente.modele.ElementListeSouhaits;
import sn.boutique.apresvente.modele.MotifSav;
import sn.boutique.apresvente.modele.StatutAvis;
import sn.boutique.apresvente.modele.TypeAlerte;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.LigneCommande;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.stock.depot.MouvementStockDepot;
import sn.boutique.stock.modele.MouvementStock;
import sn.boutique.stock.modele.TypeMouvementStock;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.TestDepot;

@TestDepot
class ApresVenteDepotTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private AvisDepot avisDepot;

    @Autowired
    private AlerteProduitDepot alerteDepot;

    @Autowired
    private DemandeSavDepot demandeSavDepot;

    @Autowired
    private MouvementStockDepot mouvementDepot;

    private VarianteProduit variante() {
        Produit produit = FabriqueCatalogue.produit(em);
        VarianteProduit variante = produit.ajouterVariante(unique("SKU"), "16 Go", 450_000, 5);
        em.persist(produit);
        return variante;
    }

    private Commande commandeAvec(VarianteProduit variante) {
        Commande commande = FabriqueComptes.commandeRetrait();
        commande.ajouterLigne(variante, null, 1);
        commande.recalculerTotaux();
        em.persist(commande);
        return commande;
    }

    @Test
    void laNoteDuProduitNeCompteQueLesAvisApprouves() {
        Produit produit = FabriqueCatalogue.produit(em);
        em.persist(produit);
        Avis cinq = new Avis(produit, FabriqueComptes.utilisateur(em), "Awa D.", 5);
        Avis quatre = new Avis(produit, FabriqueComptes.utilisateur(em), "Moussa N.", 4);
        Avis un = new Avis(produit, FabriqueComptes.utilisateur(em), "Fatou S.", 1);
        avisDepot.saveAll(java.util.List.of(cinq, quatre, un));
        em.flush();
        em.refresh(produit);
        assertThat(produit.getNombreAvis()).isZero();

        cinq.setStatut(StatutAvis.APPROUVE);
        quatre.setStatut(StatutAvis.APPROUVE);
        un.setStatut(StatutAvis.REJETE);
        em.flush();
        // Valeurs calculées par trigger : relire le produit (voir ADR 0004)
        em.refresh(produit);
        assertThat(produit.getNombreAvis()).isEqualTo(2);
        assertThat(produit.getNoteMoyenne()).isEqualByComparingTo(new BigDecimal("4.50"));

        avisDepot.delete(quatre);
        em.flush();
        em.refresh(produit);
        assertThat(produit.getNombreAvis()).isEqualTo(1);
        assertThat(produit.getNoteMoyenne()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void unClientNeLaissePasDeuxAvisSurLeMemeProduit() {
        Produit produit = FabriqueCatalogue.produit(em);
        em.persist(produit);
        Utilisateur client = FabriqueComptes.utilisateur(em);
        avisDepot.saveAndFlush(new Avis(produit, client, "Awa D.", 5));

        assertThat(avisDepot.existsByProduitIdAndUtilisateurId(produit.getId(), client.getId())).isTrue();
        assertThatThrownBy(() -> avisDepot.saveAndFlush(new Avis(produit, client, "Awa D.", 2)))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_avis_produit_utilisateur"));
    }

    @Test
    void lAvisResteApresLaSuppressionDuCompte() {
        Produit produit = FabriqueCatalogue.produit(em);
        em.persist(produit);
        Utilisateur client = FabriqueComptes.utilisateur(em);
        Avis avis = avisDepot.saveAndFlush(new Avis(produit, client, "Awa D.", 4));

        em.createNativeQuery("delete from utilisateur where id = :id").setParameter("id", client.getId()).executeUpdate();
        em.clear();

        Avis relu = avisDepot.findById(avis.getId()).orElseThrow();
        assertThat(relu.getUtilisateur()).isNull();
        assertThat(relu.getNomAuteur()).isEqualTo("Awa D.");
    }

    @Test
    void uneVarianteNApparaitQuUneFoisDansLaListeDeSouhaits() {
        Utilisateur client = FabriqueComptes.utilisateur(em);
        VarianteProduit variante = variante();
        em.persist(new ElementListeSouhaits(client, variante));

        assertThatThrownBy(() -> {
            em.persist(new ElementListeSouhaits(client, variante));
            em.flush();
        }).satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_liste_souhaits"));
    }

    @Test
    void uneSeuleAlerteEnAttenteParEmailEtVarianteMaisOnPeutSeReabonner() {
        VarianteProduit variante = variante();
        AlerteProduit premiere = new AlerteProduit("Client@Exemple.SN", variante, TypeAlerte.RETOUR_EN_STOCK);
        alerteDepot.saveAndFlush(premiere);
        assertThat(premiere.getEmail()).isEqualTo("client@exemple.sn");

        AlerteProduit doublon = new AlerteProduit("client@exemple.sn", variante, TypeAlerte.RETOUR_EN_STOCK);
        assertThatThrownBy(() -> alerteDepot.saveAndFlush(doublon))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_alerte_en_attente"));
    }

    @Test
    void uneAlerteEnvoyeeLibereLaPlace() {
        VarianteProduit variante = variante();
        AlerteProduit premiere = new AlerteProduit("client@exemple.sn", variante, TypeAlerte.RETOUR_EN_STOCK);
        alerteDepot.saveAndFlush(premiere);
        premiere.marquerEnvoyee(java.time.Instant.now());
        alerteDepot.saveAndFlush(premiere);

        alerteDepot.saveAndFlush(new AlerteProduit("client@exemple.sn", variante, TypeAlerte.RETOUR_EN_STOCK));
        assertThat(alerteDepot.findByVarianteIdAndTypeAndEnvoyeeFalse(variante.getId(), TypeAlerte.RETOUR_EN_STOCK))
                .hasSize(1);
    }

    @Test
    void uneAlerteDeBaisseDePrixExigeUnPrixCible() {
        AlerteProduit sansPrix = new AlerteProduit("client@exemple.sn", variante(), TypeAlerte.BAISSE_DE_PRIX);

        assertThatThrownBy(() -> alerteDepot.saveAndFlush(sansPrix))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_alerte_prix_cible"));
    }

    @Test
    void uneDemandeSavGardeSonFilDeMessages() {
        VarianteProduit variante = variante();
        Commande commande = commandeAvec(variante);
        DemandeSav demande = new DemandeSav(commande, MotifSav.DEFECTUEUX, "L'écran clignote au démarrage.");
        demande.setLigneCommande(commande.getLignes().getFirst());
        demande.ajouterMessage(null, "Awa D.", false, false, "Photos jointes.");
        demande.ajouterMessage(null, "Service client", true, true, "Vérifier le numéro de série.");
        demandeSavDepot.saveAndFlush(demande);
        em.clear();

        DemandeSav relue = demandeSavDepot.findByCommandeIdOrderByDateCreationDesc(commande.getId()).getFirst();
        assertThat(relue.getMessages()).hasSize(2);
        assertThat(relue.getMessages().get(1).isNoteInterne()).isTrue();
    }

    @Test
    void uneDemandeSavNePeutPasViserLaLigneDUneAutreCommande() {
        VarianteProduit variante = variante();
        Commande premiere = commandeAvec(variante);
        Commande autre = commandeAvec(variante);
        LigneCommande ligneDeLAutre = autre.getLignes().getFirst();
        DemandeSav demande = new DemandeSav(premiere, MotifSav.GARANTIE, "Panne de batterie.");
        demande.setLigneCommande(ligneDeLAutre);

        assertThatThrownBy(() -> demandeSavDepot.saveAndFlush(demande))
                .hasStackTraceContaining("n'appartient pas à la commande");
    }

    @Test
    void unClientNePeutPasEcrireDeNoteInterne() {
        Commande commande = commandeAvec(variante());
        DemandeSav demande = new DemandeSav(commande, MotifSav.NON_CONFORME, "Mauvais clavier (QWERTY).");
        demande.ajouterMessage(null, "Awa D.", false, true, "…");

        assertThatThrownBy(() -> demandeSavDepot.saveAndFlush(demande))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_message_sav_note_interne"));
    }

    @Test
    void lesMouvementsDeStockSuiventLeurSensEtSontImmuables() {
        VarianteProduit variante = variante();
        Commande commande = commandeAvec(variante);
        mouvementDepot.saveAndFlush(
                new MouvementStock(variante, 10, 15, TypeMouvementStock.ENTREE, "Arrivage", null, "stock@exemple.sn"));
        mouvementDepot.saveAndFlush(
                new MouvementStock(variante, -1, 14, TypeMouvementStock.SORTIE_VENTE, null, commande, "SYSTEME"));

        // Une vente sans commande est refusée
        MouvementStock venteSansCommande =
                new MouvementStock(variante, -1, 13, TypeMouvementStock.SORTIE_VENTE, null, null, "SYSTEME");
        assertThatThrownBy(() -> mouvementDepot.saveAndFlush(venteSansCommande))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_mouvement_stock_sens"));
    }

    @Test
    void unMouvementDeStockNeSeModifiePas() {
        VarianteProduit variante = variante();
        MouvementStock entree = mouvementDepot.saveAndFlush(
                new MouvementStock(variante, 10, 15, TypeMouvementStock.ENTREE, "Arrivage", null, "stock@exemple.sn"));

        assertThatThrownBy(() -> em.createNativeQuery("update mouvement_stock set quantite = 20 where id = :id")
                .setParameter("id", entree.getId()).executeUpdate())
                .hasStackTraceContaining("Un mouvement de stock ne se modifie pas");
    }
}
