package sn.boutique.commande.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import sn.boutique.administration.journal.JournalAdministration;
import sn.boutique.administration.securite.ReauthentificationRequise;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.boutique.commande.facture.MentionsFactureService;
import sn.boutique.commande.facture.MentionsFactureService.MentionsFactureReponse;
import sn.boutique.commande.facture.MentionsFactureService.MentionsFactureRequete;

/**
 * Mentions légales et régime de TVA des factures. Réservé à ADMIN et SUPER_ADMIN (règle
 * {@code /api/admin/parametres/**} de SecuriteConfig). L'écran viendra en phase 6.
 */
@RestController
@RequestMapping("/api/admin/parametres/facturation")
@RequiredArgsConstructor
public class AdminMentionsFactureControleur {

    private final MentionsFactureService service;
    private final JournalAdministration journal;

    /** Mentions de l'entreprise (pas des données client) : raison sociale, NINEA, RCCM, régime de TVA. */
    private static Map<String, Object> valeurs(MentionsFactureReponse m) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("raisonSociale", m.raisonSociale());
        valeurs.put("ninea", m.ninea());
        valeurs.put("rccm", m.rccm());
        valeurs.put("regimeTva", String.valueOf(m.regimeTva()));
        valeurs.put("tauxTvaBp", m.tauxTvaBp());
        valeurs.put("mentionTva", m.mentionTva());
        return valeurs;
    }

    @GetMapping
    public ResponseEntity<MentionsFactureReponse> lire() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.lire());
    }

    @Transactional
    @ReauthentificationRequise
    @PutMapping
    public ResponseEntity<MentionsFactureReponse> modifier(@Valid @RequestBody MentionsFactureRequete requete) {
        MentionsFactureReponse avant = service.lire();
        MentionsFactureReponse apres = service.modifier(requete);
        journal.enregistrer("MENTIONS_FACTURATION_MODIFIEES", "ParametresSite", "facturation", valeurs(avant), valeurs(apres));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(apres);
    }
}
