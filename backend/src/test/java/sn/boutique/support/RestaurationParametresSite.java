package sn.boutique.support;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Filet de sécurité contre les tests qui modifient {@code parametres_site}, ligne unique partagée par toute la suite
 * (boutique physique, adresse, délais de paiement, mentions, politique de retour…).
 * <p>
 * Avant chaque test, la ligne est photographiée en entier ; après, si elle a changé, elle est remise
 * <strong>colonne par colonne</strong> dans l'état trouvé, quelle que soit la colonne touchée (y compris celles
 * ajoutées plus tard : la liste vient du schéma). C'est la mise en œuvre commune de la règle « un test laisse la base
 * dans l'état où il l'a trouvée » (CLAUDE.md) pour cette ligne : un test n'a pas à la remettre lui-même.
 * <p>
 * Enregistrée automatiquement (service JUnit, {@code junit.jupiter.extensions.autodetection.enabled}). Ne fait rien
 * pour un test sans contexte Spring. Lecture et écriture passent par une connexion à part ; l'écriture n'a lieu que
 * si la ligne a changé, jamais sous le verrou d'une transaction de test encore ouverte.
 */
public class RestaurationParametresSite implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace ESPACE = ExtensionContext.Namespace.create(RestaurationParametresSite.class);
    private static final String PHOTO = "photo";

    @Override
    public void beforeEach(ExtensionContext contexte) {
        JdbcClient jdbc = jdbc(contexte);
        if (jdbc == null) {
            return;
        }
        try {
            String photo = jdbc.sql("select to_jsonb(p)::text from parametres_site p where id = 1")
                    .query(String.class).optional().orElse(null);
            if (photo != null) {
                contexte.getStore(ESPACE).put(PHOTO, photo);
            }
        } catch (DataAccessException e) {
            // Pas de table (schéma absent dans ce contexte) : rien à protéger
        }
    }

    @Override
    public void afterEach(ExtensionContext contexte) {
        String photo = contexte.getStore(ESPACE).remove(PHOTO, String.class);
        JdbcClient jdbc = photo == null ? null : jdbc(contexte);
        if (jdbc == null) {
            return;
        }
        boolean identique = Boolean.TRUE.equals(jdbc.sql(
                        "select to_jsonb(p) = cast(:photo as jsonb) from parametres_site p where id = 1")
                .param("photo", photo).query(Boolean.class).optional().orElse(null));
        if (identique) {
            return;
        }
        List<String> colonnes = jdbc.sql("""
                        select column_name from information_schema.columns
                         where table_schema = current_schema() and table_name = 'parametres_site' and column_name <> 'id'
                         order by ordinal_position
                        """).query(String.class).list();
        String affectations = String.join(", ", colonnes.stream().map(c -> "\"" + c + "\" = r.\"" + c + "\"").toList());
        jdbc.sql("update parametres_site p set " + affectations
                        + " from jsonb_populate_record(null::parametres_site, cast(:photo as jsonb)) r where p.id = 1")
                .param("photo", photo).update();
}

    /** Un client JDBC sur la base du contexte Spring du test, ou null si le test n'a pas de contexte Spring. */
    private static JdbcClient jdbc(ExtensionContext contexte) {
        Class<?> classe = contexte.getRequiredTestClass();
        boolean spring = AnnotationSupport.findRepeatableAnnotations(classe, ExtendWith.class).stream()
                .anyMatch(e -> List.of(e.value()).contains(SpringExtension.class));
        if (!spring) {
            return null;
        }
        ApplicationContext application = SpringExtension.getApplicationContext(contexte);
        return application.getBeanProvider(DataSource.class).getIfUnique() == null ? null
                : JdbcClient.create(application.getBean(DataSource.class));
    }
}
