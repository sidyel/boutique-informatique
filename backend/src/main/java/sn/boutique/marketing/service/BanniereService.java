package sn.boutique.marketing.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sn.boutique.marketing.convertisseur.BanniereConvertisseur;
import sn.boutique.marketing.depot.BanniereDepot;
import sn.boutique.marketing.dto.BanniereReponse;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.EmplacementBanniere;

/** Bannières visibles d'un emplacement : actives et dans leur période de diffusion. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BanniereService {

    private final BanniereDepot banniereDepot;
    private final BanniereConvertisseur convertisseur;
    private final Clock horloge;

    public List<BanniereReponse> visibles(EmplacementBanniere emplacement, String slugCategorie) {
        List<Banniere> candidates;
        if (emplacement == EmplacementBanniere.ENTETE_CATEGORIE) {
            if (slugCategorie == null || slugCategorie.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le paramètre « categorie » est obligatoire pour l'emplacement ENTETE_CATEGORIE.");
            }
            candidates = banniereDepot.findByEmplacementAndCategorieSlugAndActifTrueOrderByPositionAsc(
                    emplacement, slugCategorie);
        } else {
            candidates = banniereDepot.findByEmplacementAndActifTrueOrderByPositionAsc(emplacement);
        }
        // La période est vérifiée à chaque lecture, après le cache (commit du cache) : une bannière
        // apparaît et disparaît à l'heure prévue.
        Instant maintenant = horloge.instant();
        return candidates.stream()
                .filter(banniere -> banniere.estVisibleA(maintenant))
                .map(convertisseur::versReponse)
                .toList();
    }
}
