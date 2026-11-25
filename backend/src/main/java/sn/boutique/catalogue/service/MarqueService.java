package sn.boutique.catalogue.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.dto.MarqueReponse;
import sn.boutique.catalogue.dto.MarqueResumeReponse;
import sn.boutique.noyau.cache.CachesPublics;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Lectures publiques des marques. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarqueService {

    private final JdbcClient jdbc;

    /** Marques ayant au moins un produit actif, par ordre alphabétique. */
    @Cacheable(cacheNames = CachesPublics.MARQUES, key = "'toutes'")
    public List<MarqueResumeReponse> lister() {
        return jdbc.sql("""
                        select m.nom, m.slug, m.logo_url, count(p.id) as nombre
                          from marque m
                          join produit p on p.marque_id = m.id and p.actif
                         group by m.id
                         order by lower(m.nom)
                        """)
                .query((ligne, n) -> new MarqueResumeReponse(ligne.getString("nom"), ligne.getString("slug"),
                        ligne.getString("logo_url"), ligne.getLong("nombre")))
                .list();
    }

    /** Une marque existante, même sans produit actif pour l'instant (la page affichera une liste vide). */
    @Cacheable(cacheNames = CachesPublics.MARQUE, key = "#slug")
    public MarqueReponse trouverParSlug(String slug) {
        return jdbc.sql("""
                        select m.nom, m.slug, m.logo_url, m.description, m.meta_titre, m.meta_description,
                               (select count(*) from produit p where p.marque_id = m.id and p.actif) as nombre
                          from marque m
                         where m.slug = :slug
                        """)
                .param("slug", slug)
                .query((ligne, n) -> new MarqueReponse(ligne.getString("nom"), ligne.getString("slug"),
                        ligne.getString("logo_url"), ligne.getString("description"), ligne.getString("meta_titre"),
                        ligne.getString("meta_description"), ligne.getLong("nombre")))
                .optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Marque", slug));
    }
}
