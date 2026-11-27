package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.facture.FactureDepot;
import sn.boutique.commande.facture.FactureDepot.FactureArchivee;
import sn.boutique.commande.facture.FactureDepot.LigneArchivee;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;
import sn.boutique.compte.modele.Role;
import sn.boutique.marketing.modele.TypeCodePromo;

/**
 * Émission des factures (paiement en ligne confirmé, livraison avec encaissement), immuabilité des pièces émises,
 * cohérence des montants et de la TVA, écart d'encaissement.
 */
class FacturationTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 7_000_000);

    @Autowired
    ConfirmationPaiementService confirmation;
    @Autowired
    FactureDepot factures;

    private static String telephone() {
        return "76%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    private String commande(VarianteProduit variante, int quantite, String mode, long total) throws Exception {
        String jeton = panierAvec(variante, quantite);
        return corps(commander(jeton, null, cle(), requete(telephone(), mode, total)).andExpect(status().isCreated()))
                .path("numero").asString();
    }

    private void payerEnLigne(String numero) throws Exception {
        long paiementId = jdbc.sql("select p.id from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n order by p.id desc limit 1").param("n", numero).query(Long.class).single();
        String reference = jdbc.sql("select reference_externe from paiement where id = :i").param("i", paiementId)
                .query(String.class).single();
        fournisseur.repondreALaVerification(reference, Statut.REUSSI);
        confirmation.confirmer(paiementId);
    }

    @Test
    void unNomWolofOuArabeNeFaitJamaisEchouerLePaiementNiLaFacture() throws Exception {
        for (String nom : new String[]{"Ibrahima Ŋgom Sène", "محمد الأمين", "Ñaare 😀 Ɓa"}) {
            VarianteProduit variante = variante(10_000, 10);
            String jeton = panierAvec(variante, 1);
            String numero = corps(commander(jeton, null, cle(),
                    requete(telephone(), "MOBILE_MONEY", 12_000, nom, "client@exemple.sn"))
                    .andExpect(status().isCreated())).path("numero").asString();

            payerEnLigne(numero);

            assertThat(jdbc.sql("select statut from commande where numero = :n").param("n", numero)
                    .query(String.class).single()).isEqualTo("PAYEE");
            FactureArchivee f = facture(numero);
            // Le nom exact est conservé tel quel dans la facture archivée ; seul le PDF le simplifie
            assertThat(f.clientNom()).isEqualTo(nom);
            assertThat(texte(factures.pdf(f.numero()).orElseThrow())).contains("CLIENT");
        }
    }

    private long idCommande(String numero) {
        return jdbc.sql("select id from commande where numero = :n").param("n", numero).query(Long.class).single();
    }

    private FactureArchivee facture(String numero) {
        return factures.trouverParCommande(idCommande(numero)).orElseThrow();
    }

    private ResultActions livrer(String numero, String acces, String corps) throws Exception {
        return envoyer(post("/api/admin/commandes/{numero}/livrer", numero), null, acces, corps);
    }

    private void reglerSite(String sql) {
        jdbc.sql(sql).update();
    }

    private static String texte(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    // ------------------------------------------------------------------ paiement en ligne

    @Test
    void unPaiementEnLigneConfirmeEmetLaFactureDansLaMemeTransaction() throws Exception {
        VarianteProduit variante = variante(10_000, 10);
        String numero = commande(variante, 2, "MOBILE_MONEY", 22_000);

        payerEnLigne(numero);

        assertThat(statut(numero)).isEqualTo("PAYEE");
        FactureArchivee f = facture(numero);
        assertThat(f.numero()).matches("FAC-\\d{4}-\\d{6}");
        assertThat(f.totalTtc()).isEqualTo(22_000);
        assertThat(f.totalHt() + f.totalTva()).isEqualTo(22_000);
        List<LigneArchivee> lignes = factures.lignes(f.id());
        assertThat(lignes).extracting(LigneArchivee::designation).anyMatch(d -> d.contains("Produit"))
                .anyMatch(d -> d.startsWith("Livraison"));
        assertThat(lignes.stream().mapToLong(LigneArchivee::totalTtc).sum()).isEqualTo(22_000);
        byte[] pdf = factures.pdf(f.numero()).orElseThrow();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texte(pdf)).contains(f.numero(), numero);
        // Une seconde confirmation (notification reçue deux fois) n'émet pas de seconde facture
        confirmation.confirmer(jdbc.sql("select p.id from paiement p where p.commande_id = :c order by p.id desc limit 1")
                .param("c", idCommande(numero)).query(Long.class).single());
        assertThat(compter("select count(*) from facture where commande_id = ?", idCommande(numero))).isEqualTo(1);
    }

    @Test
    void unPaiementNonConfirmeNEmetAucuneFacture() throws Exception {
        String numero = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);

        assertThat(compter("select count(*) from facture where commande_id = ?", idCommande(numero))).isZero();
    }

    // ------------------------------------------------------------------ immuabilité

    @Test
    void modifierLesParametresDuSiteApresLEmissionNeChangeJamaisLaFactureEmise() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        envoyer(put("/api/admin/parametres/facturation"), null, admin, """
                {"raisonSociale":"Ancienne Boutique SARL","ninea":"AAA111","rccm":"RCCM-ANCIEN",
                 "adresseSiege":"Rue de l'Ancien Siège, Dakar","regimeTva":"NON_APPLICABLE","tauxTvaBp":1800,
                 "mentionTva":"TVA non applicable"}
                """).andExpect(status().isOk());
        String numeroA = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);
        payerEnLigne(numeroA);
        FactureArchivee avant = facture(numeroA);
        byte[] pdfAvant = factures.pdf(avant.numero()).orElseThrow();
        List<LigneArchivee> lignesAvant = factures.lignes(avant.id());

        // L'entreprise change de nom, d'adresse, de numéros et de régime de TVA
        envoyer(put("/api/admin/parametres/facturation"), null, admin, """
                {"raisonSociale":"Nouvelle Boutique SA","ninea":"BBB222","rccm":"RCCM-NOUVEAU",
                 "adresseSiege":"Avenue du Nouveau Siège, Thiès","regimeTva":"ASSUJETTI","tauxTvaBp":1800,
                 "mentionTva":"sans objet"}
                """).andExpect(status().isOk());

        // La facture déjà émise est strictement identique, octet pour octet
        FactureArchivee apres = facture(numeroA);
        assertThat(apres).isEqualTo(avant);
        assertThat(factures.pdf(avant.numero()).orElseThrow()).isEqualTo(pdfAvant);
        assertThat(factures.lignes(avant.id())).isEqualTo(lignesAvant);
        assertThat(apres.vendeurRaisonSociale()).isEqualTo("Ancienne Boutique SARL");
        assertThat(apres.regimeTva().name()).isEqualTo("NON_APPLICABLE");
        assertThat(texte(pdfAvant)).contains("Ancienne Boutique SARL", "AAA111", "TVA non applicable")
                .doesNotContain("Nouvelle Boutique");

        // Une facture émise après le changement porte, elle, les nouvelles mentions
        String numeroB = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);
        payerEnLigne(numeroB);
        FactureArchivee nouvelle = facture(numeroB);
        assertThat(nouvelle.vendeurRaisonSociale()).isEqualTo("Nouvelle Boutique SA");
        assertThat(nouvelle.regimeTva().name()).isEqualTo("ASSUJETTI");
        assertThat(nouvelle.totalTva()).isEqualTo(1_831); // 12 000 × 18 / 118 = 1 830,5 → 1 831
        assertThat(nouvelle.totalHt()).isEqualTo(10_169);
    }

    @Test
    void laBaseRefuseToutChangementDUneFactureEmise() throws Exception {
        String numero = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);
        payerEnLigne(numero);
        long id = facture(numero).id();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.sql("update facture set total_ttc = 1, total_ht = 1 where id = :i").param("i", id).update())
                .hasMessageContaining("immuable");
    }

    // ------------------------------------------------------------------ montants et TVA

    @Test
    void laRemiseDuCodePromoEstRepartieEtLaTvaDesLignesSommeAuTotal() throws Exception {
        reglerSite("update parametres_site set facture_regime_tva = 'ASSUJETTI', facture_taux_tva_bp = 1800 where id = 1");
        VarianteProduit a = variante(33_333, 10);
        VarianteProduit b = variante(10_001, 10);
        String code = code(TypeCodePromo.MONTANT_FIXE, 5_000, c -> { });
        String jeton = panierAvec(a, 1);
        ajouterAuPanier(jeton, b, 3).andExpect(status().is2xxSuccessful());
        envoyer(put("/api/panier/code-promo"), jeton, null, "{\"code\":\"%s\"}".formatted(code));
        long total = totalDuPanier(jeton) + 2_000;
        String numero = corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", total))
                .andExpect(status().isCreated())).path("numero").asString();
        payerEnLigne(numero);

        FactureArchivee f = facture(numero);
        List<LigneArchivee> lignes = factures.lignes(f.id());
        assertThat(f.totalTtc()).isEqualTo(total);
        assertThat(lignes.stream().mapToLong(LigneArchivee::totalTtc).sum()).isEqualTo(total);
        assertThat(lignes.stream().mapToLong(LigneArchivee::montantTva).sum()).isEqualTo(f.totalTva());
        assertThat(lignes.stream().mapToLong(LigneArchivee::totalHt).sum()).isEqualTo(f.totalHt());
        assertThat(lignes).allSatisfy(l -> {
            assertThat(l.totalHt() + l.montantTva()).isEqualTo(l.totalTtc());
            assertThat(l.totalBrutTtc() - l.remiseTtc()).isEqualTo(l.totalTtc());
        });
        assertThat(lignes.stream().mapToLong(LigneArchivee::remiseTtc).sum()).isEqualTo(5_000);
        // 1 ligne article à 33 333 + 3 × 10 001 + livraison 2 000, moins 5 000
        assertThat(f.totalTva()).isEqualTo(Math.round(total * 18.0 / 118.0));
    }

    // ------------------------------------------------------------------ livraison et encaissement

    @Test
    void laLivraisonEncaisseLeMontantEtEmetLaFacture() throws Exception {
        String numero = commande(variante(10_000, 10), 1, "A_LA_LIVRAISON", 12_000);
        var agent = utilisateur(Role.SERVICE_CLIENT);
        envoyer(post("/api/admin/commandes/{n}/confirmer", numero), null, acces(agent), null).andExpect(status().isOk());

        livrer(numero, acces(agent), """
                {"montantRecu":12000,"moyen":"WAVE_LIVREUR","encaissePar":"Moussa, livreur"}
                """).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.numeroFacture").value(org.hamcrest.Matchers.startsWith("FAC-")))
                .andExpect(jsonPath("$.ecartEncaissement").value(0));

        assertThat(statut(numero)).isEqualTo("LIVREE");
        FactureArchivee f = facture(numero);
        assertThat(f.totalTtc()).isEqualTo(12_000);
        assertThat(f.modePaiement()).isEqualTo("A_LA_LIVRAISON");
        assertThat(texte(factures.pdf(f.numero()).orElseThrow())).contains("Wave remis au livreur");
        var encaissement = jdbc.sql("select montant, moyen, encaisse_par_nom, motif_ecart, encaisse_par_id "
                + "from encaissement where commande_id = :c").param("c", idCommande(numero)).query().singleRow();
        assertThat(encaissement.get("montant")).isEqualTo(12_000L);
        assertThat(encaissement.get("moyen")).isEqualTo("WAVE_LIVREUR");
        assertThat(encaissement.get("encaisse_par_nom")).isEqualTo("Moussa, livreur");
        assertThat(encaissement.get("motif_ecart")).isNull();
        assertThat(encaissement.get("encaisse_par_id")).isEqualTo(agent.getId());
        assertThat(jdbc.sql("select ecart_encaissement from commande where numero = :n").param("n", numero)
                .query(Long.class).single()).isZero();
        // Livrer deux fois est refusé et n'émet rien de plus
        livrer(numero, acces(agent), """
                {"montantRecu":12000,"moyen":"ESPECES"}
                """).andExpect(status().isConflict());
        assertThat(compter("select count(*) from encaissement where commande_id = ?", idCommande(numero))).isEqualTo(1);
    }

    @Test
    void unMontantInferieurExigeUnMotifPuisMarqueUnEcartAvecAlerte() throws Exception {
        String numero = commande(variante(10_000, 10), 1, "A_LA_LIVRAISON", 12_000);
        String agent = acces(utilisateur(Role.SERVICE_CLIENT));
        envoyer(post("/api/admin/commandes/{n}/confirmer", numero), null, agent, null).andExpect(status().isOk());

        livrer(numero, agent, """
                {"montantRecu":11500,"moyen":"ESPECES"}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MOTIF_ECART_REQUIS"));
        livrer(numero, agent, """
                {"montantRecu":11500,"moyen":"ESPECES","motifEcart":"AUTRE"}
                """).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOTIF_ECART_A_PRECISER"));
        assertThat(statut(numero)).isEqualTo("CONFIRMEE");
        assertThat(compter("select count(*) from facture where commande_id = ?", idCommande(numero))).isZero();

        livrer(numero, agent, """
                {"montantRecu":11500,"moyen":"ESPECES","motifEcart":"MONNAIE_MANQUANTE"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.ecartEncaissement").value(500));

        // La facture est émise pour le total de la commande ; l'encaissement réel est enregistré tel quel
        assertThat(facture(numero).totalTtc()).isEqualTo(12_000);
        assertThat(jdbc.sql("select montant from encaissement where commande_id = :c").param("c", idCommande(numero))
                .query(Long.class).single()).isEqualTo(11_500);
        assertThat(jdbc.sql("select ecart_encaissement from commande where numero = :n").param("n", numero)
                .query(Long.class).single()).isEqualTo(500);
        assertThat(jdbc.sql("select count(*) from commande where ecart_encaissement > 0 and ecart_regularise_le is null "
                + "and numero = :n").param("n", numero).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void unMontantSuperieurAuTotalOuUnMoyenAutreSansPrecisionSontRefuses() throws Exception {
        String numero = commande(variante(10_000, 10), 1, "A_LA_LIVRAISON", 12_000);
        String agent = acces(utilisateur(Role.SERVICE_CLIENT));
        envoyer(post("/api/admin/commandes/{n}/confirmer", numero), null, agent, null).andExpect(status().isOk());

        livrer(numero, agent, """
                {"montantRecu":12001,"moyen":"ESPECES"}
                """).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MONTANT_SUPERIEUR_AU_TOTAL"));
        livrer(numero, agent, """
                {"montantRecu":12000,"moyen":"AUTRE"}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MOYEN_A_PRECISER"));
        livrer(numero, agent, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ENCAISSEMENT_REQUIS"));
        livrer(numero, agent, """
                {"montantRecu":-5,"moyen":"ESPECES"}
                """).andExpect(status().isUnprocessableContent());

        assertThat(statut(numero)).isEqualTo("CONFIRMEE");
        assertThat(compter("select count(*) from encaissement where commande_id = ?", idCommande(numero))).isZero();
    }

    @Test
    void uneCommandeNonConfirmeeOuDejaPayeeEnLigneSeLivreSansNouvelEncaissement() throws Exception {
        String agent = acces(utilisateur(Role.SERVICE_CLIENT));
        // À la livraison mais pas encore confirmée par téléphone : refusée
        String attente = commande(variante(10_000, 10), 1, "A_LA_LIVRAISON", 12_000);
        livrer(attente, agent, """
                {"montantRecu":12000,"moyen":"ESPECES"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATUT_INVALIDE"));

        // Payée en ligne : livrable sans corps, la facture existe déjà (émise au paiement) et reste la seule
        String enLigne = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);
        payerEnLigne(enLigne);
        String numeroFacture = facture(enLigne).numero();
        livrer(enLigne, agent, """
                {"montantRecu":12000,"moyen":"ESPECES"}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("DEJA_PAYEE_EN_LIGNE"));
        livrer(enLigne, agent, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroFacture").value(numeroFacture));
        assertThat(statut(enLigne)).isEqualTo("LIVREE");
        assertThat(compter("select count(*) from facture where commande_id = ?", idCommande(enLigne))).isEqualTo(1);
    }

    @Test
    void seulsLesRolesInternesPeuventLivrer() throws Exception {
        String numero = commande(variante(10_000, 10), 1, "A_LA_LIVRAISON", 12_000);
        livrer(numero, acces(utilisateur(Role.CLIENT)), null).andExpect(status().isForbidden());
        livrer(numero, "", null).andExpect(status().isUnauthorized());
    }

    @Test
    void lePremierNumeroDeFactureSuitLeDernierSansTrou() throws Exception {
        String premier = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);
        String second = commande(variante(10_000, 10), 1, "MOBILE_MONEY", 12_000);
        payerEnLigne(premier);
        payerEnLigne(second);

        long a = Long.parseLong(facture(premier).numero().substring(9));
        long b = Long.parseLong(facture(second).numero().substring(9));
        assertThat(b).isEqualTo(a + 1);
    }
}
