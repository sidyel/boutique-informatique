package sn.boutique.commande.facture;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mentions légales imprimées sur les factures (préfixe {@code boutique.facture}), toutes lues de l'environnement.
 * Elles peuvent rester vides en développement ; {@link VerificationMentionsLegales} empêche alors le passage en
 * production.
 *
 * @param raisonSociale          dénomination sociale du vendeur
 * @param ninea                  numéro d'identification nationale des entreprises et associations
 * @param rccm                   registre du commerce et du crédit mobilier
 * @param tva                    taux ou régime de TVA applicable, tel qu'il doit figurer sur la facture (ex. « 18 % »,
 *                               « non assujetti »)
 * @param mentionsObligatoires   faux uniquement pour un environnement de démonstration lancé avec le profil
 *                               {@code prod} : les mentions manquantes sont alors seulement signalées
 */
@ConfigurationProperties(prefix = "boutique.facture")
public record ProprietesFacture(
        String raisonSociale,
        String ninea,
        String rccm,
        String tva,
        boolean mentionsObligatoires) {

    /** Noms des variables d'environnement des mentions encore vides. */
    public java.util.List<String> manquantes() {
        var manquantes = new java.util.ArrayList<String>();
        if (vide(raisonSociale)) {
            manquantes.add("FACTURE_RAISON_SOCIALE");
        }
        if (vide(ninea)) {
            manquantes.add("FACTURE_NINEA");
        }
        if (vide(rccm)) {
            manquantes.add("FACTURE_RCCM");
        }
        if (vide(tva)) {
            manquantes.add("FACTURE_TVA");
        }
        return manquantes;
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
