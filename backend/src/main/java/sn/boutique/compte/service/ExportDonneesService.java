package sn.boutique.compte.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.dto.MesDonneesReponse;
import sn.boutique.compte.dto.MesDonneesReponse.Adresse;
import sn.boutique.compte.dto.MesDonneesReponse.Avis;
import sn.boutique.compte.dto.MesDonneesReponse.ChangementEmail;
import sn.boutique.compte.dto.MesDonneesReponse.Commande;
import sn.boutique.compte.dto.MesDonneesReponse.Consentement;
import sn.boutique.compte.dto.MesDonneesReponse.Etape;
import sn.boutique.compte.dto.MesDonneesReponse.Ligne;
import sn.boutique.compte.dto.MesDonneesReponse.Paiement;
import sn.boutique.compte.dto.MesDonneesReponse.Profil;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Droit d'accès : rassemble les données personnelles du client (profil, adresses, commandes, consentements, changements
 * d'e-mail, avis). Le mot de passe actuel est exigé par l'appelant avant d'appeler ce service.
 */
@Service
@RequiredArgsConstructor
public class ExportDonneesService {

    private static final String NOTE = "Données personnelles détenues par la boutique sur votre compte. "
            + "Les factures et avoirs (PDF) se téléchargent depuis « Mes commandes ».";

    private final UtilisateurDepot utilisateurDepot;
    private final VerificationMotDePasse verification;
    private final JdbcClient jdbc;
    private final Clock horloge;

    @Transactional(readOnly = true)
    public MesDonneesReponse exporter(Long id, String motDePasse) {
        Utilisateur u = utilisateurDepot.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Compte", id));
        verification.exiger(u, motDePasse);

        Profil profil = new Profil(u.getEmail(), u.getPrenom(), u.getNom(), u.getTelephone(), u.isEmailVerifie(),
                u.isConsentementMarketing(), u.getDateCreation());
        List<Adresse> adresses = jdbc.sql("""
                select libelle, nom_complet, telephone, ville, quartier, adresse_ligne, point_de_repere, par_defaut
                  from adresse where utilisateur_id = :u order by par_defaut desc, id""").param("u", id)
                .query((rs, n) -> new Adresse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8))).list();
        List<Consentement> consentements = jdbc.sql("""
                select consentement, source, date_decision from historique_consentement
                 where utilisateur_id = :u order by date_decision, id""").param("u", id)
                .query((rs, n) -> new Consentement(rs.getBoolean(1), rs.getString(2), instant(rs.getTimestamp(3)))).list();
        List<ChangementEmail> changements = jdbc.sql("""
                select ancien_email, nouvel_email, statut, date_creation from changement_email
                 where utilisateur_id = :u order by date_creation, id""").param("u", id)
                .query((rs, n) -> new ChangementEmail(rs.getString(1), rs.getString(2), rs.getString(3),
                        instant(rs.getTimestamp(4)))).list();
        List<Avis> avis = jdbc.sql("""
                select p.nom, a.note, a.titre, a.commentaire, a.statut, a.achat_verifie, a.date_creation
                  from avis a join produit p on p.id = a.produit_id
                 where a.utilisateur_id = :u order by a.date_creation desc, a.id desc""").param("u", id)
                .query((rs, n) -> new Avis(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6), instant(rs.getTimestamp(7)))).list();
        List<String> rattachees = jdbc.sql("""
                select c.numero from rattachement_commande r join commande c on c.id = r.commande_id
                 where r.utilisateur_id = :u order by c.date_creation""").param("u", id).query(String.class).list();

        return new MesDonneesReponse(NOTE, horloge.instant(), profil, adresses, commandes(id), consentements,
                changements, avis, rattachees);
    }

    private List<Commande> commandes(Long id) {
        record Entete(long id, Commande commande) {
        }
        return jdbc.sql("""
                select c.id, c.numero, c.date_creation, c.statut, c.mode_livraison, c.mode_paiement,
                       c.livraison_nom_complet, c.livraison_telephone, c.livraison_ville, c.livraison_quartier,
                       c.livraison_adresse_ligne, c.livraison_point_de_repere, c.sous_total, c.frais_livraison,
                       c.remise, c.total, c.code_promo, f.numero
                  from commande c left join facture f on f.commande_id = c.id
                 where c.utilisateur_id = :u order by c.date_creation desc, c.id desc""").param("u", id)
                .query((rs, n) -> new Entete(rs.getLong(1), new Commande(rs.getString(2), instant(rs.getTimestamp(3)),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getLong(13),
                        rs.getLong(14), rs.getLong(15), rs.getLong(16), rs.getString(17), rs.getString(18),
                        List.of(), List.of(), List.of(), List.of()))).list().stream()
                .map(e -> {
                    Commande c = e.commande();
                    return new Commande(c.numero(), c.date(), c.statut(), c.modeLivraison(), c.modePaiement(),
                            c.livraisonNomComplet(), c.livraisonTelephone(), c.livraisonVille(), c.livraisonQuartier(),
                            c.livraisonAdresse(), c.livraisonPointDeRepere(), c.sousTotal(), c.fraisLivraison(),
                            c.remise(), c.total(), c.codePromo(), c.numeroFacture(), avoirs(e.id()), lignes(e.id()),
                            historique(e.id()), paiements(e.id()));
                }).toList();
    }

    private List<String> avoirs(long commandeId) {
        return jdbc.sql("""
                select a.numero from avoir a join facture f on f.id = a.facture_id
                 where f.commande_id = :c order by a.date_emission""").param("c", commandeId).query(String.class).list();
    }

    private List<Ligne> lignes(long commandeId) {
        return jdbc.sql("""
                select nom_produit, libelle_variante, sku, quantite, prix_unitaire, total_ligne
                  from ligne_commande where commande_id = :c order by id""").param("c", commandeId)
                .query((rs, n) -> new Ligne(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getLong(5), rs.getLong(6))).list();
    }

    private List<Etape> historique(long commandeId) {
        return jdbc.sql("""
                select nouveau_statut, date_creation from historique_statut_commande
                 where commande_id = :c order by date_creation, id""").param("c", commandeId)
                .query((rs, n) -> new Etape(rs.getString(1), instant(rs.getTimestamp(2)))).list();
    }

    private List<Paiement> paiements(long commandeId) {
        return jdbc.sql("""
                select fournisseur, montant, statut, date_creation from paiement
                 where commande_id = :c order by date_creation, id""").param("c", commandeId)
                .query((rs, n) -> new Paiement(rs.getString(1), rs.getLong(2), rs.getString(3),
                        instant(rs.getTimestamp(4)))).list();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
