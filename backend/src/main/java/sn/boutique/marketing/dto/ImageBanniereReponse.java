package sn.boutique.marketing.dto;

import java.util.List;

import sn.boutique.marketing.modele.FormatBanniere;

/**
 * Image de bannière envoyée, à rattacher à une bannière (formulaire du back-office, phase 6).
 *
 * @param avertissements proportions ou largeur éloignées du format conseillé (l'envoi n'est pas bloqué)
 */
public record ImageBanniereReponse(String idPublic, String url, int largeur, int hauteur, FormatBanniere format,
                                   String formatConseille, List<String> avertissements) {
}
