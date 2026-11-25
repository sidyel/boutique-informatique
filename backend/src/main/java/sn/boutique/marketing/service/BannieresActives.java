package sn.boutique.marketing.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.marketing.convertisseur.BanniereConvertisseur;
import sn.boutique.marketing.depot.BanniereDepot;
import sn.boutique.marketing.dto.BanniereProgrammee;
import sn.boutique.marketing.modele.Banniere;
import sn.boutique.marketing.modele.EmplacementBanniere;
import sn.boutique.noyau.cache.CachesPublics;

/** Bannières actives d'un emplacement, avec leurs dates, en cache (sans le filtre des dates). */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BannieresActives {

    private final BanniereDepot banniereDepot;
    private final BanniereConvertisseur convertisseur;

    /** {@code slugCategorie} : uniquement pour {@link EmplacementBanniere#ENTETE_CATEGORIE}, sinon {@code null}. */
    @Cacheable(cacheNames = CachesPublics.BANNIERES, key = "#emplacement.name() + ':' + #slugCategorie")
    public List<BanniereProgrammee> lister(EmplacementBanniere emplacement, String slugCategorie) {
        List<Banniere> bannieres = slugCategorie == null
                ? banniereDepot.findByEmplacementAndActifTrueOrderByPositionAsc(emplacement)
                : banniereDepot.findByEmplacementAndCategorieSlugAndActifTrueOrderByPositionAsc(emplacement, slugCategorie);
        return bannieres.stream()
                .map(banniere -> new BanniereProgrammee(convertisseur.versReponse(banniere), banniere.getDateDebut(),
                        banniere.getDateFin()))
                .toList();
    }
}
