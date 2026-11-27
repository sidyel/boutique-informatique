package sn.boutique.authentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.support.ClientMailpit;
import sn.boutique.support.ClientMailpit.Email;
import sn.boutique.support.FabriqueComptes;

/** Espace client (phase 5b) : mot de passe, e-mail, téléphone, consentement et rattachement des commandes invité. */
class EspaceClientCompteTest extends AuthentificationTestBase {

    private static final String NOUVEAU_MOT_DE_PASSE = "Mafe-Tiga-Diga-2026";

    @Value("${test.mailpit.url}")
    private String urlMailpit;
    @Autowired
    private CommandeDepot commandeDepot;

    private ClientMailpit mailpit;

    @BeforeEach
    void connecterMailpit() {
        mailpit = new ClientMailpit(urlMailpit);
    }

    private Utilisateur compte(String email, boolean verifie) {
        Utilisateur u = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Awa", "Diop");
        u.setEmailVerifie(verifie);
        return utilisateurDepot.save(u);
    }

    private ResultActions appeler(String methode, String chemin, String jetonAcces, Object corps) throws Exception {
        var requete = "PUT".equals(methode) ? put(chemin) : post(chemin);
        return mockMvc.perform(requete.with(depuisIpClient()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAcces)
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private ResultActions lien(String chemin, String jeton) throws Exception {
        return mockMvc.perform(post(chemin).with(depuisIpClient()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jeton", jeton))));
    }

    private String commandeInvite(String email) {
        var commande = FabriqueComptes.commandeRetrait();
        var enregistree = commandeDepot.save(new sn.boutique.commande.modele.Commande(commande.getNumero(), email,
                "77 123 45 67", commande.getModeLivraison(), commande.getModePaiement()));
        return enregistree.getNumero();
    }

    private Long proprietaire(String numero) {
        return jdbc.queryForList("select utilisateur_id from commande where numero = ?", Long.class, numero).get(0);
    }

    // ------------------------------------------------------------------ mot de passe

    @Test
    void changerLeMotDePasseExigeLAncienEtGardeSeulementLaSessionCourante() throws Exception {
        String email = emailUnique();
        compte(email, true);
        MvcResult courante = connecter(email, MOT_DE_PASSE).andExpect(status().isOk()).andReturn();
        MvcResult autre = connecter(email, MOT_DE_PASSE).andExpect(status().isOk()).andReturn();

        appeler("POST", "/api/compte/mot-de-passe", jetonAcces(courante),
                Map.of("motDePasseActuel", "faux-faux-faux-1", "nouveauMotDePasse", NOUVEAU_MOT_DE_PASSE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_ACTUEL_INCORRECT"));

        appeler("POST", "/api/compte/mot-de-passe", jetonAcces(courante),
                Map.of("motDePasseActuel", MOT_DE_PASSE, "nouveauMotDePasse", NOUVEAU_MOT_DE_PASSE))
                .andExpect(status().isOk());

        rafraichir(jetonRafraichissement(courante)).andExpect(status().isOk());
        rafraichir(jetonRafraichissement(autre)).andExpect(status().isUnauthorized());
        connecter(email, NOUVEAU_MOT_DE_PASSE).andExpect(status().isOk());
        connecter(email, MOT_DE_PASSE).andExpect(status().isUnauthorized());

        Email alerte = mailpit.dernier(email);
        assertThat(alerte.sujet()).isEqualTo("Votre mot de passe a été modifié");
        assertThat(alerte.html()).contains("reste connecté");
    }

    @Test
    void lAncienMotDePasseNeSertPasDeNouveauMotDePasse() throws Exception {
        String email = emailUnique();
        compte(email, true);
        MvcResult session = connecter(email, MOT_DE_PASSE).andReturn();
        appeler("POST", "/api/compte/mot-de-passe", jetonAcces(session),
                Map.of("motDePasseActuel", MOT_DE_PASSE, "nouveauMotDePasse", MOT_DE_PASSE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_IDENTIQUE"));
    }

    // ------------------------------------------------------------------ téléphone et consentement

    @Test
    void leTelephoneDuProfilEstNormaliseEnE164EtRefuseSiInvalide() throws Exception {
        String email = emailUnique();
        compte(email, true);
        String acces = jetonAcces(connecter(email, MOT_DE_PASSE).andReturn());

        appeler("PUT", "/api/compte/profil", acces, Map.of("prenom", "Awa", "nom", "Diop", "telephone", "77 123 45 67"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telephone").value("+221771234567"));
        appeler("PUT", "/api/compte/profil", acces, Map.of("prenom", "Awa", "nom", "Diop", "telephone", "12345"))
                .andExpect(status().isBadRequest());
        appeler("PUT", "/api/compte/profil", acces, Map.of("prenom", "Awa", "nom", "Diop", "telephone", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telephone").doesNotExist());
    }

    @Test
    void leConsentementEstDateAvecSaSourceEtSeRetireAussiSimplement() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE).andExpect(status().isAccepted());
        String acces = jetonAcces(connecter(email, MOT_DE_PASSE).andReturn());
        Long id = utilisateurDepot.trouverParEmail(email).orElseThrow().getId();

        appeler("PUT", "/api/compte/consentement-marketing", acces, Map.of("consentement", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentementMarketing").value(false))
                .andExpect(jsonPath("$.sourceConsentement").value("COMPTE"));
        // Même choix répété : pas de ligne inutile dans l'historique
        appeler("PUT", "/api/compte/consentement-marketing", acces, Map.of("consentement", false)).andExpect(status().isOk());
        appeler("PUT", "/api/compte/consentement-marketing", acces, Map.of("consentement", true))
                .andExpect(jsonPath("$.consentementMarketing").value(true));

        var historique = jdbc.queryForList(
                "select consentement, source from historique_consentement where utilisateur_id = ? order by id", id);
        assertThat(historique).extracting(l -> l.get("consentement") + "/" + l.get("source"))
                .containsExactly("true/INSCRIPTION", "false/COMPTE", "true/COMPTE");
    }

    // ------------------------------------------------------------------ changement d'e-mail

    @Test
    void changerDEmailExigeLeMotDePasseEtLeLienDeLaNouvelleAdresse() throws Exception {
        String ancien = emailUnique();
        String nouveau = emailUnique();
        compte(ancien, true);
        String acces = jetonAcces(connecter(ancien, MOT_DE_PASSE).andReturn());

        appeler("POST", "/api/compte/email", acces, Map.of("motDePasse", "faux-faux-faux-1", "nouvelEmail", nouveau))
                .andExpect(status().isUnprocessableContent());
        appeler("POST", "/api/compte/email", acces, Map.of("motDePasse", MOT_DE_PASSE, "nouvelEmail", nouveau))
                .andExpect(status().isAccepted());

        Email confirmation = mailpit.dernier(nouveau);
        Email alerte = mailpit.dernier(ancien);
        assertThat(alerte.html()).contains("Ce n’est pas moi").contains(nouveau);
        // Tant que le lien n'est pas ouvert, rien ne change
        connecter(ancien, MOT_DE_PASSE).andExpect(status().isOk());
        connecter(nouveau, MOT_DE_PASSE).andExpect(status().isUnauthorized());

        lien("/api/auth/confirmer-email", confirmation.jeton()).andExpect(status().isOk());
        connecter(nouveau, MOT_DE_PASSE).andExpect(status().isOk());
        connecter(ancien, MOT_DE_PASSE).andExpect(status().isUnauthorized());
        assertThat(utilisateurDepot.trouverParEmail(nouveau).orElseThrow().isEmailVerifie()).isTrue();
        assertThat(mailpit.dernier(ancien).sujet()).isEqualTo("Votre adresse e-mail a été changée");
        // Usage unique
        lien("/api/auth/confirmer-email", confirmation.jeton()).andExpect(status().isBadRequest());
    }

    @Test
    void uneAdresseDejaUtiliseeDonneLaMemeReponseSansRienEnvoyer() throws Exception {
        String ancien = emailUnique();
        String pris = emailUnique();
        compte(ancien, true);
        compte(pris, true);
        String acces = jetonAcces(connecter(ancien, MOT_DE_PASSE).andReturn());

        appeler("POST", "/api/compte/email", acces, Map.of("motDePasse", MOT_DE_PASSE, "nouvelEmail", pris))
                .andExpect(status().isAccepted());
        assertThat(mailpit.compterApresDelai(pris)).isZero();
    }

    @Test
    void signalerUnChangementNonSollicitePermetDAnnulerPuisDeRetablirLAncienneAdresse() throws Exception {
        String ancien = emailUnique();
        String nouveau = emailUnique();
        compte(ancien, true);
        MvcResult session = connecter(ancien, MOT_DE_PASSE).andReturn();
        appeler("POST", "/api/compte/email", jetonAcces(session), Map.of("motDePasse", MOT_DE_PASSE, "nouvelEmail", nouveau));
        String jetonConfirmation = mailpit.dernier(nouveau).jeton();
        String jetonSignalement = mailpit.dernier(ancien).jeton();

        // Avant confirmation : la demande est annulée, les sessions sont révoquées
        lien("/api/auth/signaler-changement-email", jetonSignalement).andExpect(status().isOk());
        lien("/api/auth/confirmer-email", jetonConfirmation).andExpect(status().isBadRequest());
        rafraichir(jetonRafraichissement(session)).andExpect(status().isUnauthorized());
        assertThat(mailpit.attendre(ancien, 2)).extracting(Email::sujet).contains("Choisissez un nouveau mot de passe");

        // Après confirmation : l'ancienne adresse est rétablie dans le délai de 7 jours
        String autreNouveau = emailUnique();
        String acces = jetonAcces(connecter(ancien, MOT_DE_PASSE).andReturn());
        appeler("POST", "/api/compte/email", acces, Map.of("motDePasse", MOT_DE_PASSE, "nouvelEmail", autreNouveau));
        lien("/api/auth/confirmer-email", mailpit.dernier(autreNouveau).jeton()).andExpect(status().isOk());
        assertThat(utilisateurDepot.trouverParEmail(autreNouveau)).isPresent();
        String signalement2 = mailpit.attendre(ancien, 1).stream()
                .filter(e -> e.sujet().startsWith("Demande de changement")).findFirst().orElseThrow().jeton();
        lien("/api/auth/signaler-changement-email", signalement2).andExpect(status().isOk());
        assertThat(utilisateurDepot.trouverParEmail(ancien)).isPresent();
        assertThat(utilisateurDepot.trouverParEmail(autreNouveau)).isEmpty();
    }

    @Test
    void leSignalementExpireSeptJoursApresLaConfirmation() throws Exception {
        String ancien = emailUnique();
        String nouveau = emailUnique();
        compte(ancien, true);
        String acces = jetonAcces(connecter(ancien, MOT_DE_PASSE).andReturn());
        appeler("POST", "/api/compte/email", acces, Map.of("motDePasse", MOT_DE_PASSE, "nouvelEmail", nouveau));
        lien("/api/auth/confirmer-email", mailpit.dernier(nouveau).jeton()).andExpect(status().isOk());
        String signalement = mailpit.attendre(ancien, 1).stream()
                .filter(e -> e.sujet().startsWith("Demande de changement")).findFirst().orElseThrow().jeton();

        horloge.avancer(java.time.Duration.ofDays(8));
        lien("/api/auth/signaler-changement-email", signalement).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ rattachement des commandes invité

    @Test
    void laVerificationDeLEmailRattacheLesCommandesInviteDeLaMemeAdresseSeulement() throws Exception {
        String email = emailUnique();
        inscrire(email, MOT_DE_PASSE).andExpect(status().isAccepted());
        String lienVerification = mailpit.dernier(email).jeton();
        String sienne = commandeInvite(email.toUpperCase());   // casse différente : même adresse
        String autre = commandeInvite(emailUnique());
        assertThat(proprietaire(sienne)).isNull();

        mockMvc.perform(post("/api/auth/verification-email").with(depuisIpClient()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("jeton", lienVerification)))).andExpect(status().isOk());

        assertThat(proprietaire(sienne)).isEqualTo(utilisateurDepot.trouverParEmail(email).orElseThrow().getId());
        assertThat(proprietaire(autre)).isNull();
        assertThat(mailpit.attendre(email, 2)).as("récapitulatif envoyé au titulaire")
                .anyMatch(e -> e.sujet().equals("Votre commande est dans votre compte") && e.html().contains(sienne));
        assertThat(jdbc.queryForObject("select count(*) from rattachement_commande where declencheur = 'VERIFICATION_EMAIL'"
                + " and utilisateur_id = ?", Integer.class, utilisateurDepot.trouverParEmail(email).orElseThrow().getId()))
                .isEqualTo(1);
    }

    @Test
    void unCompteNonVerifieNeRecoitJamaisDeCommandesNiParConnexionNiParTelephone() throws Exception {
        String email = emailUnique();
        Utilisateur u = compte(email, false);
        u.setTelephone("+221771234567");
        utilisateurDepot.save(u);
        String commandeMemeEmail = commandeInvite(email);
        String commandeMemeTelephone = commandeInvite(emailUnique());
        jdbc.update("update commande set telephone_client = '+221771234567' where numero = ?", commandeMemeTelephone);

        connecter(email, MOT_DE_PASSE).andExpect(status().isOk());

        assertThat(proprietaire(commandeMemeEmail)).isNull();
        assertThat(proprietaire(commandeMemeTelephone)).isNull();
    }

    @Test
    void leChangementDEmailConfirmeRattacheLesCommandesDeLaNouvelleAdresse() throws Exception {
        String ancien = emailUnique();
        String nouveau = emailUnique();
        Utilisateur u = compte(ancien, true);
        String commande = commandeInvite(nouveau);
        String acces = jetonAcces(connecter(ancien, MOT_DE_PASSE).andReturn());
        appeler("POST", "/api/compte/email", acces, Map.of("motDePasse", MOT_DE_PASSE, "nouvelEmail", nouveau));
        assertThat(proprietaire(commande)).as("avant confirmation").isNull();

        lien("/api/auth/confirmer-email", mailpit.dernier(nouveau).jeton()).andExpect(status().isOk());

        assertThat(proprietaire(commande)).isEqualTo(u.getId());
    }
}
