package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.boutique.support.FabriqueCatalogue.unique;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sn.boutique.catalogue.depot.ProduitDepot;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.DefinitionCaracteristique;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.TypeCaracteristique;
import sn.boutique.catalogue.modele.ValeurCaracteristique;
import sn.boutique.support.TestDepot;

/**
 * Recherche plein texte (V7). D'autres tests peuvent avoir laissé des produits en base :
 * on vérifie donc la présence (ou l'absence) des produits créés ici, et des mots inventés
 * servent quand il faut un résultat exclusif.
 */
@TestDepot
class RechercheProduitTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProduitDepot produitDepot;

    /** Mot inventé, en lettres seules, introuvable ailleurs dans la base. */
    private static String motInvente() {
        StringBuilder mot = new StringBuilder("zq");
        for (int i = 0; i < 8; i++) {
            mot.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return mot.toString();
    }

    private Marque marque(String nom) {
        Marque marque = new Marque(nom, unique(nom.toLowerCase()));
        em.persist(marque);
        return marque;
    }

    private Categorie categorie(String nom, Categorie parent) {
        Categorie categorie = new Categorie(nom, unique("categorie"));
        categorie.setParent(parent);
        em.persist(categorie);
        return categorie;
    }

    private Produit produit(String nom, Marque marque, Categorie categorie) {
        Produit produit = new Produit(nom, unique("produit"), marque, categorie);
        em.persist(produit);
        em.flush();
        return produit;
    }

    private List<Long> rechercher(String texte) {
        em.flush();
        return produitDepot.rechercher(texte, 500).stream().map(Produit::getId).toList();
    }

    @Test
    void asusPortableTrouveUnPortableAsusDontLeNomNeContientPasAsus() {
        Produit zenbook = produit("Zenbook 14 OLED UX3405", marque("Asus"), categorie("PC portables", null));
        Produit ecranAsus = produit("ProArt PA278QV", marque("Asus"), categorie("Écrans", null));
        Produit portableLenovo = produit("IdeaPad Slim 3", marque("Lenovo"), categorie("PC portables", null));

        assertThat(rechercher("asus portable"))
                .contains(zenbook.getId())
                .doesNotContain(ecranAsus.getId(), portableLenovo.getId());
        // Casse, pluriel et accents sans importance
        assertThat(rechercher("ASUS Portables")).contains(zenbook.getId());
        assertThat(rechercher("asus portáble")).contains(zenbook.getId());
    }

    @Test
    void laRechercheEstInsensibleAuxAccents() {
        Produit moniteur = produit("Moniteur 27 pouces", marque("Samsung"), categorie("Écrans", null));

        assertThat(rechercher("ecran")).contains(moniteur.getId());
        assertThat(rechercher("écrans")).contains(moniteur.getId());
        assertThat(rechercher("ÉCRAN")).contains(moniteur.getId());
    }

    @Test
    void laCategorieParenteEstCherchable() {
        Categorie ordinateurs = categorie("Ordinateurs", null);
        Produit nitro = produit("Nitro V 15", marque("Acer"), categorie("PC portables gaming", ordinateurs));

        assertThat(rechercher("ordinateur gaming acer")).contains(nitro.getId());
    }

    @Test
    void leMpnEstCherchable() {
        String mpn = "FA507NV-" + motInvente().toUpperCase();
        Produit tuf = new Produit("TUF Gaming A15", unique("tuf"), marque("Asus"), categorie("PC portables", null));
        tuf.setMpn(mpn);
        em.persist(tuf);

        assertThat(rechercher(mpn.toLowerCase())).containsExactly(tuf.getId());
    }

    @Test
    void seulesLesCaracteristiquesClesSontCherchables() {
        String processeur = motInvente();
        String connectique = motInvente();
        Categorie portables = categorie("PC portables", null);
        DefinitionCaracteristique cpu = new DefinitionCaracteristique(
                portables, "processeur_modele", "Processeur", TypeCaracteristique.TEXTE, "Processeur");
        cpu.setCleRecherche(true);
        DefinitionCaracteristique ports = new DefinitionCaracteristique(
                portables, "ports", "Ports", TypeCaracteristique.TEXTE, "Connectique");
        em.persist(cpu);
        em.persist(ports);
        Produit produit = new Produit("Vivobook 16", unique("vivobook"), marque("Asus"), portables);
        produit.ajouterCaracteristique(cpu).setValeurTexte("Ryzen 7 " + processeur);
        produit.ajouterCaracteristique(ports).setValeurTexte("USB-C " + connectique);
        em.persist(produit);

        assertThat(rechercher(processeur)).containsExactly(produit.getId());
        assertThat(rechercher(connectique)).isEmpty();
    }

    @Test
    void lesCaracteristiquesNumeriquesSontIndexeesAvecLeurUnite() {
        Categorie portables = categorie("PC portables", null);
        DefinitionCaracteristique ram = new DefinitionCaracteristique(
                portables, "ram_go", "Mémoire vive", TypeCaracteristique.NOMBRE, "Mémoire");
        ram.setUnite("Go");
        ram.setCleRecherche(true);
        em.persist(ram);
        String nom = motInvente();
        Produit produit = new Produit(nom, unique("p"), marque("HP"), portables);
        produit.ajouterCaracteristique(ram).setValeurNombre(new java.math.BigDecimal("32.0000"));
        em.persist(produit);

        // 32.0000 est indexé « 32 Go », pas « 32.0000 »
        assertThat(rechercher(nom + " 32 go")).containsExactly(produit.getId());
    }

    @Test
    void leNomPasseAvantLaDescription() {
        String mot = motInvente();
        Marque marque = marque("Dell");
        Categorie categorie = categorie("PC portables", null);
        Produit parDescription = new Produit("Latitude 5440", unique("latitude"), marque, categorie);
        parDescription.setDescriptionCourte("Compatible avec la station " + mot);
        em.persist(parDescription);
        Produit parNom = new Produit("Station " + mot, unique("station"), marque, categorie);
        em.persist(parNom);

        assertThat(rechercher(mot)).containsExactly(parNom.getId(), parDescription.getId());
    }

    @Test
    void lesVecteursSuiventLesModificationsDesTablesLiees() {
        String ancienNom = motInvente();
        String nouveauNom = motInvente();
        Marque marque = marque(ancienNom);
        Categorie categorie = categorie("PC portables", null);
        Produit produit = produit("Modèle X", marque, categorie);
        assertThat(rechercher(ancienNom)).containsExactly(produit.getId());

        // Marque renommée
        marque.setNom(nouveauNom);
        assertThat(rechercher(nouveauNom)).containsExactly(produit.getId());
        assertThat(rechercher(ancienNom)).isEmpty();

        // Catégorie renommée
        String nomCategorie = motInvente();
        categorie.setNom(nomCategorie);
        assertThat(rechercher(nomCategorie)).containsExactly(produit.getId());

        // Caractéristique clé ajoutée puis supprimée
        String gpu = motInvente();
        DefinitionCaracteristique carteGraphique = new DefinitionCaracteristique(
                categorie, "carte_graphique", "Carte graphique", TypeCaracteristique.TEXTE, "Graphisme");
        carteGraphique.setCleRecherche(true);
        em.persist(carteGraphique);
        ValeurCaracteristique valeur = produit.ajouterCaracteristique(carteGraphique);
        valeur.setValeurTexte("RTX 4060 " + gpu);
        assertThat(rechercher(gpu)).containsExactly(produit.getId());

        produit.getCaracteristiques().remove(valeur);
        assertThat(rechercher(gpu)).isEmpty();

        // Caractéristique retirée de la recherche
        produit.ajouterCaracteristique(carteGraphique).setValeurTexte("RTX 4070 " + gpu);
        assertThat(rechercher(gpu)).containsExactly(produit.getId());
        carteGraphique.setCleRecherche(false);
        assertThat(rechercher(gpu)).isEmpty();
    }

    @Test
    void unProduitInactifNEstPasTrouve() {
        String nom = motInvente();
        Produit produit = produit(nom, marque("MSI"), categorie("PC portables", null));
        assertThat(rechercher(nom)).containsExactly(produit.getId());

        produit.setActif(false);
        assertThat(rechercher(nom)).isEmpty();
    }

    @Test
    void laSuggestionTolereLesFautesDeFrappe() {
        Produit ideapad = produit("Lenovo IdeaPad Slim 3 15IAN8", marque("Lenovo"), categorie("PC portables", null));
        em.flush();

        assertThat(produitDepot.suggerer("lenvo ideapad", 50)).extracting(Produit::getId).contains(ideapad.getId());
        assertThat(produitDepot.suggerer("idéapad", 50)).extracting(Produit::getId).contains(ideapad.getId());
    }
}
