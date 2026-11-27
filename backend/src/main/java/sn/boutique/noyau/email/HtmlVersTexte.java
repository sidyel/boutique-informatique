package sn.boutique.noyau.email;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version texte brut d'un e-mail HTML, pour la partie {@code text/plain} d'un message multipart/alternative.
 * <p>
 * Dérivée du HTML plutôt que réécrite à la main : une seule source de vérité par e-mail, donc jamais de divergence
 * entre les deux versions. Les liens sont conservés (« libellé (https://…) »), ce qui compte pour les liens secrets
 * (suivi de commande, réinitialisation du mot de passe). Les clients de messagerie qui n'affichent pas le HTML
 * (certaines applications, lecteurs d'écran, filtres anti-spam) lisent cette version.
 */
public final class HtmlVersTexte {

    private static final Pattern IGNORES = Pattern.compile("(?is)<(head|style|script)\\b.*?</\\1>");
    private static final Pattern COMMENTAIRES = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern BLANCS_SOURCE = Pattern.compile("\\s+");
    private static final Pattern LIEN = Pattern.compile("(?is)<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>");
    private static final Pattern SAUT_LIGNE = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOC_FERMANT = Pattern.compile("(?i)</(p|div|h[1-6]|table|ul|ol)>");
    private static final Pattern LIGNE_FERMANTE = Pattern.compile("(?i)</(tr|li)>");
    private static final Pattern CELLULE_FERMANTE = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern BALISES = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern ENTITE_NUMERIQUE = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
    private static final Pattern ESPACES = Pattern.compile("[ \\t\\u00A0\\u202F]+");

    private HtmlVersTexte() {
    }

    public static String convertir(String html) {
        if (html == null) {
            return "";
        }
        String texte = IGNORES.matcher(html).replaceAll("");
        texte = COMMENTAIRES.matcher(texte).replaceAll("");
        // Les retours à la ligne du source HTML n'ont aucun sens : seules les balises font les sauts de ligne
        texte = BLANCS_SOURCE.matcher(texte).replaceAll(" ");
        texte = LIEN.matcher(texte).replaceAll(m -> Matcher.quoteReplacement(lien(m.group(1), m.group(2))));
        texte = SAUT_LIGNE.matcher(texte).replaceAll("\n");
        texte = BLOC_FERMANT.matcher(texte).replaceAll("\n\n");
        texte = LIGNE_FERMANTE.matcher(texte).replaceAll("\n");
        texte = CELLULE_FERMANTE.matcher(texte).replaceAll(" ");
        texte = BALISES.matcher(texte).replaceAll("");
        texte = decoder(texte);
        StringBuilder sortie = new StringBuilder();
        int vides = 0;
        for (String ligne : texte.split("\n", -1)) {
            String nette = ESPACES.matcher(ligne).replaceAll(" ").strip();
            if (nette.isEmpty()) {
                if (++vides == 1 && !sortie.isEmpty()) {
                    sortie.append('\n');
                }
            } else {
                vides = 0;
                sortie.append(nette).append('\n');
            }
        }
        return sortie.toString().strip() + "\n";
    }

    private static String lien(String url, String libelleHtml) {
        String libelle = decoder(BALISES.matcher(libelleHtml).replaceAll("")).strip();
        String adresse = decoder(url).strip();
        if (adresse.isEmpty() || adresse.startsWith("#")) {
            return libelle;
        }
        // Le libellé est déjà l'adresse (lien affiché en clair) : pas de doublon
        if (libelle.isEmpty() || libelle.equals(adresse) || libelle.contains(adresse)) {
            return adresse;
        }
        return libelle + " (" + adresse + ")";
    }

    private static String decoder(String texte) {
        String resultat = ENTITE_NUMERIQUE.matcher(texte).replaceAll(m -> {
            int point = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            return Matcher.quoteReplacement(new String(Character.toChars(point)));
        });
        return resultat.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }
}
