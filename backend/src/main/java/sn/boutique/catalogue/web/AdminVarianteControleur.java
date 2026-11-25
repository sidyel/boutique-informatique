package sn.boutique.catalogue.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.catalogue.dto.ModificationVarianteRequete;
import sn.boutique.catalogue.dto.ProduitAdminResumeReponse;
import sn.boutique.catalogue.service.VarianteAdminService;

/** Variantes dans le back-office. Réservé à ADMIN et SUPER_ADMIN (règle dans SecuriteConfig). */
@RestController
@RequestMapping("/api/admin/variantes")
@RequiredArgsConstructor
public class AdminVarianteControleur {

    private final VarianteAdminService varianteAdminService;

    /** Exemple : {@code {"prix": 429000, "prixBarre": null}} retire le prix barré et baisse le prix. */
    @PatchMapping("/{id}")
    public ProduitAdminResumeReponse modifier(@PathVariable Long id, @Valid @RequestBody ModificationVarianteRequete requete) {
        return varianteAdminService.modifier(id, requete);
    }
}
