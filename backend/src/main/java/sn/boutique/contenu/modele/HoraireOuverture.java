package sn.boutique.contenu.modele;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Plage d'ouverture de la boutique physique (JSON-LD {@code openingHoursSpecification}) :
 * {@code {"jours": ["MONDAY", "TUESDAY"], "ouverture": "09:00", "fermeture": "19:00"}}.
 * Heures en texte « HH:mm » (stockage JSON lisible, sans dépendre du format des dates de Jackson).
 */
public record HoraireOuverture(List<DayOfWeek> jours, String ouverture, String fermeture) {

    /** Valide : au moins un jour, deux heures « HH:mm » et l'ouverture avant la fermeture. */
    public boolean estValide() {
        if (jours == null || jours.isEmpty() || jours.contains(null)) {
            return false;
        }
        try {
            return LocalTime.parse(ouverture).isBefore(LocalTime.parse(fermeture));
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }
}
