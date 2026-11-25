package sn.boutique.media;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Enregistre un fichier orphelin dans sa propre transaction (appelé après la validation, ou en compensation). */
@Component
@RequiredArgsConstructor
public class RegistreOrphelins {

    private final MediaOrphelinDepot depot;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrer(String idPublic, StockageMedia.TypeRessource type, String origine, int essais,
                            String erreur) {
        MediaOrphelin orphelin = depot.findByIdPublic(idPublic)
                .orElseGet(() -> new MediaOrphelin(idPublic, type, origine));
        orphelin.echec(essais, erreur);
        depot.save(orphelin);
    }
}
