package sn.boutique.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Suppression des fichiers sur le stockage, avec plusieurs essais. Si tous échouent, l'idPublic est
 * enregistré dans {@code media_orphelin} (et pas seulement dans les journaux) pour un nettoyage ultérieur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettoyageStockage {

    private final StockageMedia stockage;
    private final ProprietesMedias proprietes;
    private final RegistreOrphelins orphelins;

    /**
     * Après la validation, hors du fil de la requête : une panne de Cloudinary (jusqu'à 3 délais d'appel)
     * ne fait pas attendre l'administrateur, la suppression en base est déjà acquise.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void surMediaRetire(MediaRetire evenement) {
        supprimer(evenement.idPublic(), evenement.type(), evenement.origine());
    }

    /** @return vrai si le fichier est supprimé (ou n'existait plus) */
    public boolean supprimer(String idPublic, StockageMedia.TypeRessource type, String origine) {
        int essais = proprietes.suppression().essais();
        String derniereErreur = null;
        for (int essai = 1; essai <= essais; essai++) {
            try {
                stockage.supprimer(idPublic, type);
                return true;
            } catch (RuntimeException e) {
                derniereErreur = e.getMessage();
                log.warn("Suppression de {} : essai {}/{} en échec ({})", idPublic, essai, essais, derniereErreur);
                if (essai < essais && !pause()) {
                    break;
                }
            }
        }
        try {
            orphelins.enregistrer(idPublic, type, origine, essais, derniereErreur);
            log.error("Fichier orphelin enregistré dans media_orphelin : {} ({})", idPublic, origine);
        } catch (RuntimeException e) {
            // Dernier recours : le journal est alors la seule trace
            log.error("Fichier orphelin NON enregistré : {} ({})", idPublic, origine, e);
        }
        return false;
    }

    private boolean pause() {
        try {
            Thread.sleep(proprietes.suppression().pause());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
