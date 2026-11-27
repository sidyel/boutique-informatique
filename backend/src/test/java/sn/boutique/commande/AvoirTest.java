package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.facture.AvoirDepot;
import sn.boutique.commande.facture.AvoirDepot.AvoirArchive;
import sn.boutique.commande.facture.FactureDepot;
import sn.boutique.commande.facture.FactureDepot.FactureArchivee;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;
import sn.boutique.compte.modele.Role;

/** Avoirs : total, partiel, cumul plafonné à la facture, identité figée, accès, concurrence. */
class AvoirTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 8_000_000);

    @Autowired
    ConfirmationPaiementService confirmation;
    @Autowired
    FactureDepot factures;
    @Autowired
    AvoirDepot avoirs;

    private static String telephone() {
        return "75%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    /** Commande payée en ligne, donc facturée ; renvoie son numéro. */
    private String commandePayee(VarianteProduit variante, int quantite, long total) throws Exception {
        String jeton = panierAvec(variante, quantite);
        String numero = corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", total))
                .andExpect(status().isCreated())).path("numero").asString();
        long paiementId = jdbc.sql("select p.id from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n order by p.id desc limit 1").param("n", numero).query(Long.class).single();
        fournisseur.repondreALaVerification(jdbc.sql("select reference_externe from paiement where id = :i")
                .param("i", paiementId).query(String.class).single(), Statut.REUSSI);
        confirmation.confirmer(paiementId);
        return numero;
    }

    private FactureArchivee facture(String numeroCommande) {
        return factures.trouverParCommande(jdbc.sql("select id from commande where numero = :n")
                .param("n", numeroCommande).query(Long.class).single()).orElseThrow();
    }

    private ResultActions avoir(String numeroFacture, String acces, String corps) throws Exception {
        return envoyer(post("/api/admin/factures/{n}/avoirs", numeroFacture), null, acces, corps);
    }

    private static String texte(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void unAvoirTotalCreditePartoutNumeroteLaPieceEtRembourseLaCommande() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        String numero = commandePayee(variante(10_000, 10), 1, 12_000);
        FactureArchivee facture = facture(numero);

        avoir(facture.numero(), admin, """
                {"motif":"Commande annulée par le client","total":true}
                """).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.numero").value(org.hamcrest.Matchers.matchesPattern("AV-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.totalTtc").value(12_000));

        AvoirArchive a = avoirs.parFacture(facture.id()).getFirst();
        assertThat(a.totalHt() + a.totalTva()).isEqualTo(12_000);
        assertThat(a.emisPar()).isNotBlank();
        assertThat(statut(numero)).isEqualTo("REMBOURSEE");
        String texte = texte(avoirs.pdf(a.numero()).orElseThrow());
        assertThat(texte).contains("AVOIR", a.numero(), "Avoir sur la facture " + facture.numero(),
                "Motif : Commande annulée par le client");
        // Tout est crédité : un second avoir est refusé
        avoir(facture.numero(), admin, """
                {"motif":"Doublon","total":true}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("AVOIR_VIDE"));
        assertThat(avoirs.parFacture(facture.id())).hasSize(1);
    }

    @Test
    void desAvoirsPartielsSeCumulentSansJamaisDepasserLaFactureEtLeDernierRepriendLeReste() throws Exception {
        reglerRegimeAssujetti();
        String admin = acces(utilisateur(Role.ADMIN));
        // 3 × 3 333 FCFA = 9 999 + livraison 2 000 = 11 999 : des montants qui ne se divisent pas
        String numero = commandePayee(variante(3_333, 10), 3, 11_999);
        FactureArchivee facture = facture(numero);
        var lignes = factures.lignes(facture.id());
        assertThat(lignes).hasSize(2);

        for (int i = 0; i < 3; i++) {
            avoir(facture.numero(), admin, """
                    {"motif":"Retour d'une unité","lignes":[{"rang":1,"quantite":1}]}
                    """).andExpect(status().isCreated());
        }
        // Les trois unités créditées reprennent exactement la ligne
        List<AvoirArchive> faits = avoirs.parFacture(facture.id());
        assertThat(faits.stream().mapToLong(AvoirArchive::totalTtc).sum()).isEqualTo(lignes.get(0).totalTtc());
        assertThat(faits.stream().mapToLong(AvoirArchive::totalTva).sum()).isEqualTo(lignes.get(0).montantTva());
        assertThat(faits).allSatisfy(f -> assertThat(f.totalHt() + f.totalTva()).isEqualTo(f.totalTtc()));
        // Quatrième unité : refusée
        avoir(facture.numero(), admin, """
                {"motif":"Trop","lignes":[{"rang":1,"quantite":1}]}
                """).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUANTITE_AU_DESSUS_DU_RESTANT"));
        // La commande n'est pas encore remboursée : la livraison n'est pas créditée
        assertThat(statut(numero)).isEqualTo("PAYEE");

        avoir(facture.numero(), admin, """
                {"motif":"Livraison remboursée","lignes":[{"rang":2,"quantite":1}]}
                """).andExpect(status().isCreated());
        assertThat(statut(numero)).isEqualTo("REMBOURSEE");
        List<AvoirArchive> tous = avoirs.parFacture(facture.id());
        assertThat(tous.stream().mapToLong(AvoirArchive::totalTtc).sum()).isEqualTo(facture.totalTtc());
        assertThat(tous.stream().mapToLong(AvoirArchive::totalTva).sum()).isEqualTo(facture.totalTva());
        // Numéros consécutifs
        List<Long> suffixes = tous.stream().map(f -> Long.parseLong(f.numero().substring(8))).toList();
        for (int i = 1; i < suffixes.size(); i++) {
            assertThat(suffixes.get(i)).isEqualTo(suffixes.get(i - 1) + 1);
        }
    }

    @Test
    void unAvoirReprendLIdentiteFigeeDeLaFactureEtNonCelleDuSiteActuel() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        envoyer(put("/api/admin/parametres/facturation"), null, admin, """
                {"raisonSociale":"Ancienne Boutique SARL","ninea":"AAA111","regimeTva":"NON_APPLICABLE",
                 "tauxTvaBp":1800,"mentionTva":"TVA non applicable"}
                """).andExpect(status().isOk());
        String numero = commandePayee(variante(10_000, 10), 1, 12_000);
        FactureArchivee facture = facture(numero);
        envoyer(put("/api/admin/parametres/facturation"), null, admin, """
                {"raisonSociale":"Nouvelle Boutique SA","ninea":"BBB222","regimeTva":"ASSUJETTI",
                 "tauxTvaBp":1800,"mentionTva":"sans objet"}
                """).andExpect(status().isOk());

        avoir(facture.numero(), admin, """
                {"motif":"Retour","total":true}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.totalTva").value(0));

        String texte = texte(avoirs.pdf(avoirs.parFacture(facture.id()).getFirst().numero()).orElseThrow());
        assertThat(texte).contains("Ancienne Boutique SARL", "AAA111", "TVA non applicable")
                .doesNotContain("Nouvelle Boutique", "BBB222");
    }

    @Test
    void seulLAdministrationEmetUnAvoirLesRolesInternesLisent() throws Exception {
        String numero = commandePayee(variante(10_000, 10), 1, 12_000);
        String facture = facture(numero).numero();

        avoir(facture, acces(utilisateur(Role.SERVICE_CLIENT)), """
                {"motif":"x","total":true}
                """).andExpect(status().isForbidden());
        avoir(facture, acces(utilisateur(Role.CLIENT)), """
                {"motif":"x","total":true}
                """).andExpect(status().isForbidden());
        avoir(facture, "", """
                {"motif":"x","total":true}
                """).andExpect(status().isUnauthorized());
        avoir(facture, acces(utilisateur(Role.SUPER_ADMIN)), """
                {"motif":"Retour","total":true}
                """).andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/factures/{n}/avoirs", facture)
                        .header(HttpHeaders.AUTHORIZATION, acces(utilisateur(Role.SERVICE_CLIENT))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void lesRequetesInvalidesSontRefusees() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        String facture = facture(commandePayee(variante(10_000, 10), 1, 12_000)).numero();

        avoir(facture, admin, """
                {"motif":" ","total":true}
                """).andExpect(status().isBadRequest());
        avoir(facture, admin, """
                {"motif":"x","total":false}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("AVOIR_VIDE"));
        avoir(facture, admin, """
                {"motif":"x","total":true,"lignes":[{"rang":1,"quantite":1}]}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("AVOIR_AMBIGU"));
        avoir(facture, admin, """
                {"motif":"x","lignes":[{"rang":9,"quantite":1}]}
                """).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("LIGNE_INCONNUE"));
        avoir(facture, admin, """
                {"motif":"x","lignes":[{"rang":1,"quantite":0}]}
                """).andExpect(status().isBadRequest());
        avoir("FAC-0000-000000", admin, """
                {"motif":"x","total":true}
                """).andExpect(status().isNotFound());
    }

    @Test
    void deuxAvoirsTotauxSimultanesNEnDonnentQuUn() throws Exception {
        String admin = acces(utilisateur(Role.ADMIN));
        String facture = facture(commandePayee(variante(10_000, 10), 1, 12_000)).numero();
        Callable<Integer> action = () -> avoir(facture, admin, """
                {"motif":"Retour","total":true}
                """).andReturn().getResponse().getStatus();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> futurs = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futurs.add(pool.submit(() -> {
                    depart.await();
                    return action.call();
                }));
            }
            depart.countDown();
            List<Integer> statuts = new ArrayList<>();
            for (Future<Integer> futur : futurs) {
                statuts.add(futur.get());
            }
            assertThat(statuts).containsExactlyInAnyOrder(201, 422);
        } finally {
            pool.shutdownNow();
        }
    }

    private void reglerRegimeAssujetti() {
        jdbc.sql("update parametres_site set facture_regime_tva = 'ASSUJETTI', facture_taux_tva_bp = 1800 where id = 1")
                .update();
    }
}
