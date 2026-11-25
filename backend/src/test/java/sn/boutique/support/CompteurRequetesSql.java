package sn.boutique.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Compte les requêtes SQL envoyées à la base, pour détecter les N+1 dans les tests.
 * <p>
 * Enveloppe la {@link DataSource} : chaque {@code prepareStatement}, {@code prepareCall} ou
 * {@code createStatement} compte pour une requête. Contrairement aux statistiques d'Hibernate, les
 * requêtes natives ({@code JdbcClient}) sont aussi comptées. Les tests tournent les uns après les autres :
 * un compteur global suffit.
 * <pre>
 * CompteurRequetesSql.reinitialiser();
 * mockMvc.perform(get("/api/categories"));
 * assertThat(CompteurRequetesSql.nombre()).isEqualTo(1);
 * </pre>
 */
public final class CompteurRequetesSql implements BeanPostProcessor {

    private static final AtomicInteger COMPTEUR = new AtomicInteger();
    private static final Set<String> CREATIONS_DE_REQUETE = Set.of("prepareStatement", "prepareCall", "createStatement");

    public static void reinitialiser() {
        COMPTEUR.set(0);
    }

    public static int nombre() {
        return COMPTEUR.get();
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String nom) {
        if (bean instanceof DataSource source && !Proxy.isProxyClass(bean.getClass())) {
            return envelopper(source, DataSource.class, (cible, methode, arguments) -> {
                Object resultat = appeler(cible, methode, arguments);
                return resultat instanceof Connection connexion ? envelopperConnexion(connexion) : resultat;
            });
        }
        return bean;
    }

    private static Connection envelopperConnexion(Connection connexion) {
        return envelopper(connexion, Connection.class, (cible, methode, arguments) -> {
            if (CREATIONS_DE_REQUETE.contains(methode.getName())) {
                COMPTEUR.incrementAndGet();
            }
            return appeler(cible, methode, arguments);
        });
    }

    @FunctionalInterface
    private interface Interception<T> {
        Object intercepter(T cible, Method methode, Object[] arguments) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T envelopper(T cible, Class<T> type, Interception<T> interception) {
        InvocationHandler gestionnaire = (proxy, methode, arguments) -> interception.intercepter(cible, methode, arguments);
        return (T) Proxy.newProxyInstance(CompteurRequetesSql.class.getClassLoader(), new Class<?>[] {type}, gestionnaire);
    }

    private static Object appeler(Object cible, Method methode, Object[] arguments) throws Throwable {
        try {
            return methode.invoke(cible, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
