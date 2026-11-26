package sn.boutique.referencement.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.config.ProprietesBoutique;

/**
 * Sitemap XML (ADR 0007) : un index et un fichier par type de page, uniquement des URL canoniques publiées.
 * <ul>
 *   <li>Produits publiés ({@code actif}) : les produits masqués ou retirés en sont exclus ; {@code lastmod} tient
 *   compte des variantes (un changement de prix modifie la page) ; photo principale en {@code image:image}.</li>
 *   <li>Catégories actives, marques ayant au moins un produit publié, accueil.</li>
 * </ul>
 * Les fichiers de produits sont découpés par {@value #URL_PAR_FICHIER} URL (limite du protocole : 50 000).
 * Mis en cache, vidé avec le catalogue.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SitemapService {

    /** Limite du protocole sitemap : 50 000 URL et 50 Mo par fichier. */
    static final int URL_PAR_FICHIER = 50_000;
    private static final Pattern FICHIER_PRODUITS = Pattern.compile("produits-([1-9][0-9]{0,3})");
    private static final DateTimeFormatter W3C = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
            .withZone(ZoneOffset.UTC);

    private final JdbcClient jdbc;
    private final ProprietesBoutique proprietes;

    private record Url(String chemin, Instant modification, String image) {
    }

    /** Index : la liste des fichiers, avec la date de leur dernière modification. */
    @Cacheable(cacheNames = CachesPublics.SITEMAP, key = "'index'")
    public String index() {
        List<String[]> fichiers = new ArrayList<>();
        fichiers.add(new String[] {"pages", null});
        fichiers.add(new String[] {"categories", derniere(categories())});
        fichiers.add(new String[] {"marques", derniere(marques())});
        long nombre = jdbc.sql("select count(*) from produit where actif").query(Long.class).single();
        OffsetDateTime dernierProduit = jdbc.sql("""
                        select max(greatest(p.date_modification, v.derniere))
                        from produit p
                        left join (select produit_id, max(date_modification) as derniere
                                   from variante_produit group by produit_id) v on v.produit_id = p.id
                        where p.actif
                        """).query((rs, i) -> rs.getObject(1, OffsetDateTime.class)).list().stream()
                .filter(Objects::nonNull).findFirst().orElse(null);
        long pages = Math.max(1, (nombre + URL_PAR_FICHIER - 1) / URL_PAR_FICHIER);
        for (int i = 1; i <= pages; i++) {
            fichiers.add(new String[] {"produits-" + i, dernierProduit == null ? null : W3C.format(dernierProduit)});
        }

        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        for (String[] fichier : fichiers) {
            xml.append("  <sitemap><loc>").append(echapper(absolue("/sitemap-" + fichier[0] + ".xml"))).append("</loc>");
            if (fichier[1] != null) {
                xml.append("<lastmod>").append(fichier[1]).append("</lastmod>");
            }
            xml.append("</sitemap>\n");
        }
        return xml.append("</sitemapindex>\n").toString();
    }

    /**
     * Un fichier du sitemap : « pages », « categories », « marques » ou « produits-N ».
     *
     * @return vide si le nom est inconnu ou la page de produits hors limites (404)
     */
    @Cacheable(cacheNames = CachesPublics.SITEMAP, key = "#nom", unless = "#result == null")
    public String fichier(String nom) {
        List<Url> urls = switch (nom) {
            case "pages" -> List.of(new Url("/", null, null));
            case "categories" -> categories();
            case "marques" -> marques();
            default -> produits(nom).orElse(null);
        };
        return urls == null ? null : urlset(urls);
    }

    private Optional<List<Url>> produits(String nom) {
        Matcher correspondance = FICHIER_PRODUITS.matcher(nom);
        if (!correspondance.matches()) {
            return Optional.empty();
        }
        int page = Integer.parseInt(correspondance.group(1));
        List<Url> urls = jdbc.sql("""
                        select p.slug,
                               greatest(p.date_modification, v.derniere) as modification,
                               m.url as image
                        from produit p
                        left join (select produit_id, max(date_modification) as derniere
                                   from variante_produit group by produit_id) v on v.produit_id = p.id
                        left join media_produit m on m.produit_id = p.id and m.principal
                        where p.actif
                        order by p.id
                        limit :limite offset :decalage
                        """)
                .param("limite", URL_PAR_FICHIER)
                .param("decalage", (long) (page - 1) * URL_PAR_FICHIER)
                .query((rs, i) -> new Url("/produit/" + rs.getString("slug"),
                        instant(rs.getObject("modification", OffsetDateTime.class)),
                        rs.getString("image")))
                .list();
        // La première page existe toujours (sitemap vide plutôt que 404 tant que le catalogue est vide)
        return urls.isEmpty() && page > 1 ? Optional.empty() : Optional.of(urls);
    }

    private List<Url> categories() {
        return jdbc.sql("select slug, date_modification from categorie where actif order by position, id")
                .query((rs, i) -> new Url("/categorie/" + rs.getString("slug"),
                        instant(rs.getObject("date_modification", OffsetDateTime.class)), null))
                .list();
    }

    private List<Url> marques() {
        return jdbc.sql("""
                        select m.slug, greatest(m.date_modification, max(p.date_modification)) as modification
                        from marque m
                        join produit p on p.marque_id = m.id and p.actif
                        group by m.id, m.slug, m.date_modification
                        order by m.slug
                        """)
                .query((rs, i) -> new Url("/marque/" + rs.getString("slug"),
                        instant(rs.getObject("modification", OffsetDateTime.class)), null))
                .list();
    }

    private String urlset(List<Url> urls) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" \
                xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
                """);
        for (Url url : urls) {
            xml.append("  <url><loc>").append(echapper(absolue(url.chemin()))).append("</loc>");
            if (url.modification() != null) {
                xml.append("<lastmod>").append(W3C.format(url.modification())).append("</lastmod>");
            }
            if (url.image() != null) {
                xml.append("<image:image><image:loc>").append(echapper(absolue(url.image())))
                        .append("</image:loc></image:image>");
            }
            xml.append("</url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    private static Instant instant(OffsetDateTime date) {
        return date == null ? null : date.toInstant();
    }

    private static String derniere(List<Url> urls) {
        return urls.stream().map(Url::modification).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).map(W3C::format).orElse(null);
    }

    /** Chemin relatif → URL absolue du site ; une URL déjà absolue (Cloudinary) est gardée. */
    private String absolue(String chemin) {
        return chemin.startsWith("http://") || chemin.startsWith("https://")
                ? chemin
                : proprietes.urlSiteSansBarre() + chemin;
    }

    static String echapper(String texte) {
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
