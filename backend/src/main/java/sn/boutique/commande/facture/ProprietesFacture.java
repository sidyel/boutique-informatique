package sn.boutique.commande.facture;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglage technique des factures (préfixe {@code boutique.facture}). Les mentions légales (raison sociale, NINEA,
 * RCCM, siège, régime de TVA) ne sont plus ici : elles sont en base ({@code parametres_site}), réglables par l'admin.
 *
 * @param mentionsObligatoires faux uniquement pour un environnement lancé avec le profil {@code prod} sans être une
 *                             vraie production (pile locale) : les mentions manquantes sont alors seulement signalées
 */
@ConfigurationProperties(prefix = "boutique.facture")
public record ProprietesFacture(boolean mentionsObligatoires) {
}
