package sn.boutique.noyau.limitation;

/**
 * Résultat d'un comptage : rang de la requête dans la fenêtre en cours et temps restant avant sa fin.
 *
 * @param nombre          nombre de requêtes comptées dans la fenêtre, celle-ci comprise
 * @param millisRestantes durée restante de la fenêtre
 */
public record Comptage(long nombre, long millisRestantes) {

    public boolean depasse(ProprietesLimitation.Regle regle) {
        return nombre > regle.limite();
    }

    /** Vrai pour la seule requête qui franchit la limite (alerte émise une fois par fenêtre). */
    public boolean franchit(ProprietesLimitation.Regle regle) {
        return nombre == regle.limite() + 1L;
    }

    /** Secondes à attendre, arrondies au-dessus, au moins 1. */
    public long reessayerDans() {
        return Math.max(1, (millisRestantes + 999) / 1000);
    }
}
