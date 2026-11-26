package sn.boutique.noyau.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sn.boutique.catalogue.evenement.CatalogueModifie;

/**
 * Vide les caches publics au démarrage et <strong>après la validation</strong> de la transaction qui a
 * modifié le catalogue : vider avant laisserait une requête concurrente remettre en cache l'ancienne version. Une transaction
 * annulée ne vide rien. Hors transaction (import de démonstration), le vidage est immédiat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvalidationCaches {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void vider(CatalogueModifie evenement) {
        viderTout(evenement.origine());
    }

    /**
     * Au démarrage d'une nouvelle version : les entrées écrites par la version précédente peuvent avoir une
     * autre forme (champ ajouté à un DTO) et seraient relues avec des valeurs manquantes jusqu'à expiration.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void viderAuDemarrage() {
        viderTout("démarrage de l'application");
    }

    private void viderTout(String origine) {
        for (String nom : CachesPublics.TOUS) {
            Cache cache = cacheManager.getCache(nom);
            try {
                if (cache != null) {
                    // invalidate() et non clear() : clear() peut vider plus tard (asynchrone), une lecture
                    // juste après la modification remettrait alors l'ancienne version en circulation
                    cache.invalidate();
                }
            } catch (RuntimeException e) {
                log.error("Cache « {} » : vidage impossible après « {} », entrées périmées jusqu'à expiration. {}",
                        nom, origine, e.getMessage());
            }
        }
        log.info("Caches publics vidés : {}", origine);
    }
}
