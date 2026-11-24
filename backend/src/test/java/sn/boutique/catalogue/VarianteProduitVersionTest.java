package sn.boutique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.boutique.support.FabriqueCatalogue.unique;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.support.FabriqueCatalogue;
import sn.boutique.support.TestDepot;

/**
 * Deux vendeurs (ou deux commandes) modifient le stock de la même variante en même temps :
 * la seconde écriture doit échouer au lieu d'écraser la première.
 */
@TestDepot
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VarianteProduitVersionTest {

    @Autowired
    private EntityManagerFactory fabrique;

    @Test
    void uneEcritureConcurrenteSurLeStockEstRefusee() {
        Long idVariante = creerVariante();

        EntityManager premier = fabrique.createEntityManager();
        EntityManager second = fabrique.createEntityManager();
        try {
            premier.getTransaction().begin();
            second.getTransaction().begin();
            VarianteProduit vuParLePremier = premier.find(VarianteProduit.class, idVariante);
            VarianteProduit vuParLeSecond = second.find(VarianteProduit.class, idVariante);

            vuParLePremier.setStock(vuParLePremier.getStock() - 1);
            premier.getTransaction().commit();

            vuParLeSecond.setStock(vuParLeSecond.getStock() - 2);
            assertThatThrownBy(() -> second.getTransaction().commit())
                    .isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);
        } finally {
            premier.close();
            second.close();
        }

        EntityManager verification = fabrique.createEntityManager();
        try {
            VarianteProduit finale = verification.find(VarianteProduit.class, idVariante);
            assertThat(finale.getStock()).isEqualTo(4);
            assertThat(finale.getVersion()).isEqualTo(1);
        } finally {
            verification.close();
        }
    }

    private Long creerVariante() {
        EntityManager em = fabrique.createEntityManager();
        try {
            em.getTransaction().begin();
            Produit produit = FabriqueCatalogue.produit(em);
            VarianteProduit variante = produit.ajouterVariante(unique("VERSION"), "16 Go", 450_000, 5);
            em.persist(produit);
            em.getTransaction().commit();
            return variante.getId();
        } finally {
            em.close();
        }
    }
}
