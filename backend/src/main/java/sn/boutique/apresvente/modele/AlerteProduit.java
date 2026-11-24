package sn.boutique.apresvente.modele;

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
import lombok.Setter;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.noyau.audit.EntiteAuditee;

/**
 * Alerte « retour en stock » ou « baisse de prix », ouverte aussi aux visiteurs sans compte
 * (simple e-mail). Une seule alerte en attente par e-mail, variante et type.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlerteProduit extends EntiteAuditee {

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 254)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private VarianteProduit variante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeAlerte type;

    /** Pour BAISSE_DE_PRIX : prévenir quand le prix passe sous ce montant (FCFA). */
    private Long prixCible;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean envoyee;

    @Setter(AccessLevel.NONE)
    private Instant dateEnvoi;

    public AlerteProduit(String email, VarianteProduit variante, TypeAlerte type) {
        this.email = Utilisateur.normaliserEmail(email);
        this.variante = variante;
        this.type = type;
    }

    public void marquerEnvoyee(Instant maintenant) {
        this.envoyee = true;
        this.dateEnvoi = maintenant;
    }
}
