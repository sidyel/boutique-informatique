package sn.boutique.apresvente.modele;

/**
 * Modération d'un avis : seuls les avis approuvés sont publiés et comptés dans la note.
 */
public enum StatutAvis {
    EN_ATTENTE,
    APPROUVE,
    REJETE
}
