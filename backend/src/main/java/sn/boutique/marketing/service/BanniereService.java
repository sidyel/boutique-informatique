package sn.boutique.marketing.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import sn.boutique.marketing.dto.BanniereProgrammee;
import sn.boutique.marketing.dto.BanniereReponse;
import sn.boutique.marketing.modele.EmplacementBanniere;

/** Bannières visibles d'un emplacement : actives et dans leur période de diffusion. */
@Service
@RequiredArgsConstructor
public class BanniereService {

    private final BannieresActives bannieresActives;
    private final Clock horloge;

    public List<BanniereReponse> visibles(EmplacementBanniere emplacement, String slugCategorie) {
        String categorie = null;
        if (emplacement == EmplacementBanniere.ENTETE_CATEGORIE) {
            if (slugCategorie == null || slugCategorie.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le paramètre « categorie » est obligatoire pour l'emplacement ENTETE_CATEGORIE.");
            }
            categorie = slugCategorie;
        }
        // La période est vérifiée à chaque lecture, après le cache
        Instant maintenant = horloge.instant();
        return bannieresActives.lister(emplacement, categorie).stream()
                .filter(banniere -> banniere.estVisibleA(maintenant))
                .map(BanniereProgrammee::banniere)
                .toList();
    }
}
