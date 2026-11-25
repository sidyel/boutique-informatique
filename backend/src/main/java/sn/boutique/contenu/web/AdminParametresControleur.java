package sn.boutique.contenu.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.contenu.modele.ImageParametre;
import sn.boutique.contenu.service.ParametresAdminService;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/** Images des paramètres du site. Réservé à ADMIN et SUPER_ADMIN (règle dans SecuriteConfig). */
@RestController
@RequestMapping("/api/admin/parametres")
@RequiredArgsConstructor
public class AdminParametresControleur {

    private final ParametresAdminService parametresAdminService;

    /**
     * {@code image} : {@code logo}, {@code logo-sombre} (JPEG, PNG, WebP ou SVG nettoyé), {@code favicon}
     * (carré, 512 px au moins), {@code image-partage} (600 × 315 au moins, 1200 × 630 conseillé). Champ
     * multipart {@code fichier}. Renvoie les paramètres publics à jour.
     */
    @PostMapping(path = "/{image}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParametresPublicsReponse remplacerImage(@PathVariable String image,
                                                   @RequestParam(required = false) MultipartFile fichier) {
        ImageParametre cible = ImageParametre.depuisChemin(image)
                .orElseThrow(() -> new RessourceIntrouvableException("Image des paramètres", image));
        return parametresAdminService.remplacerImage(cible, fichier);
    }
}
