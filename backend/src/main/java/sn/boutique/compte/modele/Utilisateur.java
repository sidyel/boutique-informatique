package sn.boutique.compte.modele;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.boutique.noyau.audit.EntiteAuditee;
import sn.boutique.noyau.telephone.TelephoneSenegal;

/**
 * Compte client ou interne. L'e-mail est stocké en minuscules (unicité insensible à la casse)
 * et le téléphone au format E.164.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utilisateur extends EntiteAuditee {

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String motDePasseHash;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(nullable = false, length = 80)
    private String nom;

    /** Format E.164 : +221XXXXXXXXX (facultatif). */
    @Setter(AccessLevel.NONE)
    @Column(length = 13)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.CLIENT;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false)
    private boolean emailVerifie;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean consentementMarketing;

    @Setter(AccessLevel.NONE)
    private Instant dateConsentement;

    public Utilisateur(String email, String motDePasseHash, String prenom, String nom) {
        setEmail(email);
        this.motDePasseHash = motDePasseHash;
        this.prenom = prenom;
        this.nom = nom;
    }

    public static String normaliserEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public void setEmail(String email) {
        this.email = normaliserEmail(email);
    }

    /** Accepte toute saisie valide (« 77 123 45 67 »…) et la stocke en E.164. */
    public void setTelephone(String telephone) {
        this.telephone = TelephoneSenegal.normaliserFacultatif(telephone);
    }

    /** D'où vient la dernière décision de consentement (inscription, espace client, administrateur). */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SourceConsentement sourceConsentement;

    /** Enregistre le choix marketing, sa date et sa source (preuve du consentement). */
    public void definirConsentementMarketing(boolean consentement, Instant date, SourceConsentement source) {
        this.consentementMarketing = consentement;
        this.dateConsentement = date;
        this.sourceConsentement = source;
    }

    /** Choix fait à l'inscription. */
    public void definirConsentementMarketing(boolean consentement, Instant date) {
        definirConsentementMarketing(consentement, date, SourceConsentement.INSCRIPTION);
    }
}
