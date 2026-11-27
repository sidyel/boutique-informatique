package sn.boutique.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.compte.modele.Role;
import sn.boutique.support.ClientMailpit;
import sn.boutique.support.FauxNotificationClient;
import tools.jackson.databind.JsonNode;

/**
 * Le jeton du lien de suivi n'est jamais stocké : seule son empreinte SHA-256 est en base. Pour renvoyer le lien,
 * un nouveau jeton remplace l'ancien.
 */
class JetonSuiviTest extends CommandeTestBase {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime() % 1_000_000 + 3_000_000);

    @Value("${test.mailpit.url}")
    String urlMailpit;

    private static String telephone() {
        return "75%07d".formatted(COMPTEUR.incrementAndGet() % 10_000_000);
    }

    private JsonNode commande(String tel, String email) throws Exception {
        String panier = panierAvec(variante(10_000, 10), 1);
        return corps(commander(panier, null, cle(), requete(tel, "A_LA_LIVRAISON", 12_000, "Awa Diop", email))
                .andExpect(status().isCreated()));
    }

    private void suiviAccessible(String jeton, boolean accessible) throws Exception {
        mockMvc.perform(get("/api/commandes/suivi/{jeton}", jeton))
                .andExpect(status().is(accessible ? 200 : 404));
    }

    private FauxNotificationClient.Envoi attendreEnvoi(String telephone, int rang) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var envois = notifications.envois().stream().filter(e -> e.telephone().equals(telephone)).toList();
            if (envois.size() >= rang) {
                return envois.get(rang - 1);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Notification n°" + rang + " absente pour " + telephone);
    }

    @Test
    void labaseNeGardeQueLEmpreinteDuJeton() throws Exception {
        JsonNode creee = commande(telephone(), null);
        String jeton = creee.path("jetonSuivi").asString();
        String numero = creee.path("numero").asString();

        assertThat(jdbc.sql("select empreinte_jeton_suivi from commande where numero = :n").param("n", numero)
                .query(String.class).single()).isEqualTo(GenerateurJetons.empreinte(jeton));
        // Aucune colonne de la commande ne contient le jeton, ni une colonne « jeton_suivi » en clair
        assertThat(jdbc.sql("select c::text from commande c where c.numero = :n").param("n", numero)
                .query(String.class).single()).doesNotContain(jeton);
        assertThat(compter("select count(*) from information_schema.columns where table_name = 'commande' "
                + "and column_name = 'jeton_suivi'")).isZero();
        // L'empreinte de la migration (sha256 de PostgreSQL) est celle de l'application : les liens déjà émis
        // restent valables après V19
        assertThat(jdbc.sql("select encode(sha256(convert_to(:j, 'UTF8')), 'hex')").param("j", jeton)
                .query(String.class).single()).isEqualTo(GenerateurJetons.empreinte(jeton));
        suiviAccessible(jeton, true);
    }

    @Test
    void uneRequeteRejoueeRenvoieUnNouveauLienEtInvalideLAncien() throws Exception {
        String panier = panierAvec(variante(10_000, 10), 1);
        String cle = cle();
        String tel = telephone();
        String corps = requete(tel, "A_LA_LIVRAISON", 12_000);
        JsonNode premiere = corps(commander(panier, null, cle, corps).andExpect(status().isCreated()));
        JsonNode seconde = corps(commander(panier, null, cle, corps).andExpect(status().isOk())
                .andExpect(jsonPath("$.rejouee").value(true)));

        String ancien = premiere.path("jetonSuivi").asString();
        String nouveau = seconde.path("jetonSuivi").asString();
        assertThat(nouveau).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(ancien);
        assertThat(seconde.path("numero").asString()).isEqualTo(premiere.path("numero").asString());
        suiviAccessible(ancien, false);
        suiviAccessible(nouveau, true);
        // Le client reçoit le nouveau lien : l'ancien, déjà envoyé, ne fonctionne plus
        assertThat(attendreEnvoi("+221" + tel, 2).message()).contains(nouveau);
    }

    @Test
    void leServiceClientRenvoieLeLienSansJamaisLeVoir() throws Exception {
        String tel = telephone();
        String email = "perdu-" + COMPTEUR.incrementAndGet() + "@exemple.sn";
        JsonNode creee = commande(tel, email);
        String ancien = creee.path("jetonSuivi").asString();
        String numero = creee.path("numero").asString();
        attendreEnvoi("+221" + tel, 1);
        String acces = acces(utilisateur(Role.SERVICE_CLIENT));

        String reponse = envoyer(post("/api/admin/commandes/{numero}/renvoyer-lien-suivi", numero), null, acces, null)
                .andExpect(status().isNoContent()).andReturn().getResponse().getContentAsString();
        assertThat(reponse).isEmpty();

        // Nouveau lien par message et par e-mail ; l'ancien est mort
        String message = attendreEnvoi("+221" + tel, 2).message();
        String lien = proprietes.urlSiteSansBarre() + "/suivi/";
        assertThat(message).contains(lien).contains("ne fonctionne plus");
        String nouveau = message.substring(message.indexOf(lien) + lien.length()).substring(0, 43);
        assertThat(nouveau).isNotEqualTo(ancien);
        suiviAccessible(ancien, false);
        suiviAccessible(nouveau, true);
        var emails = new ClientMailpit(urlMailpit).attendre(email, 2);
        assertThat(emails).anySatisfy(e -> {
            assertThat(e.sujet()).isEqualTo("Nouveau lien de suivi : commande " + numero);
            assertThat(e.html()).contains(nouveau);
        });
    }

    @Test
    void renvoyerLeLienEstReserveAuServiceClient() throws Exception {
        String numero = commande(telephone(), null).path("numero").asString();

        envoyer(post("/api/admin/commandes/{numero}/renvoyer-lien-suivi", numero), null, null, null)
                .andExpect(status().isUnauthorized());
        envoyer(post("/api/admin/commandes/{numero}/renvoyer-lien-suivi", numero), null,
                acces(utilisateur(Role.CLIENT)), null).andExpect(status().isForbidden());
        envoyer(post("/api/admin/commandes/{numero}/renvoyer-lien-suivi", "CMD-2026-999999"), null,
                acces(utilisateur(Role.SERVICE_CLIENT)), null).andExpect(status().isNotFound());
    }
}
