package sn.boutique.compte.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** « Mes avis » : les avis laissés par le client, quel que soit leur état de modération. */
@Service
@RequiredArgsConstructor
public class AvisCompteService {

    /**
     * @param statut EN_ATTENTE (en cours de modération), APPROUVE (publié) ou REJETE
     */
    public record MonAvis(long id, String produit, String slugProduit, int note, String titre, String commentaire,
                          String statut, boolean achatVerifie, Instant date, String reponseBoutique) {
    }

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public List<MonAvis> lister(Long utilisateurId) {
        return jdbc.sql("""
                select a.id, p.nom, p.slug, a.note, a.titre, a.commentaire, a.statut, a.achat_verifie,
                       a.date_creation, a.reponse_boutique
                  from avis a join produit p on p.id = a.produit_id
                 where a.utilisateur_id = :u order by a.date_creation desc, a.id desc""").param("u", utilisateurId)
                .query((rs, n) -> new MonAvis(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8),
                        ((Timestamp) rs.getTimestamp(9)).toInstant(), rs.getString(10))).list();
    }
}
