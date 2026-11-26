package sn.boutique.referencement.web;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.noyau.config.ProprietesBoutique;
import sn.boutique.referencement.service.SitemapService;

/**
 * robots.txt et sitemap, à la racine du site (nginx les transmet au backend). ADR 0007 :
 * hors production ({@code INDEXATION_AUTORISEE=false}), robots.txt interdit tout le site.
 */
@RestController
@RequiredArgsConstructor
public class ReferencementControleur {

    private static final MediaType XML = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private static final MediaType TEXTE = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final CacheControl UNE_HEURE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    /**
     * Pages privées ou sans intérêt pour la recherche. Les URL filtrées ou triées des listes ne sont PAS
     * bloquées : Google doit pouvoir les visiter pour lire leur balise « noindex ».
     */
    static final String[] INTERDITS = {"/admin", "/compte", "/panier", "/commande", "/suivi", "/api/", "/recherche"};

    private final SitemapService sitemap;
    private final ProprietesBoutique proprietes;

    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        StringBuilder texte = new StringBuilder("User-agent: *\n");
        if (!proprietes.indexationAutorisee()) {
            // Préproduction, démonstration : rien n'est indexé (INDEXATION_AUTORISEE=false)
            texte.append("Disallow: /\n");
        } else {
            for (String chemin : INTERDITS) {
                texte.append("Disallow: ").append(chemin).append('\n');
            }
            texte.append('\n').append("Sitemap: ").append(proprietes.urlSiteSansBarre()).append("/sitemap.xml\n");
        }
        return ResponseEntity.ok().contentType(TEXTE).cacheControl(UNE_HEURE).body(texte.toString());
    }

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> index() {
        return ResponseEntity.ok().contentType(XML).cacheControl(UNE_HEURE).body(sitemap.index());
    }

    @GetMapping("/sitemap-{nom}.xml")
    public ResponseEntity<String> fichier(@PathVariable String nom) {
        String xml = sitemap.fichier(nom);
        return xml == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok().contentType(XML).cacheControl(UNE_HEURE).body(xml);
    }
}
