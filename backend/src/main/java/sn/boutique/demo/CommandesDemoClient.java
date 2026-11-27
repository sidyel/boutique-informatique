package sn.boutique.demo;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.ZoneLivraisonDepot;
import sn.boutique.commande.dto.CommandeCreeeReponse;
import sn.boutique.commande.dto.CreationCommandeRequete;
import sn.boutique.commande.facture.EncaissementService;
import sn.boutique.commande.facture.EncaissementService.LivraisonRequete;
import sn.boutique.commande.facture.EncaissementService.MoyenEncaissement;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.commande.service.CommandeService.ContexteRequete;
import sn.boutique.commande.service.GestionCommandeService;
import sn.boutique.commande.service.PriseDeCommandeService;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.panier.service.PanierService;
import sn.boutique.panier.service.PanierService.Demandeur;

/**
 * Donne au compte client de démonstration trois commandes réalistes, pour que les mesures Lighthouse et les captures
 * montrent une liste remplie : une livrée et <strong>facturée</strong>, une confirmée, une en attente d'appel.
 * <p>
 * Elles passent par les vrais services (panier, prise de commande, confirmation, encaissement) : mêmes règles, même
 * facture que sur le site. Idempotent : rien n'est créé si le compte a déjà des commandes. Profil « demo » seulement.
 */
@Slf4j
@Service
@Profile("demo")
@RequiredArgsConstructor
public class CommandesDemoClient {

    private static final String AUTEUR = ImportDonneesDemo.AUTEUR;
    private static final ContexteRequete CONTEXTE = new ContexteRequete("127.0.0.1", "Amorçage des données de démonstration");

    private final ProprietesDemo proprietes;
    private final UtilisateurDepot utilisateurDepot;
    private final CommandeDepot commandeDepot;
    private final ZoneLivraisonDepot zoneDepot;
    private final PanierService panierService;
    private final PriseDeCommandeService priseDeCommande;
    private final GestionCommandeService gestionCommande;
    private final EncaissementService encaissement;
    private final JdbcClient jdbc;
    private final EntityManager entityManager;

    /** Une seule transaction : les trois commandes sont créées ensemble ou pas du tout. */
    @Transactional
    public void creer() {
        if (proprietes.clientEmail() == null) {
            return;
        }
        Utilisateur client = utilisateurDepot.findByEmail(proprietes.clientEmail().toLowerCase()).orElse(null);
        if (client == null || commandeDepot.findByUtilisateurIdOrderByDateCreationDesc(client.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).hasContent()) {
            return;
        }
        List<Long> variantes = jdbc.sql("""
                select v.id from variante_produit v join produit p on p.id = v.produit_id
                 where v.actif and p.actif and p.date_retrait is null and v.stock >= 5
                 order by v.prix, v.id limit 3""").query(Long.class).list();
        List<ZoneLivraison> zones = zoneDepot.findByActifTrueOrderByTarifAsc();
        if (variantes.size() < 3 || zones.isEmpty()) {
            log.warn("Commandes de démonstration du client non créées : catalogue ou zones insuffisants");
            return;
        }
        Demandeur demandeur = new Demandeur(client.getId(), null);
        // La limite « 2 commandes à la livraison en attente par numéro » impose de confirmer au fur et à mesure
        String livree = commander(demandeur, variantes.get(0), zones.get(0));
        gestionCommande.confirmer(livree, AUTEUR);
        String confirmee = commander(demandeur, variantes.get(1), zones.get(0));
        gestionCommande.confirmer(confirmee, AUTEUR);
        commander(demandeur, variantes.get(2), zones.get(0));   // reste « à confirmer par téléphone »

        long total = commandeDepot.findByNumero(livree).orElseThrow().getTotal();
        encaissement.livrer(livree, AUTEUR, null,
                new LivraisonRequete(total, MoyenEncaissement.ESPECES, null, "Livreur démonstration", null, null));
        log.info("Commandes de démonstration créées pour {} : {} (livrée et facturée), {} (confirmée), 1 à confirmer",
                client.getEmail(), livree, confirmee);
    }

    private String commander(Demandeur demandeur, long varianteId, ZoneLivraison zone) {
        // Panier du compte de démonstration vidé d'abord : un article resté d'une mesure précédente fausserait le total
        panierService.panierPourCommande(demandeur).ifPresent(panierService::vider);
        entityManager.flush();   // suppressions écrites avant les insertions (même variante, même panier)
        var panier = panierService.ajouter(demandeur, varianteId, 1, zone.getId()).panier();
        CommandeCreeeReponse reponse = priseDeCommande.commander(demandeur, UUID.randomUUID().toString().replace("-", ""),
                new CreationCommandeRequete("Awa Démo", "77 000 00 01", null, ModeLivraison.DOMICILE, zone.getId(),
                        "Dakar", "Plateau", "Rue de démonstration", "Près du marché", null, ModePaiement.A_LA_LIVRAISON,
                        panier.total(), null),
                CONTEXTE);
        return reponse.numero();
    }
}
