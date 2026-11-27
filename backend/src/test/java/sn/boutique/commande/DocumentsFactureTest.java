package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.facture.FactureDepot;
import sn.boutique.commande.paiement.ConfirmationPaiementService;
import sn.boutique.commande.paiement.FournisseurPaiement.Statut;
import sn.boutique.compte.modele.Role;
import sn.boutique.support.ClientMailpit;
import tools.jackson.databind.JsonNode;

/**
 * Accès aux factures et avoirs (lien de suivi, compte client, personnel) et e-mails multipart avec pièce jointe.
 */
class DocumentsFactureTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 9_000_000);

    @Autowired
    ConfirmationPaiementService confirmation;
    @Autowired
    FactureDepot factures;
    @Value("${test.mailpit.url}")
    String urlMailpit;

    private static String telephone() {
        return "70%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    private void payer(String numero) throws Exception {
        long paiementId = jdbc.sql("select p.id from paiement p join commande c on c.id = p.commande_id "
                + "where c.numero = :n order by p.id desc limit 1").param("n", numero).query(Long.class).single();
        fournisseur.repondreALaVerification(jdbc.sql("select reference_externe from paiement where id = :i")
                .param("i", paiementId).query(String.class).single(), Statut.REUSSI);
        confirmation.confirmer(paiementId);
    }

    /** Commande en ligne passée sans compte : {numero, jeton}. */
    private JsonNode commandeInvite(String email) throws Exception {
        String jeton = panierAvec(variante(10_000, 10), 1);
        return corps(commander(jeton, null, cle(), requete(telephone(), "MOBILE_MONEY", 12_000, "Awa Diop", email))
                .andExpect(status().isCreated()));
    }

    private String numeroFacture(String numeroCommande) {
        return factures.trouverParCommande(jdbc.sql("select id from commande where numero = :n")
                .param("n", numeroCommande).query(Long.class).single()).orElseThrow().numero();
    }

    private void avoirTotal(String numeroFacture) throws Exception {
        envoyer(post("/api/admin/factures/{n}/avoirs", numeroFacture), null, acces(utilisateur(Role.ADMIN)),
                "{\"motif\":\"Retour d'un article\",\"total\":true}").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ lien de suivi

    @Test
    void leLienDeSuiviTelechargeLaFactureMemeSansEmail() throws Exception {
        JsonNode commande = commandeInvite(null);
        String suivi = commande.path("jetonSuivi").asString();
        String numero = commande.path("numero").asString();

        // Pas encore payée : pas de facture, rien à télécharger
        mockMvc.perform(get("/api/commandes/suivi/{j}", suivi)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroFacture").doesNotExist());
        mockMvc.perform(get("/api/commandes/suivi/{j}/facture", suivi)).andExpect(status().isNotFound());

        payer(numero);
        String facture = numeroFacture(numero);

        mockMvc.perform(get("/api/commandes/suivi/{j}", suivi)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroFacture").value(facture))
                .andExpect(jsonPath("$.numerosAvoirs.length()").value(0));
        byte[] pdf = mockMvc.perform(get("/api/commandes/suivi/{j}/facture", suivi))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"" + facture + ".pdf\"")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf).isEqualTo(factures.pdf(facture).orElseThrow());
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void unLienInconnuOuMalFormeDonneToujoursLaMemeErreur404() throws Exception {
        mockMvc.perform(get("/api/commandes/suivi/{j}/facture", "x".repeat(43))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/commandes/suivi/{j}/facture", "court")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/commandes/suivi/{j}/avoirs/AV-2026-000001", "x".repeat(43)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unAvoirSeTelechargeParLeLienMaisSeulSiIlPorteSurLaFactureDeLaCommande() throws Exception {
        JsonNode a = commandeInvite(null);
        JsonNode b = commandeInvite(null);
        payer(a.path("numero").asString());
        payer(b.path("numero").asString());
        avoirTotal(numeroFacture(a.path("numero").asString()));
        String numeroAvoir = jdbc.sql("select av.numero from avoir av join facture f on f.id = av.facture_id "
                + "join commande c on c.id = f.commande_id where c.numero = :n").param("n", a.path("numero").asString())
                .query(String.class).single();

        mockMvc.perform(get("/api/commandes/suivi/{j}", a.path("jetonSuivi").asString()))
                .andExpect(jsonPath("$.numerosAvoirs[0]").value(numeroAvoir));
        mockMvc.perform(get("/api/commandes/suivi/{j}/avoirs/{n}", a.path("jetonSuivi").asString(), numeroAvoir))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        // Le lien d'une autre commande ne donne pas accès à cet avoir
        mockMvc.perform(get("/api/commandes/suivi/{j}/avoirs/{n}", b.path("jetonSuivi").asString(), numeroAvoir))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ compte client et personnel

    @Test
    void leClientConnecteTelechargeSaFactureEtPasCelleDUnAutre() throws Exception {
        var client = utilisateur(Role.CLIENT);
        String acces = acces(client);
        VarianteProduit variante = variante(10_000, 10);
        envoyer(MockMvcRequestBuilders.post("/api/panier/lignes"), null, acces,
                "{\"varianteId\":%d,\"quantite\":1}".formatted(variante.getId())).andExpect(status().isOk());
        String numero = corps(commander(null, acces, cle(), requete(telephone(), "MOBILE_MONEY", 12_000)
                .replace("\"awa@exemple.sn\"", "null")).andExpect(status().isCreated())).path("numero").asString();
        payer(numero);
        String facture = numeroFacture(numero);

        mockMvc.perform(get("/api/compte/commandes/{n}/facture", numero).header(HttpHeaders.AUTHORIZATION, acces))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(facture + ".pdf")));
        // Un autre client, ou personne : aucun accès, sans révéler que la commande existe
        mockMvc.perform(get("/api/compte/commandes/{n}/facture", numero)
                        .header(HttpHeaders.AUTHORIZATION, acces(utilisateur(Role.CLIENT))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/compte/commandes/{n}/facture", numero)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/compte/commandes/{n}/facture", "CMD-0000-000000")
                        .header(HttpHeaders.AUTHORIZATION, acces)).andExpect(status().isNotFound());
    }

    @Test
    void leClientConnecteRecoitUnVraiPdfAvecLeBonNumeroDeFactureEtDAvoirEtUnAutreCompteAFaitUn404() throws Exception {
        var client = utilisateur(Role.CLIENT);
        String acces = acces(client);
        VarianteProduit variante = variante(10_000, 10);
        envoyer(MockMvcRequestBuilders.post("/api/panier/lignes"), null, acces,
                "{\"varianteId\":%d,\"quantite\":1}".formatted(variante.getId())).andExpect(status().isOk());
        String numero = corps(commander(null, acces, cle(), requete(telephone(), "MOBILE_MONEY", 12_000)
                .replace("\"awa@exemple.sn\"", "null")).andExpect(status().isCreated())).path("numero").asString();
        payer(numero);
        String facture = numeroFacture(numero);
        avoirTotal(facture);
        String avoir = corps(mockMvc.perform(get("/api/compte/commandes/{n}", numero)
                .header(HttpHeaders.AUTHORIZATION, acces)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroFacture").value(facture))).path("numerosAvoirs").get(0).asString();

        byte[] pdfFacture = mockMvc.perform(get("/api/compte/commandes/{n}/facture", numero)
                        .header(HttpHeaders.AUTHORIZATION, acces))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdfFacture, 0, 4)).isEqualTo("%PDF");
        assertThat(texte(pdfFacture)).contains(facture);

        byte[] pdfAvoir = mockMvc.perform(get("/api/compte/commandes/{n}/avoirs/{a}", numero, avoir)
                        .header(HttpHeaders.AUTHORIZATION, acces))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdfAvoir, 0, 4)).isEqualTo("%PDF");
        assertThat(texte(pdfAvoir)).contains(avoir).contains(facture);

        // Un autre compte : 404 partout, sans rien révéler
        String autre = acces(utilisateur(Role.CLIENT));
        mockMvc.perform(get("/api/compte/commandes/{n}/facture", numero).header(HttpHeaders.AUTHORIZATION, autre))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/compte/commandes/{n}/avoirs/{a}", numero, avoir).header(HttpHeaders.AUTHORIZATION, autre))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/compte/commandes/{n}", numero).header(HttpHeaders.AUTHORIZATION, autre))
                .andExpect(status().isNotFound());
    }

    private static String texte(byte[] pdf) throws java.io.IOException {
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
    }

    @Test
    void lePersonnelTelechargeFacturesEtAvoirsLesClientsNonConnectesPas() throws Exception {
        JsonNode commande = commandeInvite(null);
        payer(commande.path("numero").asString());
        String facture = numeroFacture(commande.path("numero").asString());
        avoirTotal(facture);
        String avoir = jdbc.sql("select numero from avoir order by id desc limit 1").query(String.class).single();
        String agent = acces(utilisateur(Role.SERVICE_CLIENT));

        mockMvc.perform(get("/api/admin/factures/{n}/pdf", facture).header(HttpHeaders.AUTHORIZATION, agent))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/api/admin/avoirs/{n}/pdf", avoir).header(HttpHeaders.AUTHORIZATION, agent))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/factures/{n}/pdf", facture)
                        .header(HttpHeaders.AUTHORIZATION, acces(utilisateur(Role.CLIENT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/factures/{n}/pdf", "FAC-0000-000000").header(HttpHeaders.AUTHORIZATION, agent))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ e-mails

    @Test
    void laFactureEtLAvoirPartentParEmailEnMultipartAvecLeurPdfEnPieceJointe() throws Exception {
        String email = "facture-" + COMPTEUR.incrementAndGet() + "@exemple.sn";
        JsonNode commande = commandeInvite(email);
        String numero = commande.path("numero").asString();
        ClientMailpit mailpit = new ClientMailpit(urlMailpit);
        // L'e-mail « Commande reçue » est déjà multipart avec une version texte contenant le lien de suivi
        ClientMailpit.Email recue = mailpit.attendre(email, 1).getFirst();
        assertThat(recue.brut()).contains("multipart/alternative", "text/plain", "text/html");
        assertThat(recue.texte()).contains(commande.path("jetonSuivi").asString()).doesNotContain("<");

        payer(numero);
        String facture = numeroFacture(numero);
        ClientMailpit.Email emailFacture = mailpit.attendre(email, 2).stream()
                .filter(e -> e.sujet().equals("Votre facture " + facture)).findFirst().orElseThrow();

        assertThat(emailFacture.expediteur()).isNotBlank();
        assertThat(emailFacture.brut()).contains("multipart/alternative", "text/plain", "text/html");
        assertThat(emailFacture.texte()).contains(facture, numero, "12 000").doesNotContain("<");
        assertThat(emailFacture.html()).contains(facture, numero);
        assertThat(emailFacture.pieces()).hasSize(1);
        var piece = emailFacture.pieces().getFirst();
        assertThat(piece.nom()).isEqualTo(facture + ".pdf");
        assertThat(piece.type()).startsWith("application/pdf");
        assertThat(piece.taille()).as("poids de la pièce jointe").isEqualTo(factures.pdf(facture).orElseThrow().length)
                .isLessThan(100 * 1024);

        avoirTotal(facture);
        ClientMailpit.Email emailAvoir = mailpit.attendre(email, 3).stream()
                .filter(e -> e.sujet().startsWith("Votre avoir AV-")).findFirst().orElseThrow();
        assertThat(emailAvoir.texte()).contains("Retour d'un article", facture);
        assertThat(emailAvoir.pieces()).hasSize(1);
        assertThat(emailAvoir.pieces().getFirst().nom()).startsWith("AV-").endsWith(".pdf");
    }
}
