package sn.boutique.compte.modele;

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
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Demande de changement d'adresse e-mail d'un compte.
 * <ul>
 *   <li>{@code EN_ATTENTE} : lien de confirmation envoyé à la <strong>nouvelle</strong> adresse (valable 24 h) ; l'ancienne
 *       adresse a reçu une alerte avec un lien « ce n'est pas moi » (empreinte de signalement) ;</li>
 *   <li>{@code CONFIRME} : l'adresse du compte a changé ; le lien « ce n'est pas moi » reste valable 7 jours et rétablit
 *       l'ancienne adresse ;</li>
 *   <li>{@code ANNULE} : demande remplacée par une autre, ou signalée comme non sollicitée avant confirmation ;</li>
 *   <li>{@code REVERTI} : changement signalé comme non sollicité après confirmation, ancienne adresse rétablie.</li>
 * </ul>
 * Seules les empreintes SHA-256 des jetons sont stockées.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangementEmail extends EntiteAuditee {

    public enum Statut { EN_ATTENTE, CONFIRME, ANNULE, REVERTI }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 254)
    private String ancienEmail;

    @Column(nullable = false, length = 254)
    private String nouvelEmail;

    @Column(nullable = false, length = 64)
    private String empreinteConfirmation;

    @Column(nullable = false, length = 64)
    private String empreinteSignalement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_ATTENTE;

    @Column(nullable = false)
    private Instant dateExpiration;

    private Instant dateConfirmation;

    private Instant dateSignalement;

    public ChangementEmail(Utilisateur utilisateur, String nouvelEmail, String empreinteConfirmation,
                           String empreinteSignalement, Instant dateExpiration) {
        this.utilisateur = utilisateur;
        this.ancienEmail = utilisateur.getEmail();
        this.nouvelEmail = Utilisateur.normaliserEmail(nouvelEmail);
        this.empreinteConfirmation = empreinteConfirmation;
        this.empreinteSignalement = empreinteSignalement;
        this.dateExpiration = dateExpiration;
    }

    public boolean confirmable(Instant maintenant) {
        return statut == Statut.EN_ATTENTE && maintenant.isBefore(dateExpiration);
    }

    /** Le lien « ce n'est pas moi » est valable tant que la demande attend, puis pendant le délai suivant la confirmation. */
    public boolean signalable(Instant maintenant) {
        return (statut == Statut.EN_ATTENTE || statut == Statut.CONFIRME) && maintenant.isBefore(dateExpiration);
    }

    public void confirmer(Instant maintenant, Instant finDelaiSignalement) {
        this.statut = Statut.CONFIRME;
        this.dateConfirmation = maintenant;
        this.dateExpiration = finDelaiSignalement;
    }

    public void annuler(Instant maintenant) {
        this.statut = Statut.ANNULE;
        this.dateSignalement = maintenant;
    }

    public void remplacer() {
        this.statut = Statut.ANNULE;
    }

    public void reverter(Instant maintenant) {
        this.statut = Statut.REVERTI;
        this.dateSignalement = maintenant;
    }
}
