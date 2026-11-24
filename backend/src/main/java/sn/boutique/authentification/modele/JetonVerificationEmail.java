package sn.boutique.authentification.modele;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Jeton envoyé par e-mail pour vérifier l'adresse (24 h, usage unique).
 * <p>
 * Seule l'empreinte SHA-256 du jeton est stockée : une fuite de la base ne donne aucun jeton utilisable.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JetonVerificationEmail extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** SHA-256 du jeton, en hexadécimal. */
    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Column(nullable = false)
    private Instant dateExpiration;

    /** Date d'utilisation : un jeton utilisé ne peut plus servir. */
    private Instant dateUtilisation;

    public JetonVerificationEmail(Utilisateur utilisateur, String empreinte, Instant dateExpiration) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
        this.dateExpiration = dateExpiration;
    }

    public boolean estUtilisable(Instant maintenant) {
        return dateUtilisation == null && dateExpiration.isAfter(maintenant);
    }

    public void marquerUtilise(Instant maintenant) {
        this.dateUtilisation = maintenant;
    }
}
