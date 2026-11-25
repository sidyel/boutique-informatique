package sn.boutique.catalogue.web;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.catalogue.dto.MediasProduitAdminReponse;
import sn.boutique.catalogue.dto.ModificationMediaRequete;
import sn.boutique.catalogue.dto.OrdreMediasRequete;
import sn.boutique.catalogue.service.MediaAdminService;

/**
 * Images d'un produit dans le back-office. Réservé à ADMIN, SUPER_ADMIN et GESTIONNAIRE_STOCK (règle dans
 * SecuriteConfig). Chaque route renvoie la liste à jour des médias du produit.
 */
@RestController
@RequestMapping("/api/admin/produits/{produitId}/medias")
@RequiredArgsConstructor
public class AdminMediaControleur {

    private final MediaAdminService mediaAdminService;

    /** Médias, limites et textes alternatifs suggérés (pour préremplir le formulaire). */
    @GetMapping
    public MediasProduitAdminReponse lister(@PathVariable Long produitId) {
        return mediaAdminService.lister(produitId);
    }

    /**
     * Envoi multipart : {@code fichiers} (un ou plusieurs) et autant de {@code textesAlternatifs}, dans le même
     * ordre ; {@code varianteId} facultatif. Les textes sont lus tels quels (pas de découpage sur la virgule,
     * que Spring appliquerait à une valeur unique liée à une liste).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediasProduitAdminReponse ajouter(@PathVariable Long produitId,
                                             @RequestParam(name = "fichiers", required = false) List<MultipartFile> fichiers,
                                             @RequestParam(required = false) Long varianteId,
                                             HttpServletRequest requete) {
        String[] textes = requete.getParameterValues("textesAlternatifs");
        return mediaAdminService.ajouterImages(produitId, fichiers, textes == null ? List.of() : Arrays.asList(textes),
                varianteId);
    }

    @PutMapping("/ordre")
    public MediasProduitAdminReponse ordonner(@PathVariable Long produitId,
                                              @Valid @RequestBody OrdreMediasRequete requete) {
        return mediaAdminService.ordonner(produitId, requete.ids());
    }

    @PutMapping("/{mediaId}/principal")
    public MediasProduitAdminReponse definirPrincipal(@PathVariable Long produitId, @PathVariable Long mediaId) {
        return mediaAdminService.definirPrincipal(produitId, mediaId);
    }

    /** Exemple : {@code {"texteAlternatif": "Vivobook 15 vu de face", "varianteId": null}}. */
    @PatchMapping("/{mediaId}")
    public MediasProduitAdminReponse modifier(@PathVariable Long produitId, @PathVariable Long mediaId,
                                              @Valid @RequestBody ModificationMediaRequete requete) {
        return mediaAdminService.modifier(produitId, mediaId, requete);
    }

    /** 409 pour la dernière image d'un produit publié. */
    @DeleteMapping("/{mediaId}")
    public MediasProduitAdminReponse supprimer(@PathVariable Long produitId, @PathVariable Long mediaId) {
        return mediaAdminService.supprimer(produitId, mediaId);
    }
}
