package sn.boutique.administration.journal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultation du journal : {@code SUPER_ADMIN} seulement (règle dans SecuriteConfig). Lecture seule, jamais mise en cache. */
@Tag(name = "Administration — journal")
@RestController
@RequestMapping("/api/admin/journal")
@RequiredArgsConstructor
public class AdminJournalControleur {

    private static final int TAILLE_MAX = 100;

    private final JdbcClient jdbc;

    public record LigneJournal(long id, Instant date, Long utilisateurId, String utilisateurEmail, String role,
                               String action, String entiteType, String entiteId, String avant, String apres,
                               String details, String adresseIp, String methode, String chemin, Integer statutHttp,
                               boolean generique) {
    }

    public record PageJournal(List<LigneJournal> lignes, long total, int page, int taille) {
    }

    @Operation(summary = "Lire le journal des actions d'administration (SUPER_ADMIN)")
    @GetMapping
    public ResponseEntity<PageJournal> lister(
            @RequestParam(required = false) String auteur,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entiteType,
            @RequestParam(required = false) String entiteId,
            @RequestParam(required = false) Instant du,
            @RequestParam(required = false) Instant au,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        int tailleBornee = Math.max(1, Math.min(taille, TAILLE_MAX));
        int pageBornee = Math.max(0, page);
        List<String> conditions = new ArrayList<>();
        List<Object[]> parametres = new ArrayList<>();
        if (auteur != null && !auteur.isBlank()) {
            conditions.add("lower(utilisateur_email) like :auteur");
            parametres.add(new Object[] {"auteur", "%" + auteur.strip().toLowerCase().replace("%", "") + "%"});
        }
        if (action != null && !action.isBlank()) {
            conditions.add("action = :action");
            parametres.add(new Object[] {"action", action.strip()});
        }
        if (entiteType != null && !entiteType.isBlank()) {
            conditions.add("entite_type = :entiteType");
            parametres.add(new Object[] {"entiteType", entiteType.strip()});
        }
        if (entiteId != null && !entiteId.isBlank()) {
            conditions.add("entite_id = :entiteId");
            parametres.add(new Object[] {"entiteId", entiteId.strip()});
        }
        if (du != null) {
            conditions.add("date_action >= :du");
            parametres.add(new Object[] {"du", Timestamp.from(du)});
        }
        if (au != null) {
            conditions.add("date_action < :au");
            parametres.add(new Object[] {"au", Timestamp.from(au)});
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);

        var comptage = jdbc.sql("select count(*) from journal_administration" + where);
        var requete = jdbc.sql("""
                select id, date_action, utilisateur_id, utilisateur_email, role, action, entite_type, entite_id,
                       cast(avant as text), cast(apres as text), details, adresse_ip, methode, chemin, statut_http, generique
                  from journal_administration""" + where + " order by date_action desc, id desc limit :limite offset :decalage");
        for (Object[] p : parametres) {
            comptage = comptage.param((String) p[0], p[1]);
            requete = requete.param((String) p[0], p[1]);
        }
        long total = comptage.query(Long.class).single();
        List<LigneJournal> lignes = requete
                .param("limite", tailleBornee).param("decalage", (long) pageBornee * tailleBornee)
                .query((rs, n) -> new LigneJournal(rs.getLong(1), rs.getTimestamp(2).toInstant(),
                        (Long) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getString(13), rs.getString(14), (Integer) rs.getObject(15), rs.getBoolean(16)))
                .list();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PageJournal(lignes, total, pageBornee, tailleBornee));
    }
}
