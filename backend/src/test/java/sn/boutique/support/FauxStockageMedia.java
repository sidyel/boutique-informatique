package sn.boutique.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import sn.boutique.media.FichierVerifie;
import sn.boutique.media.StockageIndisponibleException;
import sn.boutique.media.StockageMedia;

/**
 * Stockage en mémoire qui remplace Cloudinary dans tous les tests (bean {@code @Primary} de
 * {@code TestcontainersConfiguration}) : aucun test n'appelle le vrai service.
 */
public class FauxStockageMedia implements StockageMedia {

    private final Map<String, FichierVerifie> fichiers = new ConcurrentHashMap<>();
    private final Map<String, String> imagesDistantes = new ConcurrentHashMap<>();
    private final Map<String, MediaStocke> videos = new ConcurrentHashMap<>();
    private final Map<String, Instant> signatures = new ConcurrentHashMap<>();
    private final List<String> suppressions = new CopyOnWriteArrayList<>();
    private final AtomicLong version = new AtomicLong(1_700_000_000L);
    private volatile int echecsAProvoquer;
    private volatile int reussitesAvantEchecs;

    @Override
    public MediaStocke envoyerImage(FichierVerifie fichier, String idPublic) {
        echouerSiDemande();
        if (fichiers.putIfAbsent(idPublic, fichier) != null) {
            throw new IllegalStateException("idPublic déjà utilisé : " + idPublic);
        }
        return new MediaStocke(idPublic, version.incrementAndGet(), fichier.extension(), fichier.largeur(),
                fichier.hauteur(), fichier.contenu().length);
    }

    @Override
    public MediaStocke envoyerImageDistante(String url, String idPublic) {
        echouerSiDemande();
        imagesDistantes.put(idPublic, url);
        return new MediaStocke(idPublic, version.incrementAndGet(), "jpg", 480, 360, 20_000);
    }

    @Override
    public String urlImage(MediaStocke media) {
        String transformation = "svg".equals(media.format()) ? "fl_sanitize" : "f_auto,q_auto";
        return "https://res.cloudinary.test/faux/image/upload/" + transformation + "/v" + media.version() + "/"
                + media.idPublic() + "." + media.format();
    }

    @Override
    public String urlVideo(MediaStocke video) {
        return "https://res.cloudinary.test/faux/video/upload/f_auto,q_auto/v" + video.version() + "/"
                + video.idPublic() + "." + video.format();
    }

    @Override
    public String urlMiniatureVideo(MediaStocke video) {
        return "https://res.cloudinary.test/faux/video/upload/so_auto,f_auto,q_auto/v" + video.version() + "/"
                + video.idPublic() + ".jpg";
    }

    @Override
    public SignatureEnvoi signerEnvoiVideo(String idPublic, Instant horodatage) {
        signatures.put(idPublic, horodatage);
        Map<String, String> parametres = new LinkedHashMap<>();
        parametres.put("timestamp", String.valueOf(horodatage.getEpochSecond()));
        parametres.put("public_id", idPublic);
        parametres.put("api_key", "cle-publique-de-test");
        parametres.put("signature", "signature-de-test");
        return new SignatureEnvoi("https://api.cloudinary.test/v1_1/faux/video/upload", parametres);
    }

    @Override
    public Optional<MediaStocke> lireVideo(String idPublic) {
        echouerSiDemande();
        return Optional.ofNullable(videos.get(idPublic));
    }

    @Override
    public void supprimer(String idPublic, TypeRessource type) {
        echouerSiDemande();
        fichiers.remove(idPublic);
        imagesDistantes.remove(idPublic);
        videos.remove(idPublic);
        suppressions.add(idPublic);
    }

    /** Simule l'envoi direct d'une vidéo par le navigateur, avec la signature émise. */
    public void simulerEnvoiVideo(String idPublic, String format, long octets) {
        videos.put(idPublic, new MediaStocke(idPublic, version.incrementAndGet(), format, 1920, 1080, octets));
    }

    /** Horodatage signé pour cet idPublic (null si aucune signature émise). */
    public Instant horodatageSigne(String idPublic) {
        return signatures.get(idPublic);
    }

    /** URL copiée pour cet idPublic par {@link #envoyerImageDistante}. */
    public String urlDistante(String idPublic) {
        return imagesDistantes.get(idPublic);
    }

    /** Les {@code nombre} prochains appels échouent comme un Cloudinary injoignable. */
    public void provoquerEchecs(int nombre) {
        provoquerEchecsApres(0, nombre);
    }

    /** Les {@code reussites} prochains appels réussissent, puis les {@code echecs} suivants échouent. */
    public synchronized void provoquerEchecsApres(int reussites, int echecs) {
        reussitesAvantEchecs = reussites;
        echecsAProvoquer = echecs;
    }

    public boolean contient(String idPublic) {
        return fichiers.containsKey(idPublic) || imagesDistantes.containsKey(idPublic) || videos.containsKey(idPublic);
    }

    public FichierVerifie fichier(String idPublic) {
        return fichiers.get(idPublic);
    }

    public List<String> suppressions() {
        return List.copyOf(suppressions);
    }

    public void reinitialiser() {
        fichiers.clear();
        imagesDistantes.clear();
        videos.clear();
        signatures.clear();
        suppressions.clear();
        echecsAProvoquer = 0;
        reussitesAvantEchecs = 0;
    }

    private synchronized void echouerSiDemande() {
        if (reussitesAvantEchecs > 0) {
            reussitesAvantEchecs--;
            return;
        }
        if (echecsAProvoquer > 0) {
            echecsAProvoquer--;
            throw new StockageIndisponibleException("Échec simulé du stockage", null);
        }
    }
}
