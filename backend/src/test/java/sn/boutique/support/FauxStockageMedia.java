package sn.boutique.support;

import java.util.List;
import java.util.Map;
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
    private final List<String> suppressions = new CopyOnWriteArrayList<>();
    private final AtomicLong version = new AtomicLong(1_700_000_000L);
    private volatile int echecsAProvoquer;

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
    public void supprimer(String idPublic, TypeRessource type) {
        echouerSiDemande();
        fichiers.remove(idPublic);
        suppressions.add(idPublic);
    }

    /** Les {@code nombre} prochains appels échouent comme un Cloudinary injoignable. */
    public void provoquerEchecs(int nombre) {
        echecsAProvoquer = nombre;
    }

    public boolean contient(String idPublic) {
        return fichiers.containsKey(idPublic);
    }

    public FichierVerifie fichier(String idPublic) {
        return fichiers.get(idPublic);
    }

    public List<String> suppressions() {
        return List.copyOf(suppressions);
    }

    public void reinitialiser() {
        fichiers.clear();
        suppressions.clear();
        echecsAProvoquer = 0;
    }

    private void echouerSiDemande() {
        if (echecsAProvoquer > 0) {
            echecsAProvoquer--;
            throw new StockageIndisponibleException("Échec simulé du stockage", null);
        }
    }
}
