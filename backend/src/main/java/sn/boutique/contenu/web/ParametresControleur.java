package sn.boutique.contenu.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.contenu.dto.ParametresPublicsReponse;
import sn.boutique.contenu.service.ParametresService;

/** Paramètres publics du site (en-tête, pied de page, balises de suivi et de vérification). */
@RestController
@RequiredArgsConstructor
public class ParametresControleur {

    private final ParametresService parametresService;

    @GetMapping("/api/parametres")
    public ParametresPublicsReponse parametres() {
        return parametresService.publics();
    }
}
