package sn.boutique.authentification.modele;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Connexion d'un compte interne entre le mot de passe et le second facteur (ADR 0009 §1).
 * <p>
 * Le jeton d'étape est opaque et n'a aucun droit : seule son empreinte SHA-256 est stockée. Il expire 5 minutes après la
 * dernière étape réussie, 30 minutes au plus après le mot de passe, et après un nombre limité de codes faux.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EtapeConnexion extends EntiteAuditee {

    public enum Etape {
        /** Pas de second facteur : montrer le QR code et exiger un premier code. */
        ENROLEMENT_REQUIS,
        /** Second facteur actif : exiger un code (TOTP ou de secours). */
        CODE_REQUIS,
        /** Code valide, mais les codes de secours n'ont pas été confirmés : en présenter de nouveaux. */
        CODES_SECOURS_A_CONFIRMER
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** SHA-256 du jeton, en hexadécimal. */
    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Etape etape;

    /** Secret TOTP généré pendant l'enrôlement, chiffré, tant que le premier code n'a pas été vérifié. */
    @Column(length = 400)
    private String secretEnAttenteChiffre;

    @Column(nullable = false)
    private int essais;

    @Column(length = 45)
    private String adresseIp;

    @Column(length = 300)
    private String userAgent;

    @Column(nullable = false)
    private Instant dateExpiration;

    /** Fin absolue : le renouvellement à chaque étape ne la dépasse jamais. */
    @Column(nullable = false)
    private Instant dateLimite;

    private Instant dateUtilisation;

    public EtapeConnexion(Utilisateur utilisateur, String empreinte, Etape etape, String adresseIp, String userAgent,
                          Instant dateExpiration, Instant dateLimite) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
        this.etape = etape;
        this.adresseIp = adresseIp;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
        this.dateExpiration = dateExpiration;
        this.dateLimite = dateLimite;
    }

    public boolean estUtilisable(Instant maintenant, int essaisMaximum) {
        return dateUtilisation == null && essais < essaisMaximum
                && dateExpiration.isAfter(maintenant) && dateLimite.isAfter(maintenant);
    }

    public void compterEchec() {
        this.essais++;
    }

    /** Passe à l'étape suivante : le délai repart, sans dépasser la limite absolue. */
    public void passerA(Etape suivante, Instant maintenant, java.time.Duration duree) {
        this.etape = suivante;
        Instant renouvele = maintenant.plus(duree);
        this.dateExpiration = renouvele.isAfter(dateLimite) ? dateLimite : renouvele;
    }

    public void memoriserSecretEnAttente(String secretChiffre) {
        this.secretEnAttenteChiffre = secretChiffre;
    }

    public void marquerUtilisee(Instant maintenant) {
        this.dateUtilisation = maintenant;
        this.secretEnAttenteChiffre = null;
    }
}
