package sn.boutique.compte.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.compte.depot.AdresseDepot;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.dto.AdresseReponse;
import sn.boutique.compte.dto.AdresseRequete;
import sn.boutique.compte.modele.Adresse;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;

/**
 * Carnet d'adresses. Règles : au plus {@value #MAXIMUM} adresses ; une seule adresse par défaut (la première enregistrée
 * l'est d'office) ; supprimer l'adresse par défaut promeut la plus récente. Les adresses d'un autre compte sont
 * introuvables (404), jamais « interdites » : on ne révèle pas leur existence.
 */
@Service
@RequiredArgsConstructor
public class AdresseService {

    static final int MAXIMUM = 10;

    private final AdresseDepot depot;
    private final UtilisateurDepot utilisateurDepot;

    @Transactional(readOnly = true)
    public List<AdresseReponse> lister(Long utilisateurId) {
        return depot.findByUtilisateurIdOrderByParDefautDescDateCreationAsc(utilisateurId).stream()
                .map(AdresseReponse::de).toList();
    }

    @Transactional
    public AdresseReponse creer(Long utilisateurId, AdresseRequete requete) {
        long existantes = depot.countByUtilisateurId(utilisateurId);
        if (existantes >= MAXIMUM) {
            throw new RegleMetierException("TROP_D_ADRESSES",
                    "Vous pouvez enregistrer %d adresses au plus. Supprimez-en une pour en ajouter une autre.".formatted(MAXIMUM));
        }
        Adresse adresse = new Adresse(utilisateurDepot.getReferenceById(utilisateurId), requete.libelle().strip(),
                requete.nomComplet().strip(), requete.telephone(), requete.ville().strip(), requete.quartier().strip());
        appliquerFacultatifs(adresse, requete);
        boolean parDefaut = existantes == 0 || Boolean.TRUE.equals(requete.parDefaut());
        if (parDefaut) {
            depot.retirerParDefaut(utilisateurId);
        }
        adresse.setParDefaut(parDefaut);
        return AdresseReponse.de(depot.save(adresse));
    }

    @Transactional
    public AdresseReponse modifier(Long utilisateurId, Long id, AdresseRequete requete) {
        Adresse adresse = lire(utilisateurId, id);
        boolean devientParDefaut = Boolean.TRUE.equals(requete.parDefaut()) && !adresse.isParDefaut();
        if (devientParDefaut) {
            depot.retirerParDefaut(utilisateurId);
            adresse = lire(utilisateurId, id); // la mise à jour de masse a vidé le contexte de persistance
            adresse.setParDefaut(true);
        }
        appliquerChamps(adresse, requete);
        return AdresseReponse.de(depot.save(adresse));
    }

    @Transactional
    public AdresseReponse definirParDefaut(Long utilisateurId, Long id) {
        Adresse adresse = lire(utilisateurId, id);
        if (!adresse.isParDefaut()) {
            depot.retirerParDefaut(utilisateurId);
            adresse = lire(utilisateurId, id);
            adresse.setParDefaut(true);
        }
        return AdresseReponse.de(depot.save(adresse));
    }

    @Transactional
    public void supprimer(Long utilisateurId, Long id) {
        Adresse adresse = lire(utilisateurId, id);
        boolean etaitParDefaut = adresse.isParDefaut();
        depot.delete(adresse);
        depot.flush();
        if (etaitParDefaut) {
            depot.findFirstByUtilisateurIdOrderByDateCreationDesc(utilisateurId).ifPresent(a -> a.setParDefaut(true));
        }
    }

    private Adresse lire(Long utilisateurId, Long id) {
        return depot.findByIdAndUtilisateurId(id, utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Adresse", String.valueOf(id)));
    }

    private static void appliquerChamps(Adresse adresse, AdresseRequete requete) {
        adresse.setLibelle(requete.libelle().strip());
        adresse.setNomComplet(requete.nomComplet().strip());
        adresse.setTelephone(requete.telephone());
        adresse.setVille(requete.ville().strip());
        adresse.setQuartier(requete.quartier().strip());
        appliquerFacultatifs(adresse, requete);
    }

    private static void appliquerFacultatifs(Adresse adresse, AdresseRequete requete) {
        adresse.setAdresseLigne(videEnNull(requete.adresseLigne()));
        adresse.setPointDeRepere(videEnNull(requete.pointDeRepere()));
    }

    private static String videEnNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }
}
