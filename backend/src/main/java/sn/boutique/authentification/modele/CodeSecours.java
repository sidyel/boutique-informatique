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

/** Code de secours à usage unique (ADR 0009 §5) : seule l'empreinte SHA-256 est stockée, le code n'est affiché qu'une fois. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeSecours extends EntiteAuditee {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    private Instant dateUtilisation;

    public CodeSecours(Utilisateur utilisateur, String empreinte) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
    }
}
