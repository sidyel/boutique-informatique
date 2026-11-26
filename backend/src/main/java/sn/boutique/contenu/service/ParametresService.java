package sn.boutique.contenu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.config.ProprietesBoutique;

/** Paramètres publics du site, lus à chaque page : en cache. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametresService {

    private final ParametresSiteDepot parametresDepot;
    private final ProprietesBoutique proprietes;

    @Cacheable(cacheNames = CachesPublics.PARAMETRES, key = "'publics'")
    public ParametresPublicsReponse publics() {
        return ParametresPublicsReponse.depuis(parametresDepot.lire(), site());
    }

    public ParametresPublicsReponse.Site site() {
        return new ParametresPublicsReponse.Site(proprietes.urlSiteSansBarre(), proprietes.indexationAutorisee());
    }
}
