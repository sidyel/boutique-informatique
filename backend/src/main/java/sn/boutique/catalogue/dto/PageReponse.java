package sn.boutique.catalogue.dto;

import java.util.List;

/**
 * Page de résultats.
 *
 * @param page numéro de la page, à partir de 1
 */
public record PageReponse<T>(List<T> elements, int page, int taille, long totalElements, int totalPages) {

    public static <T> PageReponse<T> de(List<T> elements, int page, int taille, long total) {
        return new PageReponse<>(elements, page, taille, total, (int) ((total + taille - 1) / taille));
    }
}
