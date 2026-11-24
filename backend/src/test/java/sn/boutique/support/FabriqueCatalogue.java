package sn.boutique.support;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.Marque;
import sn.boutique.catalogue.modele.Produit;

/**
 * Crée des données de catalogue minimales et uniques pour les tests.
 */
public final class FabriqueCatalogue {

    private static final AtomicInteger COMPTEUR = new AtomicInteger();

    private FabriqueCatalogue() {
    }

    public static String unique(String prefixe) {
        return prefixe + "-" + COMPTEUR.incrementAndGet();
    }

    public static Categorie categorie(EntityManager em) {
        Categorie categorie = new Categorie("PC portables", unique("pc-portables"));
        em.persist(categorie);
        return categorie;
    }

    public static Marque marque(EntityManager em) {
        Marque marque = new Marque("Asus", unique("asus"));
        em.persist(marque);
        return marque;
    }

    /** Produit avec sa marque et sa catégorie, non encore persisté. */
    public static Produit produit(EntityManager em) {
        return new Produit("TUF Gaming A15", unique("tuf-gaming-a15"), marque(em), categorie(em));
    }
}
