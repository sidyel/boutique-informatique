package sn.boutique.media;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extraction de l'identifiant d'une vidéo YouTube à partir d'un lien collé par l'administrateur.
 * <p>
 * Formes acceptées : {@code youtube.com/watch?v=ID}, {@code youtu.be/ID}, {@code youtube.com/shorts/ID},
 * {@code youtube.com/embed/ID} et {@code youtube-nocookie.com/embed/ID}, en http ou https. Tout autre hôte
 * est refusé (pas de redirection vers un site tiers déguisé en lien YouTube).
 */
public final class LienYoutube {

    private static final Pattern IDENTIFIANT = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> HOTES_YOUTUBE = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com");

    private LienYoutube() {
    }

    public static Optional<String> extraireIdentifiant(String lien) {
        if (lien == null || lien.isBlank() || lien.length() > 500) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(lien.strip());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String schema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String hote = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String chemin = uri.getPath() == null ? "" : uri.getPath();
        if (!schema.equals("https") && !schema.equals("http") || uri.getUserInfo() != null) {
            return Optional.empty();
        }
        String candidat = null;
        if (hote.equals("youtu.be")) {
            candidat = premierSegment(chemin, "/");
        } else if (HOTES_YOUTUBE.contains(hote)) {
            if (chemin.equals("/watch")) {
                candidat = parametre(uri.getRawQuery(), "v");
            } else {
                for (String prefixe : new String[] {"/shorts/", "/embed/", "/live/"}) {
                    if (chemin.startsWith(prefixe)) {
                        candidat = premierSegment(chemin, prefixe);
                    }
                }
            }
        }
        return Optional.ofNullable(candidat).filter(id -> IDENTIFIANT.matcher(id).matches());
    }

    /** URL canonique enregistrée en base, quelle que soit la forme du lien collé. */
    public static String urlCanonique(String identifiant) {
        return "https://www.youtube.com/watch?v=" + identifiant;
    }

    private static String premierSegment(String chemin, String prefixe) {
        String reste = chemin.substring(prefixe.length());
        int fin = reste.indexOf('/');
        return fin < 0 ? reste : reste.substring(0, fin);
    }

    private static String parametre(String requete, String nom) {
        if (requete == null) {
            return null;
        }
        for (String paire : requete.split("&")) {
            int egal = paire.indexOf('=');
            if (egal > 0 && paire.substring(0, egal).equals(nom)) {
                return paire.substring(egal + 1);
            }
        }
        return null;
    }
}
