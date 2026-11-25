package sn.boutique.catalogue.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Envoi direct d'une vidéo : le navigateur poste le fichier (champ {@code file}) et {@code parametres} à
 * {@code urlEnvoi} avant {@code expiration}, puis déclare {@code idPublic} au backend.
 *
 * @param tailleMax taille maximale acceptée à la déclaration, en octets
 */
public record SignatureEnvoiVideoReponse(String urlEnvoi, Map<String, String> parametres, String idPublic,
                                         Instant expiration, long tailleMax, List<String> formats) {
}
