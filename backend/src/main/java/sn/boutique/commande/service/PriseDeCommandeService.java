package sn.boutique.commande.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sn.boutique.commande.dto.CommandeCreeeReponse;
import sn.boutique.commande.dto.CreationCommandeRequete;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.paiement.PaiementIndisponibleException;
import sn.boutique.commande.paiement.PaiementService;
import sn.boutique.commande.service.CommandeService.ContexteRequete;
import sn.boutique.panier.service.PanierService.Demandeur;

/**
 * Prise de commande complète : création (une transaction, voir {@link CommandeService}) puis, pour un paiement en
 * ligne, démarrage du paiement chez le prestataire <strong>après le commit</strong>.
 * <p>
 * Si le prestataire ne répond pas, la commande existe et le stock est réservé : la réponse porte
 * {@code paiementAReprendre} et le client reprend le paiement depuis sa page de suivi, sans risque de doublon.
 * Sur une requête rejouée (même clé d'idempotence), le paiement déjà démarré est retrouvé au lieu d'en créer un autre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriseDeCommandeService {

    private final CommandeService commandeService;
    private final PaiementService paiementService;

    public CommandeCreeeReponse commander(Demandeur demandeur, String cle, CreationCommandeRequete requete,
                                          ContexteRequete contexte) {
        CommandeCreeeReponse creee = commandeService.creer(demandeur, cle, requete, contexte);
        if (creee.statut() != StatutCommande.EN_ATTENTE_PAIEMENT || !creee.modePaiement().enLigne()) {
            return creee;
        }
        try {
            return creee.avecPaiement(paiementService.demarrer(creee.numero(), creee.jetonSuivi()), false);
        } catch (PaiementIndisponibleException e) {
            log.warn("Paiement non démarré pour la commande {} : {}", creee.numero(), e.getMessage());
            return creee.avecPaiement(null, true);
        }
    }
}
