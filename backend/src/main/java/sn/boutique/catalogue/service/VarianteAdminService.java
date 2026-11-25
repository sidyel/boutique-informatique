package sn.boutique.catalogue.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.VarianteProduitDepot;
import sn.boutique.catalogue.dto.ModificationVarianteRequete;
import sn.boutique.catalogue.dto.ProduitAdminResumeReponse;
import sn.boutique.catalogue.evenement.CatalogueModifie;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Modification du prix et de l'état d'une variante depuis le back-office. */
@Service
@RequiredArgsConstructor
@Transactional
public class VarianteAdminService {

    private final VarianteProduitDepot varianteDepot;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher evenements;

    public ProduitAdminResumeReponse modifier(Long id, ModificationVarianteRequete requete) {
        VarianteProduit variante = varianteDepot.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Variante", String.valueOf(id)));
        Produit produit = variante.getProduit();

        if (requete.getPrix() != null) {
            variante.setPrix(requete.getPrix());
        }
        if (requete.isPrixBarreFourni()) {
            variante.setPrixBarre(requete.getPrixBarre());
        }
        if (variante.getPrixBarre() != null && variante.getPrixBarre() <= variante.getPrix()) {
            throw new RegleMetierException("PRIX_BARRE_INVALIDE",
                    "Le prix barré (%d FCFA) doit être supérieur au prix (%d FCFA)."
                            .formatted(variante.getPrixBarre(), variante.getPrix()));
        }
        if (Boolean.FALSE.equals(requete.getActif()) && variante.isActif() && produit.isActif()
                && produit.getVariantes().stream().noneMatch(autre -> autre != variante && autre.isActif())) {
            throw new RegleMetierException("DERNIERE_VARIANTE_ACTIVE",
                    "C'est la dernière variante active d'un produit publié : désactiver d'abord le produit.");
        }
        if (requete.getActif() != null) {
            variante.setActif(requete.getActif());
        }

        // prixMinimum et stockTotal sont recalculés par un trigger à l'écriture de la variante : le produit
        // chargé garde les anciennes valeurs tant qu'il n'est pas relu (ADR 0004 §2)
        varianteDepot.saveAndFlush(variante);
        entityManager.refresh(produit);
        evenements.publishEvent(new CatalogueModifie("variante " + variante.getSku()));
        return ProduitAdminResumeReponse.depuis(produit);
    }
}
