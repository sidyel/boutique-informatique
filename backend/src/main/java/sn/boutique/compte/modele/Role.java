package sn.boutique.compte.modele;

/**
 * Rôle d'un utilisateur. Seul CLIENT est public ; les autres sont des rôles internes
 * (session plus courte, accès au back-office selon le rôle).
 */
public enum Role {
    CLIENT,
    SERVICE_CLIENT,
    GESTIONNAIRE_STOCK,
    ADMIN,
    SUPER_ADMIN;

    public boolean estInterne() {
        return this != CLIENT;
    }
}
