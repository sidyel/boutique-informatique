package sn.boutique.authentification.depot;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Échecs de connexion par couple (empreinte de l'e-mail saisi, IP) : ADR 0003 §8.
 * <p>
 * Requêtes SQL directes : l'enregistrement d'un échec est un seul {@code INSERT … ON CONFLICT} atomique
 * (des tentatives simultanées sont toutes comptées) et s'exécute à l'identique que le compte existe ou non.
 */
@Repository
@RequiredArgsConstructor
public class EchecConnexionDepot {

    private final JdbcClient jdbc;

    /** Fin du verrouillage en cours pour ce couple, si le couple est verrouillé à cet instant. */
    public Optional<Instant> verrouillageEnCours(String empreinteEmail, String ip, Instant maintenant) {
        return jdbc.sql("""
                        select verrouille_jusqua from echec_connexion
                        where empreinte_email = :empreinte and adresse_ip = :ip and verrouille_jusqua > :maintenant""")
                .param("empreinte", empreinteEmail).param("ip", ip).param("maintenant", horodatage(maintenant))
                .query(java.sql.Timestamp.class).optional().map(java.sql.Timestamp::toInstant);
    }

    /**
     * Compte un échec. Au {@code seuil}-ième échec consécutif, le couple est verrouillé jusqu'à
     * {@code maintenant + duree} et le compteur repart à zéro.
     *
     * @return true si CET échec vient de verrouiller le couple
     */
    public boolean enregistrerEchec(String empreinteEmail, String ip, Instant maintenant, int seuil, Duration duree) {
        java.sql.Timestamp fin = horodatage(maintenant.plus(duree));
        java.sql.Timestamp verrouille = jdbc.sql("""
                        insert into echec_connexion (empreinte_email, adresse_ip, echecs, verrouille_jusqua,
                                                     date_creation, date_modification)
                        values (:empreinte, :ip,
                                case when 1 >= cast(:seuil as integer) then 0 else 1 end,
                                case when 1 >= cast(:seuil as integer) then cast(:fin as timestamptz) end,
                                :maintenant, :maintenant)
                        on conflict (empreinte_email, adresse_ip) do update set
                            echecs = case when echec_connexion.echecs + 1 >= cast(:seuil as integer) then 0
                                          else echec_connexion.echecs + 1 end,
                            verrouille_jusqua = case when echec_connexion.echecs + 1 >= cast(:seuil as integer) then cast(:fin as timestamptz)
                                                     else echec_connexion.verrouille_jusqua end,
                            date_modification = :maintenant
                        returning verrouille_jusqua""")
                .param("empreinte", empreinteEmail).param("ip", ip).param("seuil", seuil)
                .param("fin", fin).param("maintenant", horodatage(maintenant))
                .query(java.sql.Timestamp.class).optional().orElse(null);
        return fin.equals(verrouille);
    }

    /** Connexion réussie depuis cette IP : son compteur repart de zéro. */
    public void effacer(String empreinteEmail, String ip) {
        jdbc.sql("delete from echec_connexion where empreinte_email = :empreinte and adresse_ip = :ip")
                .param("empreinte", empreinteEmail).param("ip", ip).update();
    }

    /** Lien de déverrouillage ou nouveau mot de passe : tous les verrouillages de cet e-mail sont levés. */
    public int effacerTout(String empreinteEmail) {
        return jdbc.sql("delete from echec_connexion where empreinte_email = :empreinte")
                .param("empreinte", empreinteEmail).update();
    }

    /** Purge : lignes inactives depuis {@code limite} et qui ne verrouillent plus rien. */
    public int supprimerInactifsAvant(Instant limite) {
        return jdbc.sql("""
                        delete from echec_connexion
                        where date_modification < :limite and (verrouille_jusqua is null or verrouille_jusqua < :limite)""")
                .param("limite", horodatage(limite)).update();
    }

    private static java.sql.Timestamp horodatage(Instant instant) {
        return java.sql.Timestamp.from(instant);
    }
}
