package sn.boutique.support;

import static sn.boutique.support.FabriqueCatalogue.unique;

import jakarta.persistence.EntityManager;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.NumeroCommande;
import sn.boutique.compte.modele.Utilisateur;

/**
 * Crée des comptes et des commandes minimales et uniques pour les tests.
 */
public final class FabriqueComptes {

    /** Empreinte BCrypt factice (les tests de dépôt ne vérifient pas le mot de passe). */
    public static final String HASH_FACTICE = "$2a$12$" + "a".repeat(53);

    private FabriqueComptes() {
    }

    public static Utilisateur utilisateur(EntityManager em) {
        Utilisateur utilisateur = new Utilisateur(unique("awa") + "@exemple.sn", HASH_FACTICE, "Awa", "Diop");
        em.persist(utilisateur);
        return utilisateur;
    }

    /** Commande en retrait magasin (pas d'adresse ni de zone), non persistée. */
    public static Commande commandeRetrait() {
        long compteur = Long.parseLong(unique("0").substring(2));
        return new Commande(NumeroCommande.formater(2026, 900_000 + compteur), "client@exemple.sn",
                "77 123 45 67", ModeLivraison.RETRAIT_MAGASIN, ModePaiement.A_LA_LIVRAISON);
    }
}
