package sn.boutique.catalogue.web;

import java.util.Map;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.catalogue.dto.ModificationVarianteRequete;
import sn.boutique.catalogue.dto.ProduitAdminResumeReponse;
import sn.boutique.catalogue.service.VarianteAdminService;

/** Variantes dans le back-office. Réservé à ADMIN et SUPER_ADMIN (règle dans SecuriteConfig). */
@RestController
@RequestMapping("/api/admin/variantes")
@RequiredArgsConstructor
public class AdminVarianteControleur {

    private final VarianteAdminService varianteAdminService;
    private final JournalAdministration journal;

    /** Exemple : {@code {"prix": 429000, "prixBarre": null}} retire le prix barré et baisse le prix. */
    @Transactional
    @PatchMapping("/{id}")
    public ProduitAdminResumeReponse modifier(@PathVariable Long id, @Valid @RequestBody ModificationVarianteRequete requete) {
        ProduitAdminResumeReponse reponse = varianteAdminService.modifier(id, requete);
        journal.enregistrer("VARIANTE_MODIFIEE", "VarianteProduit", id, null, Map.of("champs", requete.toString()));
        return reponse;
    }
}
