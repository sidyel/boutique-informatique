package sn.boutique.noyau.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base de toutes les entités : identifiant et dates d'audit remplies automatiquement.
 * <p>
 * L'égalité repose sur l'identifiant (deux entités sans identifiant ne sont jamais égales),
 * avec un hashCode constant par classe : sûr avec les proxies Hibernate et dans les Set.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntiteAuditee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant dateCreation;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant dateModification;

    @Override
    public final boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (autre == null || classeReelle(this) != classeReelle(autre)) {
            return false;
        }
        Long autreId = autre instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getIdentifier()
                : ((EntiteAuditee) autre).getId();
        return id != null && id.equals(autreId);
    }

    @Override
    public final int hashCode() {
        return classeReelle(this).hashCode();
    }

    private static Class<?> classeReelle(Object objet) {
        return objet instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : objet.getClass();
    }
}
