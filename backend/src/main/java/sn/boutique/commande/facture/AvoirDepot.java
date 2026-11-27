package sn.boutique.commande.facture;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès aux avoirs émis (SQL direct, pièces immuables comme les factures). */
@Repository
@RequiredArgsConstructor
public class AvoirDepot {

    private final JdbcClient jdbc;

    /** Un avoir sans son PDF. */
    public record AvoirArchive(long id, String numero, long factureId, Instant dateEmission, String motif,
                               String emisPar, long totalTtc, long totalHt, long totalTva, String pdfSha256) {
    }

    public record LigneAvoir(long ligneFactureId, int quantite, long totalTtc, long totalHt, long montantTva) {
    }

    /** Cumul déjà crédité sur une ligne de facture. */
    public record Credite(int quantite, long totalTtc, long totalHt, long montantTva) {
        static final Credite RIEN = new Credite(0, 0, 0, 0);
    }

    public record NouvelAvoir(String numero, long factureId, Instant dateEmission, String motif, String emisPar,
                              long totalTtc, long totalHt, long totalTva, byte[] pdf, String pdfSha256,
                              List<LigneAvoir> lignes) {
    }

    private static final RowMapper<AvoirArchive> AVOIR = (rs, i) -> new AvoirArchive(rs.getLong("id"),
            rs.getString("numero"), rs.getLong("facture_id"), rs.getTimestamp("date_emission").toInstant(),
            rs.getString("motif"), rs.getString("emis_par"), rs.getLong("total_ttc"), rs.getLong("total_ht"),
            rs.getLong("total_tva"), rs.getString("pdf_sha256"));

    private static final String COLONNES = "id, numero, facture_id, date_emission, motif, emis_par, total_ttc, "
            + "total_ht, total_tva, pdf_sha256";

    public long inserer(NouvelAvoir a) {
        long id = jdbc.sql("""
                        insert into avoir (numero, facture_id, date_emission, motif, emis_par, total_ttc, total_ht,
                            total_tva, pdf, pdf_sha256)
                        values (:numero, :facture, :date, :motif, :par, :ttc, :ht, :tva, :pdf, :sha)
                        returning id
                        """)
                .param("numero", a.numero()).param("facture", a.factureId())
                .param("date", java.sql.Timestamp.from(a.dateEmission())).param("motif", a.motif())
                .param("par", a.emisPar()).param("ttc", a.totalTtc()).param("ht", a.totalHt())
                .param("tva", a.totalTva()).param("pdf", a.pdf()).param("sha", a.pdfSha256())
                .query(Long.class).single();
        for (LigneAvoir l : a.lignes()) {
            jdbc.sql("""
                            insert into ligne_avoir (avoir_id, ligne_facture_id, quantite, total_ttc, total_ht,
                                montant_tva)
                            values (:avoir, :ligne, :quantite, :ttc, :ht, :tva)
                            """)
                    .param("avoir", id).param("ligne", l.ligneFactureId()).param("quantite", l.quantite())
                    .param("ttc", l.totalTtc()).param("ht", l.totalHt()).param("tva", l.montantTva()).update();
        }
        return id;
    }

    /** Cumul des avoirs déjà émis, par ligne de facture. */
    public Map<Long, Credite> creditesParLigne(long factureId) {
        return jdbc.sql("""
                        select la.ligne_facture_id, sum(la.quantite) as quantite, sum(la.total_ttc) as ttc,
                               sum(la.total_ht) as ht, sum(la.montant_tva) as tva
                          from ligne_avoir la join avoir a on a.id = la.avoir_id
                         where a.facture_id = :f group by la.ligne_facture_id
                        """).param("f", factureId)
                .query((rs, i) -> Map.entry(rs.getLong("ligne_facture_id"), new Credite(rs.getInt("quantite"),
                        rs.getLong("ttc"), rs.getLong("ht"), rs.getLong("tva"))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<AvoirArchive> parFacture(long factureId) {
        return jdbc.sql("select " + COLONNES + " from avoir where facture_id = :f order by id").param("f", factureId)
                .query(AVOIR).list();
    }

    public Optional<AvoirArchive> trouverParNumero(String numero) {
        return jdbc.sql("select " + COLONNES + " from avoir where numero = :n").param("n", numero).query(AVOIR)
                .optional();
    }

    public List<LigneAvoir> lignes(long avoirId) {
        return jdbc.sql("select ligne_facture_id, quantite, total_ttc, total_ht, montant_tva from ligne_avoir "
                        + "where avoir_id = :a order by id").param("a", avoirId)
                .query((rs, i) -> new LigneAvoir(rs.getLong("ligne_facture_id"), rs.getInt("quantite"),
                        rs.getLong("total_ttc"), rs.getLong("total_ht"), rs.getLong("montant_tva"))).list();
    }

    public Optional<byte[]> pdf(String numero) {
        return jdbc.sql("select pdf from avoir where numero = :n").param("n", numero).query(byte[].class).optional();
    }
}
