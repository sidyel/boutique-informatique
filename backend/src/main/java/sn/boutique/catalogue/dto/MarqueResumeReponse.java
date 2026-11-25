package sn.boutique.catalogue.dto;

/** Marque dans une liste (page des marques, méga-menu). */
public record MarqueResumeReponse(String nom, String slug, String logoUrl, long nombreProduits) {
}
