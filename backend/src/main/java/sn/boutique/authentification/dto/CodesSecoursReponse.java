package sn.boutique.authentification.dto;

import java.util.List;

import sn.boutique.authentification.modele.EtapeConnexion.Etape;

/** Les dix codes de secours, en clair, affichés une seule fois ; la connexion se termine à la confirmation. */
public record CodesSecoursReponse(Etape etape, String jetonEtape, List<String> codesSecours) {
}
