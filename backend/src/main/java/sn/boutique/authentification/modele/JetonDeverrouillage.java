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
 * Lien envoyé au titulaire quand son compte est verrouillé après des échecs de connexion (1 h, usage unique) :
 * il lève tous les verrouillages de son adresse, quelle que soit l'IP (ADR 0003 §8).
 * <p>
 * Seule l'empreinte SHA-256 du jeton est stockée.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JetonDeverrouillage extends EntiteAuditee {

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

    public JetonDeverrouillage(Utilisateur utilisateur, String empreinte, Instant dateExpiration) {
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
