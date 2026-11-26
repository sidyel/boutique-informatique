package sn.boutique.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import sn.boutique.noyau.notification.NotificationClient;

/** Enregistre les messages WhatsApp/SMS au lieu de les envoyer. */
public class FauxNotificationClient implements NotificationClient {

    public record Envoi(String telephone, String message) {
    }

    private final List<Envoi> envois = new CopyOnWriteArrayList<>();

    public void reinitialiser() {
        envois.clear();
    }

    public List<Envoi> envois() {
        return new ArrayList<>(envois);
    }

    @Override
    public void envoyer(String telephone, String message) {
        envois.add(new Envoi(telephone, message));
    }
}
