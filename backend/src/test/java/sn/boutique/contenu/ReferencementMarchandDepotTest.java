package sn.boutique.contenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.Contraintes.contrainteViolee;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.contenu.modele.FraisRetour;
import sn.boutique.contenu.modele.HoraireOuverture;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.TestDepot;

/** V15 : politique de retour, boutique physique et retrait définitif d'un produit. */
@TestDepot
class ReferencementMarchandDepotTest {

    private static final ParametresPublicsReponse.Site SITE =
            new ParametresPublicsReponse.Site("https://boutique.sn", false);
    private static final Instant RETRAIT = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    private EntityManager em;

    @Autowired
    private ParametresSiteDepot parametresDepot;

    @Test
    void desRetoursAcceptesExigentUnDelaiEtUnMode() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setDelaiRetourJours(null);
        assertThatThrownBy(() -> parametresDepot.saveAndFlush(parametres))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_parametres_site_retours"));
    }

    @Test
    void aucunModeDeRetourEstRefuseSiLesRetoursSontAcceptes() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setRetourEnBoutique(false);
        parametres.setRetourParLivreur(false);
        assertThatThrownBy(() -> parametresDepot.saveAndFlush(parametres))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_parametres_site_retours"));
    }

    @Test
    void desRetoursRefusesNePublientPasDeDelai() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setRetoursAcceptes(false);
        parametres.setRetourEnBoutique(false);
        parametres.setFraisRetour(FraisRetour.A_LA_CHARGE_DU_CLIENT);
        parametresDepot.saveAndFlush(parametres);
        em.clear();

        ParametresPublicsReponse.PolitiqueRetour politique =
                ParametresPublicsReponse.depuis(parametresDepot.lire(), SITE).politiqueRetour();
        assertThat(politique.acceptes()).isFalse();
        assertThat(politique.delaiJours()).isNull();
    }

    @Test
    void uneBoutiquePhysiqueExigeUneAdresseComplete() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setBoutiquePhysique(true);
        parametres.setBoutiqueRue("Route de Ouakam");
        parametres.setBoutiqueVille("Dakar");
        assertThatThrownBy(() -> parametresDepot.saveAndFlush(parametres))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_parametres_site_boutique_adresse"));
    }

    @Test
    void lesCoordonneesVontParPaire() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setBoutiqueLatitude(new BigDecimal("14.716677"));
        assertThatThrownBy(() -> parametresDepot.saveAndFlush(parametres))
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_parametres_site_boutique_coordonnees"));
    }

    @Test
    void laBoutiquePhysiqueEstPublieeAvecSesHorairesValides() {
        ParametresSite parametres = parametresDepot.lire();
        parametres.setBoutiquePhysique(true);
        parametres.setBoutiqueRue(" Route de Ouakam ");
        parametres.setBoutiqueVille("Dakar");
        parametres.setBoutiqueRegion("Dakar");
        parametres.setBoutiqueLatitude(new BigDecimal("14.716677"));
        parametres.setBoutiqueLongitude(new BigDecimal("-17.467686"));
        parametres.getHorairesOuverture().addAll(List.of(
                new HoraireOuverture(List.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), "09:00", "19:00"),
                // Fermeture avant l'ouverture : écartée de la réponse publique
                new HoraireOuverture(List.of(DayOfWeek.SUNDAY), "18:00", "10:00")));
        parametresDepot.saveAndFlush(parametres);
        em.clear();

        ParametresPublicsReponse reponse = ParametresPublicsReponse.depuis(parametresDepot.lire(), SITE);
        assertThat(reponse.adresseStructuree().rue()).isEqualTo("Route de Ouakam");
        assertThat(reponse.adresseStructuree().codePostal()).isNull();
        assertThat(reponse.boutiquePhysique().latitude()).isEqualByComparingTo("14.716677");
        assertThat(reponse.boutiquePhysique().horaires())
                .containsExactly(new HoraireOuverture(List.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), "09:00", "19:00"));
    }

    @Test
    void unProduitRetireEstInactifEtPeutAvoirUnRemplacant() {
        Produit remplacant = FabriqueCatalogue.produit(em);
        em.persist(remplacant);
        Produit ancien = FabriqueCatalogue.produit(em);
        em.persist(ancien);
        ancien.retirer(RETRAIT, remplacant);
        em.flush();
        em.clear();

        Produit relu = em.find(Produit.class, ancien.getId());
        assertThat(relu.isActif()).isFalse();
        assertThat(relu.estRetire()).isTrue();
        assertThat(relu.getProduitRemplacant().getId()).isEqualTo(remplacant.getId());
    }

    @Test
    void unProduitRetireNePeutPasEtreRepublieSansAnnulerLeRetrait() {
        Produit produit = FabriqueCatalogue.produit(em);
        em.persist(produit);
        produit.retirer(RETRAIT, null);
        em.flush();
        produit.setActif(true);
        assertThatThrownBy(() -> em.flush())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_produit_retrait_inactif"));
    }

    @Test
    void unProduitNeSeRemplacePasLuiMeme() {
        Produit produit = FabriqueCatalogue.produit(em);
        em.persist(produit);
        assertThatThrownBy(() -> produit.retirer(RETRAIT, produit)).isInstanceOf(IllegalArgumentException.class);
        em.flush();
        assertThatThrownBy(() -> em.createNativeQuery(
                        "update produit set date_retrait = now(), actif = false, produit_remplacant_id = id where id = "
                                + produit.getId())
                .executeUpdate())
                .satisfies(e -> assertThat(contrainteViolee(e)).isEqualTo("ck_produit_remplacant"));
    }
}
