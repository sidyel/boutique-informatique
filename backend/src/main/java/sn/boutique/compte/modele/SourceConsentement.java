package sn.boutique.compte.modele;

/** D'où vient la décision de consentement marketing (preuve du consentement). */
public enum SourceConsentement {
    /** Case cochée (ou non) au formulaire d'inscription. */
    INSCRIPTION,
    /** Réglage dans l'espace client. */
    COMPTE,
    /** Modification par un administrateur (demande du client par un autre canal). */
    ADMIN
}
