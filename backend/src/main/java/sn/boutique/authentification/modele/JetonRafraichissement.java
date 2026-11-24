package sn.boutique.authentification.modele;

import java.time.Instant;
import java.util.UUID;

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
 * Jeton de rafraîchissement d'une session, transmis dans un cookie HttpOnly.
 * <p>
 * Rotation : chaque utilisation le marque utilisé et en émet un nouveau dans la même famille.
 * Présenter un jeton déjà utilisé trahit un vol : toute la famille est alors révoquée.
 * <p>
 * Seule l'empreinte SHA-256 du jeton est stockée : une fuite de la base ne donne aucun jeton utilisable.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JetonRafraichissement extends EntiteAuditee {

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

    /** Tous les jetons issus d'une même connexion partagent la même famille. */
    @Column(nullable = false)
    private UUID famille;

    @Column(nullable = false)
    private boolean revoque;

    @Column(length = 45)
    private String adresseIp;

    @Column(length = 255)
    private String userAgent;

    public JetonRafraichissement(Utilisateur utilisateur, String empreinte, Instant dateExpiration, UUID famille, String adresseIp, String userAgent) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
        this.dateExpiration = dateExpiration;
        this.famille = famille;
        this.adresseIp = adresseIp;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
    }

    public boolean estUtilisable(Instant maintenant) {
        return dateUtilisation == null && !revoque && dateExpiration.isAfter(maintenant);
    }

    public void marquerUtilise(Instant maintenant) {
        this.dateUtilisation = maintenant;
    }

    public void revoquer() {
        this.revoque = true;
    }
}
