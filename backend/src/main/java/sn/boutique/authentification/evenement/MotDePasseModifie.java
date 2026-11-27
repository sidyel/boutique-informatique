package sn.boutique.authentification.evenement;

/**
 * Mot de passe changé : le titulaire est prévenu par e-mail (alerte de sécurité).
 *
 * @param sessionCouranteConservee vrai quand le titulaire a changé son mot de passe depuis son espace client : seules ses
 *                                 <em>autres</em> sessions sont fermées ; faux après une réinitialisation (toutes fermées)
 */
public record MotDePasseModifie(Long utilisateurId, boolean sessionCouranteConservee) {

    public MotDePasseModifie(Long utilisateurId) {
        this(utilisateurId, false);
    }
}
