package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.authentification.securite.Base32;
import sn.boutique.authentification.securite.Totp;
import sn.boutique.authentification.service.SessionService;
import sn.boutique.authentification.service.SessionService.Contexte;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;

/** Connexion en deux temps des comptes internes (ADR 0009 §1 à §5). */
class SecondFacteurTest extends AuthentificationTestBase {

    @Autowired
    SessionService sessionService;

    private Utilisateur compte(Role role, boolean enrole) {
        Utilisateur u = new Utilisateur(emailUnique(), encodeur.encode(MOT_DE_PASSE), "Fatou", "Sow");
        u.setRole(role);
        utilisateurDepot.save(u);
        if (enrole) {
            enroler(u);
        }
        return u;
    }

    private String jetonEtape(String email) throws Exception {
        MvcResult etape = connecter(email, MOT_DE_PASSE).andExpect(status().isOk()).andReturn();
        return corps(etape).get("jetonEtape").asString();
    }

    private ResultActions poster(String chemin, Map<String, String> corps) throws Exception {
        return mockMvc.perform(post(chemin).with(depuisIpClient()).header(HttpHeaders.ORIGIN, ORIGINE_SITE)
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    // ------------------------------------------------------------------ le mot de passe seul n'ouvre rien

    @Test
    void lesRolesInternesNObtiennentJamaisDeSessionAvecLeSeulMotDePasse() throws Exception {
        for (Role role : List.of(Role.SERVICE_CLIENT, Role.GESTIONNAIRE_STOCK, Role.ADMIN, Role.SUPER_ADMIN)) {
            Utilisateur u = compte(role, true);
            MvcResult resultat = connecter(u.getEmail(), MOT_DE_PASSE).andExpect(status().isOk())
                    .andExpect(jsonPath("$.etape").value("CODE_REQUIS"))
                    .andExpect(jsonPath("$.jetonAcces").doesNotExist())
                    .andReturn();
            assertThat(resultat.getResponse().getCookie(NOM_COOKIE)).as("cookie pour %s", role).isNull();
            assertThat(resultat.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        }
    }

    @Test
    void leJetonDEtapeNEstPasUnJetonDAcces() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String jeton = jetonEtape(u.getEmail());
        mockMvc.perform(get("/api/admin/produits").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isUnauthorized());
        profil(jeton).andExpect(status().isUnauthorized());
    }

    @Test
    void lInvariantDeSessionRefuseUnRoleInterneSansSecondFacteurVerifie() {
        Utilisateur u = compte(Role.ADMIN, true);
        assertThatThrownBy(() -> sessionService.ouvrir(u, new Contexte(ipClient, "test"), false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessionService.ouvrir(u, new Contexte(ipClient, "test"), true).jetonAcces().valeur()).isNotBlank();
        // Un client n'a pas de second facteur
        Utilisateur client = compte(Role.CLIENT, false);
        assertThat(sessionService.ouvrir(client, new Contexte(ipClient, "test"), false).jetonAcces().valeur()).isNotBlank();
    }

    // ------------------------------------------------------------------ enrôlement

    @Test
    void enrolementComplet() throws Exception {
        Utilisateur u = compte(Role.SERVICE_CLIENT, false);
        MvcResult etape = connecter(u.getEmail(), MOT_DE_PASSE).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("ENROLEMENT_REQUIS")).andReturn();
        String jeton = corps(etape).get("jetonEtape").asString();

        // Le secret et le QR code (SVG) viennent du serveur
        MvcResult secret = poster("/api/auth/second-facteur/enrolement/demarrer", Map.of("jetonEtape", jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeQr").value(org.hamcrest.Matchers.startsWith("data:image/svg+xml;base64,")))
                .andExpect(jsonPath("$.uri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andReturn();
        String secretBase32 = corps(secret).get("secret").asString();
        assertThat(corps(secret).get("uri").asString()).contains("secret=" + secretBase32);

        // Rien n'est activé tant qu'un code juste n'a pas été saisi
        poster("/api/auth/second-facteur/enrolement/confirmer", Map.of("jetonEtape", jeton, "code", "000000"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        assertThat(utilisateurDepot.findById(u.getId()).orElseThrow().secondFacteurActif()).isFalse();

        String code = Totp.code(Base32.decoder(secretBase32), Totp.pas(horloge.instant()), 6);
        MvcResult codes = poster("/api/auth/second-facteur/enrolement/confirmer", Map.of("jetonEtape", jeton, "code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("CODES_SECOURS_A_CONFIRMER"))
                .andExpect(jsonPath("$.codesSecours.length()").value(10))
                .andReturn();
        assertThat(codes.getResponse().getCookie(NOM_COOKIE)).as("pas de session avant la confirmation des codes").isNull();

        // Le secret est chiffré en base, les codes de secours sont hachés
        Utilisateur apres = utilisateurDepot.findById(u.getId()).orElseThrow();
        assertThat(apres.secondFacteurActif()).isTrue();
        assertThat(apres.getTotpSecretChiffre()).startsWith("v1.").doesNotContain(secretBase32);
        assertThat(apres.getCodesSecoursConfirmesLe()).isNull();
        List<String> clairs = new ArrayList<>();
        corps(codes).get("codesSecours").forEach(n -> clairs.add(n.asString()));
        for (String clair : clairs) {
            assertThat(clair).matches("[2-9A-HJKMNP-Z]{5}-[2-9A-HJKMNP-Z]{5}");
            assertThat(jdbc.queryForObject("select count(*) from code_secours where utilisateur_id = ? and empreinte = ?",
                    Integer.class, u.getId(), sn.boutique.authentification.securite.GenerateurCodesSecours.empreinte(clair)))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from code_secours where empreinte = ?", Integer.class, clair))
                    .isZero();
        }

        // « Je les ai imprimés » : la session s'ouvre, avec le cookie
        MvcResult session = poster("/api/auth/second-facteur/codes-secours/confirmer", Map.of("jetonEtape", jeton))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jetonAcces").isNotEmpty()).andReturn();
        assertThat(jetonRafraichissement(session)).isNotBlank();
        assertThat(utilisateurDepot.findById(u.getId()).orElseThrow().getCodesSecoursConfirmesLe()).isNotNull();

        // Le jeton d'étape est à usage unique
        poster("/api/auth/second-facteur/codes-secours/confirmer", Map.of("jetonEtape", jeton))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ETAPE_INVALIDE"));
    }

    @Test
    void sansConfirmationDesCodesDeSecoursLaConnexionSuivanteLesRepresente() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        jdbc.update("update utilisateur set codes_secours_confirmes_le = null where id = ?", u.getId());
        String jeton = jetonEtape(u.getEmail());

        MvcResult resultat = verifierSecondFacteur(jeton, codeTotp()).andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("CODES_SECOURS_A_CONFIRMER"))
                .andExpect(jsonPath("$.codesSecours.length()").value(10)).andReturn();
        assertThat(resultat.getResponse().getCookie(NOM_COOKIE)).isNull();
        poster("/api/auth/second-facteur/codes-secours/confirmer", Map.of("jetonEtape", jeton)).andExpect(status().isOk());
    }

    @Test
    void uneEtapeNePeutPasEtreJoueeAUneAutreEtape() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String jeton = jetonEtape(u.getEmail());
        // Compte déjà enrôlé : pas de nouvel enrôlement, et pas de confirmation de codes sans avoir saisi de code
        poster("/api/auth/second-facteur/enrolement/demarrer", Map.of("jetonEtape", jeton))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ETAPE_INVALIDE"));
        poster("/api/auth/second-facteur/codes-secours/confirmer", Map.of("jetonEtape", jeton))
                .andExpect(status().isUnauthorized());
        assertThat(utilisateurDepot.findById(u.getId()).orElseThrow().getCodesSecoursConfirmesLe()).isNotNull();
    }

    // ------------------------------------------------------------------ codes TOTP

    @Test
    void connexionAvecUnCodeTotpEtSessionDeDouzeHeures() throws Exception {
        Utilisateur u = compte(Role.GESTIONNAIRE_STOCK, true);
        MvcResult session = verifierSecondFacteur(jetonEtape(u.getEmail()), codeTotp())
                .andExpect(status().isOk()).andReturn();
        assertThat(setCookie(session)).contains("Max-Age=43200");
        mockMvc.perform(get("/api/admin/produits").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces(session)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unCodeNeServQuUneFois() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String code = codeTotp();
        verifierSecondFacteur(jetonEtape(u.getEmail()), code).andExpect(status().isOk());
        // Même code, nouvelle connexion, même pas de 30 s : refusé
        verifierSecondFacteur(jetonEtape(u.getEmail()), code).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        // Le pas suivant est accepté (et le pas précédent ne l'est plus)
        horloge.avancer(Duration.ofSeconds(30));
        verifierSecondFacteur(jetonEtape(u.getEmail()), codeTotp()).andExpect(status().isOk());
        verifierSecondFacteur(jetonEtape(u.getEmail()), code).andExpect(status().isUnauthorized());
    }

    @Test
    void deuxRequetesSimultaneesAvecLeMemeJetonOuLeMemePasUneSeuleReussit() throws Exception {
        // Même jeton d'étape, même code : le verrou de ligne sérialise, une seule session s'ouvre
        Utilisateur u = compte(Role.ADMIN, true);
        String code = codeTotp();
        String jeton = jetonEtape(u.getEmail());
        List<Integer> statuts = enParallele(2, () -> verifierSecondFacteur(jeton, code).andReturn().getResponse().getStatus());
        assertThat(statuts.stream().filter(s -> s == 200).count()).as("statuts %s", statuts).isEqualTo(1);
        assertThat(statuts).doesNotContain(500);

        // Le pas TOTP lui-même : l'écriture conditionnelle n'est acceptée qu'une fois, même sans le verrou de l'étape
        Utilisateur v = compte(Role.ADMIN, true);
        long pas = Totp.pas(horloge.instant());
        List<Integer> lignes = enParallele(4, () -> utilisateurDepot.avancerPasTotp(v.getId(), pas));
        assertThat(lignes.stream().mapToInt(Integer::intValue).sum()).as("lignes %s", lignes).isEqualTo(1);
        assertThat(utilisateurDepot.avancerPasTotp(v.getId(), pas)).isZero();
        assertThat(utilisateurDepot.avancerPasTotp(v.getId(), pas - 1)).isZero();
        assertThat(utilisateurDepot.avancerPasTotp(v.getId(), pas + 1)).isEqualTo(1);
    }

    private static <T> List<T> enParallele(int fois, Callable<T> tache) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(fois);
        try {
            List<Future<T>> futurs = new ArrayList<>();
            for (int i = 0; i < fois; i++) {
                futurs.add(pool.submit(tache));
            }
            List<T> resultats = new ArrayList<>();
            for (Future<T> futur : futurs) {
                resultats.add(futur.get());
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void laFenetreAccepteUnPasDeChaqueCoteMaisPasDeuxPas() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        verifierSecondFacteur(jetonEtape(u.getEmail()), codeTotpAu(2)).andExpect(status().isUnauthorized());
        verifierSecondFacteur(jetonEtape(u.getEmail()), codeTotpAu(-2)).andExpect(status().isUnauthorized());
        verifierSecondFacteur(jetonEtape(u.getEmail()), codeTotpAu(-1)).andExpect(status().isOk());

        Utilisateur v = compte(Role.ADMIN, true);
        verifierSecondFacteur(jetonEtape(v.getEmail()), codeTotpAu(1)).andExpect(status().isOk());
    }

    @Test
    void lesCodesMalFormesSontRefuses() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String jeton = jetonEtape(u.getEmail());
        for (String mauvais : List.of("12345", "abcdef", "1234567", "12 34")) {
            verifierSecondFacteur(jeton, mauvais).andExpect(status().isUnauthorized());
        }
        // Ni code ni code de secours, ou les deux : refusé sans compter pour un code
        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", jeton)).andExpect(status().isUnauthorized());
        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", jeton, "code", codeTotp(), "codeSecours", "ABCDE-FGHJK"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apresCinqCodesFauxLEtapeEstEpuiseeMemeAvecLeBonCode() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String jeton = jetonEtape(u.getEmail());
        for (int i = 0; i < 5; i++) {
            verifierSecondFacteur(jeton, "000000").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        }
        verifierSecondFacteur(jeton, codeTotp()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ETAPE_INVALIDE"));
        // Il faut refaire le mot de passe : une nouvelle étape fonctionne
        verifierSecondFacteur(jetonEtape(u.getEmail()), codeTotp()).andExpect(status().isOk());
    }

    @Test
    void leJetonDEtapeExpireApresCinqMinutes() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String jeton = jetonEtape(u.getEmail());
        horloge.avancer(Duration.ofMinutes(5).plusSeconds(1));
        verifierSecondFacteur(jeton, codeTotp()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ETAPE_INVALIDE"));
    }

    @Test
    void lEtapeRenouveleeNeDepassePasTrenteMinutes() throws Exception {
        Utilisateur u = compte(Role.ADMIN, false);
        String jeton = corps(connecter(u.getEmail(), MOT_DE_PASSE).andReturn()).get("jetonEtape").asString();
        // Chaque démarrage renouvelle le délai de 5 minutes, mais jamais au-delà de 30 minutes depuis le mot de passe
        for (int i = 0; i < 7; i++) {
            horloge.avancer(Duration.ofMinutes(4));
            poster("/api/auth/second-facteur/enrolement/demarrer", Map.of("jetonEtape", jeton)).andExpect(status().isOk());
        }
        horloge.avancer(Duration.ofMinutes(4));
        poster("/api/auth/second-facteur/enrolement/demarrer", Map.of("jetonEtape", jeton))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unNouveauMotDePasseAnnuleLesEtapesEnCours() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String ancien = jetonEtape(u.getEmail());
        String recent = jetonEtape(u.getEmail());
        verifierSecondFacteur(ancien, codeTotp()).andExpect(status().isUnauthorized());
        verifierSecondFacteur(recent, codeTotp()).andExpect(status().isOk());
    }

    @Test
    void unCompteDesactiveEntreLesDeuxEtapesEstRefuse() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        String jeton = jetonEtape(u.getEmail());
        jdbc.update("update utilisateur set actif = false where id = ?", u.getId());
        verifierSecondFacteur(jeton, codeTotp()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ETAPE_INVALIDE"));
    }

    // ------------------------------------------------------------------ codes de secours

    @Test
    void unCodeDeSecoursOuvreUneSessionUneSeuleFois() throws Exception {
        Utilisateur u = compte(Role.ADMIN, true);
        List<String> codes = sn.boutique.authentification.securite.GenerateurCodesSecours.generer();
        for (String c : codes) {
            jdbc.update("insert into code_secours (utilisateur_id, empreinte) values (?, ?)", u.getId(),
                    sn.boutique.authentification.securite.GenerateurCodesSecours.empreinte(c));
        }
        String code = codes.get(3);

        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", jetonEtape(u.getEmail()),
                "codeSecours", code.toLowerCase().replace("-", " "))).andExpect(status().isOk())
                .andExpect(jsonPath("$.jetonAcces").isNotEmpty());
        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", jetonEtape(u.getEmail()), "codeSecours", code))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        // Un autre code du même jeu fonctionne encore
        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", jetonEtape(u.getEmail()),
                "codeSecours", codes.get(4))).andExpect(status().isOk());
    }

    @Test
    void unCodeDeSecoursNAppartientQuASonCompte() throws Exception {
        Utilisateur proprietaire = compte(Role.ADMIN, true);
        Utilisateur autre = compte(Role.ADMIN, true);
        String code = sn.boutique.authentification.securite.GenerateurCodesSecours.generer().get(0);
        jdbc.update("insert into code_secours (utilisateur_id, empreinte) values (?, ?)", proprietaire.getId(),
                sn.boutique.authentification.securite.GenerateurCodesSecours.empreinte(code));
        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", jetonEtape(autre.getEmail()), "codeSecours", code))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ clients et réinitialisation

    @Test
    void lesClientsSeConnectentSansSecondFacteur() throws Exception {
        Utilisateur client = compte(Role.CLIENT, false);
        connecter(client.getEmail(), MOT_DE_PASSE).andExpect(status().isOk())
                .andExpect(jsonPath("$.jetonAcces").isNotEmpty()).andExpect(jsonPath("$.etape").doesNotExist());
    }

    @Test
    void unJetonDEtapeInconnuEstRefuse() throws Exception {
        poster("/api/auth/second-facteur/verifier", Map.of("jetonEtape", "inconnu", "code", codeTotp()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ETAPE_INVALIDE"));
    }
}
