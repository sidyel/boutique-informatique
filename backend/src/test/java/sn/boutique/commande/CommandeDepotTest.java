package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.Contraintes.contrainteViolee;
import static sn.boutique.support.FabriqueCatalogue.unique;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.LigneCommandeDepot;
import sn.boutique.commande.depot.PaiementDepot;
import sn.boutique.commande.modele.AdresseLivraison;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.LigneCommande;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.NumeroCommande;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.panier.depot.PanierDepot;
import sn.boutique.panier.modele.Panier;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.TestDepot;

@TestDepot
class CommandeDepotTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CommandeDepot commandeDepot;

    @Autowired
    private LigneCommandeDepot ligneCommandeDepot;

    @Autowired
    private PaiementDepot paiementDepot;

    @Autowired
    private PanierDepot panierDepot;

    private VarianteProduit variante(long prix, int stock) {
        Produit produit = FabriqueCatalogue.produit(em);
        VarianteProduit variante = produit.ajouterVariante(unique("SKU"), "16 Go / 512 Go", prix, stock);
        em.persist(produit);
        return variante;
    }

    @Test
    void leNumeroDeCommandeVientDUneSequence() {
        long premier = commandeDepot.prochaineValeurNumero();
        long second = commandeDepot.prochaineValeurNumero();

        assertThat(second).isGreaterThan(premier);
        assertThat(NumeroCommande.formater(2026, 123)).isEqualTo("CMD-2026-000123");
        assertThat(NumeroCommande.idEvenementAchat("CMD-2026-000123")).isEqualTo("achat-CMD-2026-000123");
    }

    @Test
    void lesLignesGardentUneCopieFigeeDuProduit() {
        VarianteProduit variante = variante(450_000, 5);
        Commande commande = FabriqueComptes.commandeRetrait();
        commande.ajouterLigne(variante, "https://exemple/a15.webp", 2);
        commande.recalculerTotaux();
        commande.changerStatut(StatutCommande.CONFIRMEE, "SYSTEME", null);
        commandeDepot.saveAndFlush(commande);

        // Le prix et le nom changent après la commande
        variante.setPrix(399_000);
        variante.getProduit().setNom("TUF Gaming A15 (2026)");
        em.flush();
        em.clear();

        Commande relue = commandeDepot.findByNumero(commande.getNumero()).orElseThrow();
        LigneCommande ligne = relue.getLignes().getFirst();
        assertThat(ligne.getPrixUnitaire()).isEqualTo(450_000);
        assertThat(ligne.getNomProduit()).isEqualTo("TUF Gaming A15");
        assertThat(ligne.getTotalLigne()).isEqualTo(900_000);
        assertThat(relue.getTotal()).isEqualTo(900_000);
        assertThat(relue.getTelephoneClient()).isEqualTo("+221771234567");
        assertThat(relue.getHistorique()).singleElement()
                .satisfies(h -> {
                    assertThat(h.getAncienStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
                    assertThat(h.getNouveauStatut()).isEqualTo(StatutCommande.CONFIRMEE);
                });
    }

    @Test
    void uneVarianteCommandeeNePeutPasEtreSupprimee() {
        VarianteProduit variante = variante(450_000, 5);
        Commande commande = FabriqueComptes.commandeRetrait();
        commande.ajouterLigne(variante, null, 1);
        commande.recalculerTotaux();
        commandeDepot.saveAndFlush(commande);

        assertThat(ligneCommandeDepot.existsByVarianteId(variante.getId())).isTrue();
        assertThat(ligneCommandeDepot.produitDejaCommande(variante.getProduit().getId())).isTrue();

        assertThatThrownBy(() -> em.createNativeQuery("delete from variante_produit where id = :id")
                .setParameter("id", variante.getId())
                .executeUpdate())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("fk_ligne_commande_variante"));
    }

    @Test
    void unProduitCommandeNePeutPasEtreSupprimeMemeParCascade() {
        VarianteProduit variante = variante(450_000, 5);
        Commande commande = FabriqueComptes.commandeRetrait();
        commande.ajouterLigne(variante, null, 1);
        commande.recalculerTotaux();
        commandeDepot.saveAndFlush(commande);

        // produit → variantes est en ON DELETE CASCADE, mais la ligne de commande bloque tout
        assertThatThrownBy(() -> em.createNativeQuery("delete from produit where id = :id")
                .setParameter("id", variante.getProduit().getId())
                .executeUpdate())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("fk_ligne_commande_variante"));
    }

    @Test
    void unProduitJamaisCommandePeutEtreSupprime() {
        VarianteProduit variante = variante(99_000, 1);
        em.flush();

        assertThat(ligneCommandeDepot.produitDejaCommande(variante.getProduit().getId())).isFalse();
        int supprimes = em.createNativeQuery("delete from produit where id = :id")
                .setParameter("id", variante.getProduit().getId())
                .executeUpdate();
        assertThat(supprimes).isEqualTo(1);
    }

    @Test
    void laBaseRefuseUnTotalIncoherent() {
        Commande commande = FabriqueComptes.commandeRetrait();
        commande.ajouterLigne(variante(450_000, 5), null, 1);
        commande.recalculerTotaux();
        commande.setTotal(1);

        assertThatThrownBy(() -> commandeDepot.saveAndFlush(commande))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_commande_total"));
    }

    @Test
    void uneLivraisonADomicileExigeUneAdresseEtUneZone() {
        Commande commande = new Commande(NumeroCommande.formater(2026, 800_000 + Long.parseLong(unique("0").substring(2))),
                "client@exemple.sn", "+221 76 000 00 00", ModeLivraison.DOMICILE, ModePaiement.WAVE);

        assertThatThrownBy(() -> commandeDepot.saveAndFlush(commande))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_commande_adresse_domicile"));
    }

    @Test
    void uneLivraisonADomicileCompleteEstAcceptee() {
        ZoneLivraison zone = new ZoneLivraison(unique("Dakar Plateau"), 2_000, 1);
        zone.getVillesQuartiers().addAll(java.util.List.of("Plateau", "Médina"));
        em.persist(zone);
        Commande commande = new Commande(NumeroCommande.formater(2026, 700_000 + Long.parseLong(unique("0").substring(2))),
                "client@exemple.sn", "76 000 00 00", ModeLivraison.DOMICILE, ModePaiement.WAVE);
        commande.setAdresseLivraison(new AdresseLivraison("Awa Diop", "77 123 45 67", "Dakar", "Médina",
                "Rue 22 x 15", "En face de la boulangerie"));
        commande.setZoneLivraison(zone);
        commande.ajouterLigne(variante(450_000, 5), null, 1);
        commande.setFraisLivraison(zone.getTarif());
        commande.recalculerTotaux();
        commande.getAttribution().setUtmSource("facebook");
        commande.getAttribution().setFbclid("IwAR0abc");
        commandeDepot.saveAndFlush(commande);
        em.clear();

        Commande relue = commandeDepot.findByNumero(commande.getNumero()).orElseThrow();
        assertThat(relue.getTotal()).isEqualTo(452_000);
        assertThat(relue.getAdresseLivraison().getTelephone()).isEqualTo("+221771234567");
        assertThat(relue.getAdresseLivraison().getPointDeRepere()).isEqualTo("En face de la boulangerie");
        assertThat(relue.getAttribution().getUtmSource()).isEqualTo("facebook");
        assertThat(relue.getZoneLivraison().getVillesQuartiers()).containsExactly("Plateau", "Médina");
    }

    @Test
    void unWebhookRecuDeuxFoisNeCreePasDeuxPaiements() {
        Commande commande = FabriqueComptes.commandeRetrait();
        commande.ajouterLigne(variante(450_000, 5), null, 1);
        commande.recalculerTotaux();
        commandeDepot.saveAndFlush(commande);

        Paiement premier = new Paiement(commande, TypeFournisseurPaiement.PAYDUNYA, 450_000);
        premier.setReferenceExterne("pd-tok-123");
        premier.setDonneesBrutes("{\"status\": \"completed\", \"montant\": 450000}");
        paiementDepot.saveAndFlush(premier);
        em.clear();

        Paiement relu = paiementDepot.findByFournisseurAndReferenceExterne(TypeFournisseurPaiement.PAYDUNYA, "pd-tok-123")
                .orElseThrow();
        assertThat(relu.getDonneesBrutes()).contains("completed");
        // Le corps est stocké comme un vrai objet JSON (interrogeable), pas comme une chaîne
        Object statut = em.createNativeQuery("select donnees_brutes ->> 'status' from paiement where id = :id")
                .setParameter("id", relu.getId()).getSingleResult();
        assertThat(statut).isEqualTo("completed");

        Paiement doublon = new Paiement(em.find(Commande.class, commande.getId()), TypeFournisseurPaiement.PAYDUNYA, 450_000);
        doublon.setReferenceExterne("pd-tok-123");
        assertThatThrownBy(() -> paiementDepot.saveAndFlush(doublon))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_paiement_reference"));
    }

    @Test
    void ajouterDeuxFoisLaMemeVarianteAugmenteLaQuantite() {
        VarianteProduit variante = variante(25_000, 10);
        Panier panier = Panier.pourInvite(unique("invite"));
        panier.ajouter(variante, 1);
        panier.ajouter(variante, 2);
        panierDepot.saveAndFlush(panier);
        em.clear();

        Panier relu = panierDepot.findByJetonInvite(panier.getJetonInvite()).orElseThrow();
        assertThat(relu.getLignes()).singleElement()
                .satisfies(ligne -> assertThat(ligne.getQuantite()).isEqualTo(3));
    }

    @Test
    void unPanierAppartientAUnClientOuAUnVisiteur() {
        Panier panier = Panier.pourUtilisateur(FabriqueComptes.utilisateur(em));
        panierDepot.saveAndFlush(panier);
        em.clear();

        assertThatThrownBy(() -> em.createNativeQuery("update panier set jeton_invite = 'x' where id = :id")
                .setParameter("id", panier.getId()).executeUpdate())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_panier_proprietaire"));
    }
}
