package sn.boutique.contenu.web;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.dto.ParametresPublicsReponse;

/** Paramètres publics du site (en-tête, pied de page, balises de suivi et de vérification). */
@RestController
@RequiredArgsConstructor
public class ParametresControleur {

    private final ParametresSiteDepot parametresDepot;

    @GetMapping("/api/parametres")
    @Transactional(readOnly = true)
    public ParametresPublicsReponse parametres() {
        return ParametresPublicsReponse.depuis(parametresDepot.lire());
    }
}
