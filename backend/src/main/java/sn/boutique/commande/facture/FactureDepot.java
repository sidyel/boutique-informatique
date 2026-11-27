package sn.boutique.commande.facture;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.boutique.contenu.modele.RegimeTva;

/**
 * Accès aux factures émises. Écriture et lecture en SQL direct (pas d'entité JPA) : une facture est une pièce
 * immuable, insérée une fois, jamais chargée pour être modifiée, et son PDF n'est lu que sur demande.
 */
@Repository
@RequiredArgsConstructor
public class FactureDepot {

    private final JdbcClient jdbc;

    public enum TypeLigne { ARTICLE, LIVRAISON }

    /** Une facture sans son PDF. */
    public record FactureArchivee(long id, String numero, long commandeId, Instant dateEmission,
                                  String vendeurRaisonSociale, String vendeurNinea, String vendeurRccm,
                                  String vendeurAdresse, RegimeTva regimeTva, int tauxTvaBp, String mentionTva,
                                  String clientNom, String clientTelephone, String clientEmail, String clientAdresse,
                                  String modePaiement, long totalTtc, long totalHt, long totalTva, String pdfSha256) {
    }

    public record LigneArchivee(long id, int rang, TypeLigne type, Long ligneCommandeId, String sku,
                                String designation, int quantite, long prixUnitaireTtc, long totalBrutTtc,
                                long remiseTtc, long totalTtc, long totalHt, long montantTva) {
    }

    /** Ligne à insérer. */
    public record NouvelleLigne(TypeLigne type, Long ligneCommandeId, String sku, String designation, int quantite,
                                long prixUnitaireTtc, long totalBrutTtc, long remiseTtc, long totalTtc, long totalHt,
                                long montantTva) {
    }

    /** Facture à insérer ; le numéro vient du compteur, dans la même transaction. */
    public record NouvelleFacture(String numero, long commandeId, Instant dateEmission,
                                  String vendeurRaisonSociale, String vendeurNinea, String vendeurRccm,
                                  String vendeurAdresse, RegimeTva regimeTva, int tauxTvaBp, String mentionTva,
                                  String clientNom, String clientTelephone, String clientEmail, String clientAdresse,
                                  String modePaiement, long totalTtc, long totalHt, long totalTva,
                                  byte[] pdf, String pdfSha256, List<NouvelleLigne> lignes) {
    }

    private static final String COLONNES = """
            id, numero, commande_id, date_emission, vendeur_raison_sociale, vendeur_ninea, vendeur_rccm,
            vendeur_adresse, regime_tva, taux_tva_bp, mention_tva, client_nom, client_telephone, client_email,
            client_adresse, mode_paiement, total_ttc, total_ht, total_tva, pdf_sha256
            """;

    private static final RowMapper<FactureArchivee> FACTURE = (rs, i) -> new FactureArchivee(rs.getLong("id"),
            rs.getString("numero"), rs.getLong("commande_id"), rs.getTimestamp("date_emission").toInstant(),
            rs.getString("vendeur_raison_sociale"), rs.getString("vendeur_ninea"), rs.getString("vendeur_rccm"),
            rs.getString("vendeur_adresse"), RegimeTva.valueOf(rs.getString("regime_tva")), rs.getInt("taux_tva_bp"),
            rs.getString("mention_tva"), rs.getString("client_nom"), rs.getString("client_telephone"),
            rs.getString("client_email"), rs.getString("client_adresse"), rs.getString("mode_paiement"),
            rs.getLong("total_ttc"), rs.getLong("total_ht"), rs.getLong("total_tva"), rs.getString("pdf_sha256"));

    private static final RowMapper<LigneArchivee> LIGNE = (rs, i) -> new LigneArchivee(rs.getLong("id"),
            rs.getInt("rang"), TypeLigne.valueOf(rs.getString("type")), objet(rs, "ligne_commande_id"),
            rs.getString("sku"), rs.getString("designation"), rs.getInt("quantite"), rs.getLong("prix_unitaire_ttc"),
            rs.getLong("total_brut_ttc"), rs.getLong("remise_ttc"), rs.getLong("total_ttc"), rs.getLong("total_ht"),
            rs.getLong("montant_tva"));

    private static Long objet(ResultSet rs, String colonne) throws SQLException {
        long valeur = rs.getLong(colonne);
        return rs.wasNull() ? null : valeur;
    }

    /** Insère la facture et ses lignes ; renvoie son identifiant. À appeler dans la transaction d'émission. */
    public long inserer(NouvelleFacture f) {
        long id = jdbc.sql("""
                        insert into facture (numero, commande_id, date_emission, vendeur_raison_sociale, vendeur_ninea,
                            vendeur_rccm, vendeur_adresse, regime_tva, taux_tva_bp, mention_tva, client_nom,
                            client_telephone, client_email, client_adresse, mode_paiement, total_ttc, total_ht,
                            total_tva, pdf, pdf_sha256)
                        values (:numero, :commande, :date, :raison, :ninea, :rccm, :adresseVendeur, :regime, :taux,
                            :mention, :nom, :telephone, :email, :adresseClient, :mode, :ttc, :ht, :tva, :pdf, :sha)
                        returning id
                        """)
                .param("numero", f.numero()).param("commande", f.commandeId())
                .param("date", java.sql.Timestamp.from(f.dateEmission()))
                .param("raison", f.vendeurRaisonSociale()).param("ninea", f.vendeurNinea())
                .param("rccm", f.vendeurRccm()).param("adresseVendeur", f.vendeurAdresse())
                .param("regime", f.regimeTva().name()).param("taux", f.tauxTvaBp()).param("mention", f.mentionTva())
                .param("nom", f.clientNom()).param("telephone", f.clientTelephone()).param("email", f.clientEmail())
                .param("adresseClient", f.clientAdresse()).param("mode", f.modePaiement())
                .param("ttc", f.totalTtc()).param("ht", f.totalHt()).param("tva", f.totalTva())
                .param("pdf", f.pdf()).param("sha", f.pdfSha256())
                .query(Long.class).single();
        int rang = 1;
        for (NouvelleLigne l : f.lignes()) {
            jdbc.sql("""
                            insert into ligne_facture (facture_id, rang, type, ligne_commande_id, sku, designation,
                                quantite, prix_unitaire_ttc, total_brut_ttc, remise_ttc, total_ttc, total_ht,
                                montant_tva)
                            values (:facture, :rang, :type, :ligneCommande, :sku, :designation, :quantite, :pu, :brut,
                                :remise, :ttc, :ht, :tva)
                            """)
                    .param("facture", id).param("rang", rang++).param("type", l.type().name())
                    .param("ligneCommande", l.ligneCommandeId()).param("sku", l.sku())
                    .param("designation", l.designation()).param("quantite", l.quantite())
                    .param("pu", l.prixUnitaireTtc()).param("brut", l.totalBrutTtc()).param("remise", l.remiseTtc())
                    .param("ttc", l.totalTtc()).param("ht", l.totalHt()).param("tva", l.montantTva()).update();
        }
        return id;
    }

    public Optional<FactureArchivee> trouverParCommande(long commandeId) {
        return jdbc.sql("select " + COLONNES + " from facture where commande_id = :c").param("c", commandeId)
                .query(FACTURE).optional();
    }

    public Optional<FactureArchivee> trouverParNumero(String numero) {
        return jdbc.sql("select " + COLONNES + " from facture where numero = :n").param("n", numero)
                .query(FACTURE).optional();
    }

    public List<LigneArchivee> lignes(long factureId) {
        return jdbc.sql("""
                        select id, rang, type, ligne_commande_id, sku, designation, quantite, prix_unitaire_ttc,
                               total_brut_ttc, remise_ttc, total_ttc, total_ht, montant_tva
                          from ligne_facture where facture_id = :f order by rang
                        """).param("f", factureId).query(LIGNE).list();
    }

    /** Numéro lisible de la commande d'une facture. */
    public String numeroCommande(long commandeId) {
        return jdbc.sql("select numero from commande where id = :i").param("i", commandeId).query(String.class)
                .single();
    }

    /** Le PDF figé de la facture. */
    public Optional<byte[]> pdf(String numero) {
        return jdbc.sql("select pdf from facture where numero = :n").param("n", numero).query(byte[].class).optional();
    }
}
