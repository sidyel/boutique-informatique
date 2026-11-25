package sn.boutique.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.Contraintes.contrainteViolee;
import static sn.boutique.support.FabriqueCatalogue.unique;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.authentification.depot.JetonRafraichissementDepot;
import sn.boutique.authentification.modele.JetonRafraichissement;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Adresse;
import sn.boutique.compte.modele.Role;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.telephone.TelephoneInvalideException;
import sn.boutique.support.FabriqueComptes;
import sn.boutique.support.TestDepot;

@TestDepot
class CompteDepotTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private UtilisateurDepot utilisateurDepot;

    @Autowired
    private JetonRafraichissementDepot jetonDepot;

    @Test
    void lEmailEstNormaliseEtUniqueSansTenirCompteDeLaCasse() {
        String email = unique("moussa") + "@exemple.sn";
        utilisateurDepot.saveAndFlush(new Utilisateur("  " + email.toUpperCase() + " ", FabriqueComptes.HASH_FACTICE, "Moussa", "Ndiaye"));

        assertThat(utilisateurDepot.trouverParEmail(email.toUpperCase())).get()
                .extracting(Utilisateur::getEmail).isEqualTo(email);
        assertThat(utilisateurDepot.emailExiste(" " + email + " ")).isTrue();

        Utilisateur doublon = new Utilisateur(email, FabriqueComptes.HASH_FACTICE, "Autre", "Personne");
        assertThatThrownBy(() -> utilisateurDepot.saveAndFlush(doublon))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_utilisateur_email"));
    }

    @Test
    void unNouveauCompteEstUnClientActifNonVerifie() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);
        em.flush();

        assertThat(utilisateur.getRole()).isEqualTo(Role.CLIENT);
        assertThat(utilisateur.isActif()).isTrue();
        assertThat(utilisateur.isEmailVerifie()).isFalse();
    }

    @Test
    void leTelephoneEstStockeAuFormatE164() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);
        utilisateur.setTelephone("77 123 45 67");
        Adresse adresse = new Adresse(utilisateur, "Maison", "Awa Diop", "00221 78.000.11.22", "Dakar", "Sacré-Cœur 3");
        em.persist(adresse);
        em.flush();
        em.clear();

        assertThat(em.find(Utilisateur.class, utilisateur.getId()).getTelephone()).isEqualTo("+221771234567");
        assertThat(em.find(Adresse.class, adresse.getId()).getTelephone()).isEqualTo("+221780001122");
    }

    @Test
    void unTelephoneInvalideEstRefuseParLEntite() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);

        assertThatThrownBy(() -> utilisateur.setTelephone("79 000 00 00"))
                .isInstanceOf(TelephoneInvalideException.class);
        assertThatThrownBy(() -> new Adresse(utilisateur, "Bureau", "Awa Diop", "06 12 34 56 78", "Dakar", "Plateau"))
                .isInstanceOf(TelephoneInvalideException.class);
    }

    @Test
    void laBaseRefuseUnTelephoneNonNormalise() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);
        em.flush();

        // Écriture qui contourne l'application (import, script SQL)
        assertThatThrownBy(() -> em.createNativeQuery("update utilisateur set telephone = '771234567' where id = :id")
                .setParameter("id", utilisateur.getId())
                .executeUpdate())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_utilisateur_telephone"));
    }

    @Test
    void unClientNAQuUneSeuleAdresseParDefaut() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);
        Adresse maison = new Adresse(utilisateur, "Maison", "Awa Diop", "771234567", "Dakar", "Mermoz");
        maison.setParDefaut(true);
        Adresse bureau = new Adresse(utilisateur, "Bureau", "Awa Diop", "771234567", "Dakar", "Plateau");
        bureau.setParDefaut(true);
        em.persist(maison);

        // Identifiant IDENTITY : l'INSERT part dès le persist
        assertThatThrownBy(() -> {
            em.persist(bureau);
            em.flush();
        })
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("uk_adresse_par_defaut"));
    }

    @Test
    void revoquerUneFamilleRendTousSesJetonsInutilisables() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);
        UUID famille = UUID.randomUUID();
        Instant expiration = Instant.now().plus(30, ChronoUnit.DAYS);
        JetonRafraichissement premier = jetonDepot.save(
                new JetonRafraichissement(utilisateur, empreinte(), expiration, famille, "41.82.0.1", "Mozilla/5.0"));
        JetonRafraichissement second = jetonDepot.save(
                new JetonRafraichissement(utilisateur, empreinte(), expiration, famille, "41.82.0.1", "Mozilla/5.0"));
        JetonRafraichissement autreAppareil = jetonDepot.save(
                new JetonRafraichissement(utilisateur, empreinte(), expiration, UUID.randomUUID(), null, null));

        assertThat(jetonDepot.revoquerFamille(famille)).isEqualTo(2);

        Instant maintenant = Instant.now();
        assertThat(jetonDepot.findById(premier.getId()).orElseThrow().estUtilisable(maintenant)).isFalse();
        assertThat(jetonDepot.findById(second.getId()).orElseThrow().estUtilisable(maintenant)).isFalse();
        assertThat(jetonDepot.findById(autreAppareil.getId()).orElseThrow().estUtilisable(maintenant)).isTrue();
    }

    @Test
    void lEmpreinteDUnJetonDoitEtreUnSha256Hexadecimal() {
        Utilisateur utilisateur = FabriqueComptes.utilisateur(em);
        JetonRafraichissement jeton = new JetonRafraichissement(utilisateur, "jeton-en-clair",
                Instant.now().plusSeconds(60), UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> jetonDepot.saveAndFlush(jeton))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_jeton_rafraichissement_empreinte"));
    }

    private static String empreinte() {
        byte[] octets = new byte[32];
        new java.security.SecureRandom().nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }
}
