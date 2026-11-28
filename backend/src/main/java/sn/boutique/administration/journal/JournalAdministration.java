package sn.boutique.administration.journal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sn.boutique.authentification.service.JetonAccesService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal des actions d'administration (ADR 0009 §9) : qui, quoi, quand, avec quelles valeurs avant et après.
 * <p>
 * Les services appellent {@code enregistrer} <strong>dans la même transaction</strong> que la modification : action annulée,
 * ligne annulée. Un intercepteur ({@link JournalInterceptor}) ajoute une ligne générique à toute écriture d'administration
 * réussie qui n'en aurait pas écrit : aucune action n'échappe au journal.
 * <p>
 * <strong>Aucune donnée client dans les valeurs</strong> : seuls des identifiants, statuts, montants et champs de
 * configuration. Par sécurité, les clés qui désignent une donnée personnelle ({@link #CLES_MASQUEES}) sont remplacées par
 * « [masqué] » même si un appelant les fournit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalAdministration {

    /** Attribut de requête posé dès qu'une ligne a été écrite : l'intercepteur n'ajoute alors pas de ligne générique. */
    static final String ATTRIBUT_ECRIT = JournalAdministration.class.getName() + ".ecrit";

    /** Clés (en minuscules, sans séparateur) dont la valeur est une donnée personnelle ou un secret. */
    public static final Set<String> CLES_MASQUEES = Set.of("nom", "prenom", "nomcomplet", "email", "telephone", "adresse",
            "adresseligne", "quartier", "ville", "pointderepere", "motdepasse", "motdepassehash", "jeton", "secret", "totp");
    static final String MASQUE = "[masqué]";
    private static final int LONGUEUR_DETAILS = 500;

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final JsonMapper jsonMapper;

    /** Qui agit : l'utilisateur du jeton d'accès courant, ou « système » (tâche planifiée, événement de sécurité sans session). */
    public record Acteur(Long id, String email, String role) {

        public static final Acteur SYSTEME = new Acteur(null, "systeme", "SYSTEME");
    }

    public void enregistrer(String action, String entiteType, Object entiteId, Map<String, ?> avant, Map<String, ?> apres) {
        enregistrer(action, entiteType, entiteId, avant, apres, null);
    }

    public void enregistrer(String action, String entiteType, Object entiteId, Map<String, ?> avant, Map<String, ?> apres,
                            String details) {
        ecrire(acteurCourant(), action, entiteType, entiteId, avant, apres, details, false, null);
    }

    /** Événement de sécurité d'un compte qui n'a pas (encore) de session : connexion, code de secours… */
    public void enregistrerPour(Acteur acteur, String action, String entiteType, Object entiteId, String details) {
        ecrire(acteur, action, entiteType, entiteId, null, null, details, false, null);
    }

    /** Ligne générique de l'intercepteur : auteur, route, statut. */
    void enregistrerGenerique(HttpServletRequest requete, int statut) {
        ecrire(acteurCourant(), "REQUETE_ECRITURE", null, null, null, null, null, true, statut);
    }

    private void ecrire(Acteur acteur, String action, String entiteType, Object entiteId, Map<String, ?> avant,
                        Map<String, ?> apres, String details, boolean generique, Integer statutForce) {
        HttpServletRequest requete = requeteCourante();
        String ip = requete == null ? null : requete.getRemoteAddr();
        String methode = requete == null ? null : requete.getMethod();
        String chemin = requete == null ? null : tronquer(requete.getRequestURI(), 300);
        Integer statut = statutForce;
        jdbc.sql("""
                        insert into journal_administration (date_action, utilisateur_id, utilisateur_email, role, action,
                            entite_type, entite_id, avant, apres, details, adresse_ip, methode, chemin, statut_http, generique)
                        values (:date, :utilisateurId, :email, :role, :action, :entiteType, :entiteId,
                            cast(:avant as jsonb), cast(:apres as jsonb), :details, :ip, :methode, :chemin, :statut, :generique)""")
                .param("date", Timestamp.from(horloge.instant()))
                .param("utilisateurId", acteur.id(), java.sql.Types.BIGINT)
                .param("email", acteur.email())
                .param("role", acteur.role())
                .param("action", action)
                .param("entiteType", entiteType, java.sql.Types.VARCHAR)
                .param("entiteId", entiteId == null ? null : String.valueOf(entiteId), java.sql.Types.VARCHAR)
                .param("avant", versJson(avant), java.sql.Types.VARCHAR)
                .param("apres", versJson(apres), java.sql.Types.VARCHAR)
                .param("details", details == null ? null : tronquer(details, LONGUEUR_DETAILS), java.sql.Types.VARCHAR)
                .param("ip", ip, java.sql.Types.VARCHAR)
                .param("methode", methode, java.sql.Types.VARCHAR)
                .param("chemin", chemin, java.sql.Types.VARCHAR)
                .param("statut", statut, java.sql.Types.INTEGER)
                .param("generique", generique)
                .update();
        if (requete != null) {
            requete.setAttribute(ATTRIBUT_ECRIT, Boolean.TRUE);
        }
        log.info("JOURNAL {} par {} ({}){}", action, acteur.email(), acteur.role(),
                entiteType == null ? "" : " sur " + entiteType + " " + entiteId);
    }

    /** Valeurs au format JSON, données personnelles masquées. Vide si aucune valeur. */
    String versJson(Map<String, ?> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return null;
        }
        return jsonMapper.writeValueAsString(masquer(valeurs));
    }

    static Map<String, Object> masquer(Map<String, ?> valeurs) {
        Map<String, Object> sortie = new LinkedHashMap<>();
        valeurs.forEach((cle, valeur) -> {
            String normalisee = cle.toLowerCase(Locale.ROOT).replace("_", "");
            if (CLES_MASQUEES.contains(normalisee)) {
                sortie.put(cle, MASQUE);
            } else if (valeur instanceof Map<?, ?> imbrique) {
                Map<String, Object> copie = new LinkedHashMap<>();
                imbrique.forEach((k, v) -> copie.put(String.valueOf(k), v));
                sortie.put(cle, masquer(copie));
            } else if (valeur instanceof List<?> liste) {
                sortie.put(cle, liste);
            } else {
                sortie.put(cle, valeur);
            }
        });
        return sortie;
    }

    static Acteur acteurCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jwt) {
            List<String> roles = jwt.getClaimAsStringList(JetonAccesService.REVENDICATION_ROLES);
            String role = roles == null || roles.isEmpty() ? "INCONNU" : roles.get(0);
            String email = jwt.getClaimAsString(JetonAccesService.REVENDICATION_EMAIL);
            return new Acteur(Long.valueOf(jwt.getSubject()), email == null ? "inconnu" : email, role);
        }
        return Acteur.SYSTEME;
    }

    private static HttpServletRequest requeteCourante() {
        RequestAttributes attributs = RequestContextHolder.getRequestAttributes();
        return attributs instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String tronquer(String texte, int max) {
        return texte.length() <= max ? texte : texte.substring(0, max);
    }
}
