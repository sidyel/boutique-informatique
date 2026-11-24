package sn.boutique.catalogue.modele;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Ancien slug d'une entité, pour répondre par une redirection 301 vers l'URL actuelle.
 * Référence polymorphe (type + identifiant), donc sans clé étrangère.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoriqueSlug extends EntiteAuditee {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeEntiteSlug typeEntite;

    @Column(nullable = false, length = 220)
    private String ancienSlug;

    @Column(nullable = false)
    private Long entiteId;

    public HistoriqueSlug(TypeEntiteSlug typeEntite, String ancienSlug, Long entiteId) {
        this.typeEntite = typeEntite;
        this.ancienSlug = ancienSlug;
        this.entiteId = entiteId;
    }
}
