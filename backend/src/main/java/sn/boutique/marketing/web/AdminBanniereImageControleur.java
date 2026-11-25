package sn.boutique.marketing.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.boutique.marketing.dto.ImageBanniereReponse;
import sn.boutique.marketing.modele.FormatBanniere;
import sn.boutique.marketing.service.ImagesBannieresService;

/** Images des bannières. Réservé à ADMIN et SUPER_ADMIN (règle dans SecuriteConfig). */
@RestController
@RequiredArgsConstructor
public class AdminBanniereImageControleur {

    private final ImagesBannieresService imagesBannieresService;

    /** Multipart : {@code fichier} et {@code format} ({@code ORDINATEUR} ou {@code MOBILE}). */
    @PostMapping(path = "/api/admin/bannieres/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageBanniereReponse envoyer(@RequestParam(name = "fichier", required = false) MultipartFile fichier,
                                        @RequestParam FormatBanniere format) {
        return imagesBannieresService.envoyer(fichier, format);
    }
}
