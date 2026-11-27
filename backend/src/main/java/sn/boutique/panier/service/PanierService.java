package sn.boutique.panier.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.catalogue.depot.VarianteProduitDepot;
import sn.boutique.catalogue.dto.Disponibilite;
import sn.boutique.catalogue.dto.ImageReponse;
import sn.boutique.catalogue.modele.Categorie;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.catalogue.modele.VarianteProduit;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.ZoneLivraisonDepot;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.marketing.depot.CodePromoDepot;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.service.EvaluationCodePromo;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;
import sn.boutique.noyau.erreur.StockInsuffisantException;
import sn.boutique.panier.depot.PanierDepot;
import sn.boutique.panier.dto.LignePanierReponse;
import sn.boutique.panier.dto.LignePanierReponse.Probleme;
import sn.boutique.panier.dto.PanierReponse;
import sn.boutique.panier.modele.LignePanier;
import sn.boutique.panier.modele.Panier;

/**
 * Panier d'un client connecté ou d'un visiteur (cookie {@code panier_invite}).
 * <ul>
 *   <li>Le frontend n'envoie qu'une variante et une quantité : prix, remise et total sont recalculés ici à chaque
 *   réponse, à partir de la base.</li>
 *   <li>Quantité d'une ligne plafonnée par le stock et par la limite par ligne ({@code VarianteProduit.quantiteMax},
 *   sinon {@code ParametresSite.quantiteMaxParLigne}) ; le stock exact n'est jamais exposé.</li>
 *   <li>Prix vu : si le prix d'une ligne a changé depuis que le client l'a vu, la réponse donne l'ancien prix et
 *   le panier n'est plus commandable tant que le client n'a pas accepté les nouveaux prix.</li>
 *   <li>Fusion : dès qu'un client connecté présente encore un cookie de panier invité, les lignes invitées sont
 *   ajoutées à son panier (plafonnées), le panier invité est supprimé et le cookie effacé.</li>
 *   <li>Le cookie contient un jeton aléatoire (256 bits) ; la base n'en garde que l'empreinte SHA-256.</li>
 * </ul>
 * Un simple visiteur qui lit le panier sans rien y avoir mis ne crée ni panier ni cookie.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PanierService {

    private static final Pattern FORMAT_JETON = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern FORMAT_CODE = Pattern.compile("[A-Z0-9_-]{3,40}");
    private static final int QUANTITE_MAX_PAR_DEFAUT = 5;
    private static final SecureRandom ALEA = new SecureRandom();

    private final PanierDepot panierDepot;
    private final VarianteProduitDepot varianteDepot;
    private final UtilisateurDepot utilisateurDepot;
    private final CodePromoDepot codePromoDepot;
    private final CommandeDepot commandeDepot;
    private final ZoneLivraisonDepot zoneDepot;
    private final ParametresSiteDepot parametresDepot;
    private final Clock horloge;

    /** Qui demande : client connecté (identifiant du JWT) et/ou jeton du cookie invité (tel que reçu). */
    public record Demandeur(Long utilisateurId, String jetonInvite) {
    }

    /**
     * @param nouveauJetonInvite jeton à poser en cookie (premier ajout d'un visiteur), sinon null
     * @param effacerCookie      cookie invité à effacer (fusionné, inconnu ou expiré)
     */
    public record Resultat(PanierReponse panier, String nouveauJetonInvite, boolean effacerCookie) {
    }

    /** Panier courant (null si le visiteur n'en a pas), avec les messages à transmettre au client. */
    private record Courant(Panier panier, String nouveauJeton, boolean effacerCookie, List<String> avertissements) {
    }

    @Transactional
    public Resultat lire(Demandeur demandeur, Long zoneId) {
        return repondre(resoudre(demandeur, false), zoneId);
    }

    @Transactional
    public Resultat ajouter(Demandeur demandeur, long varianteId, int quantite, Long zoneId) {
        VarianteProduit variante = varianteDepot.findById(varianteId)
                .filter(PanierService::publiee)
                .orElseThrow(() -> new RessourceIntrouvableException("Variante", varianteId));
        Courant courant = resoudre(demandeur, true);
        int max = quantiteCommandable(variante, limiteParLigne(variante));
        int deja = courant.panier().ligne(variante).map(LignePanier::getQuantite).orElse(0);
        if (max == 0 || deja >= max) {
            throw refusQuantite(variante, deja + quantite);
        }
        if (deja + quantite > max) {
            courant.avertissements().add(plafonnement(variante, max));
            courant.panier().ajouter(variante, max - deja);
        } else {
            courant.panier().ajouter(variante, quantite);
        }
        return repondre(courant, zoneId);
    }

    @Transactional
    public Resultat modifierQuantite(Demandeur demandeur, long ligneId, int quantite, Long zoneId) {
        Courant courant = resoudre(demandeur, false);
        LignePanier ligne = ligneDuPanier(courant, ligneId);
        VarianteProduit variante = ligne.getVariante();
        int max = publiee(variante) ? quantiteCommandable(variante, limiteParLigne(variante)) : 0;
        if (max == 0) {
            throw refusQuantite(variante, quantite);
        }
        if (quantite > max) {
            courant.avertissements().add(plafonnement(variante, max));
        }
        ligne.setQuantite(Math.min(quantite, max));
        return repondre(courant, zoneId);
    }

    @Transactional
    public Resultat supprimerLigne(Demandeur demandeur, long ligneId, Long zoneId) {
        Courant courant = resoudre(demandeur, false);
        LignePanier ligne = ligneDuPanier(courant, ligneId);
        courant.panier().getLignes().remove(ligne);
        return repondre(courant, zoneId);
    }

    /**
     * Enregistre le code s'il s'applique maintenant ou peut s'appliquer plus tard (montant minimum, catégories) ;
     * refuse (422) un code inconnu, expiré, épuisé ou déjà utilisé par ce client, sans l'enregistrer.
     */
    @Transactional
    public Resultat appliquerCodePromo(Demandeur demandeur, String saisie, Long zoneId) {
        String code = CodePromo.normaliserCode(saisie);
        Optional<CodePromo> codePromo = FORMAT_CODE.matcher(code).matches()
                ? codePromoDepot.findByCode(code)
                : Optional.empty();
        Courant courant = resoudre(demandeur, true);
        EvaluationCodePromo.Resultat evaluation = evaluer(codePromo.orElse(null), courant.panier());
        if (evaluation.erreur() != null && EvaluationCodePromo.REFUS_DEFINITIFS.contains(evaluation.erreur())) {
            throw new RegleMetierException(evaluation.erreur(), evaluation.message());
        }
        courant.panier().setCodePromo(code);
        return repondre(courant, zoneId);
    }

    @Transactional
    public Resultat retirerCodePromo(Demandeur demandeur, Long zoneId) {
        Courant courant = resoudre(demandeur, false);
        if (courant.panier() != null) {
            courant.panier().setCodePromo(null);
        }
        return repondre(courant, zoneId);
    }

    /** Le client a vu les nouveaux prix (« Le prix de cet article est passé de X à Y ») et les accepte. */
    @Transactional
    public Resultat accepterPrix(Demandeur demandeur, Long zoneId) {
        Courant courant = resoudre(demandeur, false);
        if (courant.panier() != null) {
            courant.panier().getLignes().forEach(LignePanier::accepterPrixActuel);
        }
        return repondre(courant, zoneId);
    }

    /**
     * Panier du demandeur (fusionné si besoin) verrouillé jusqu'à la fin de la transaction de la commande :
     * une modification du panier et la création de la commande ne s'exécutent jamais en même temps.
     */
    @Transactional
    public Optional<Panier> panierPourCommande(Demandeur demandeur) {
        Courant courant = resoudre(demandeur, false);
        if (courant.panier() == null) {
            return Optional.empty();
        }
        panierDepot.flush();
        return panierDepot.findByIdPourMiseAJour(courant.panier().getId());
    }

    /** Vide le panier une fois la commande créée (lignes et code promo). */
    public void vider(Panier panier) {
        panier.getLignes().clear();
        panier.setCodePromo(null);
    }

    // ------------------------------------------------------------------ résolution et fusion

    private Courant resoudre(Demandeur demandeur, boolean creer) {
        List<String> avertissements = new ArrayList<>();
        String jeton = demandeur.jetonInvite();
        boolean jetonValide = jeton != null && FORMAT_JETON.matcher(jeton).matches();
        Optional<Panier> invite = jetonValide ? panierDepot.findByJetonInvite(empreinte(jeton)) : Optional.empty();
        boolean effacer = jeton != null && invite.isEmpty();

        if (demandeur.utilisateurId() != null) {
            Optional<Panier> compte = panierDepot.findByUtilisateurId(demandeur.utilisateurId());
            if (invite.isPresent()) {
                Panier panier = compte.orElseGet(() -> nouveauPanierClient(demandeur.utilisateurId()));
                fusionner(invite.get(), panier, avertissements);
                return new Courant(panier, null, true, avertissements);
            }
            Panier panier = compte.orElseGet(() -> creer ? nouveauPanierClient(demandeur.utilisateurId()) : null);
            return new Courant(panier, null, effacer, avertissements);
        }
        if (invite.isPresent()) {
            return new Courant(invite.get(), null, false, avertissements);
        }
        if (!creer) {
            return new Courant(null, null, effacer, avertissements);
        }
        String nouveauJeton = genererJeton();
        Panier panier = panierDepot.save(Panier.pourInvite(empreinte(nouveauJeton)));
        return new Courant(panier, nouveauJeton, false, avertissements);
    }

    private Panier nouveauPanierClient(long utilisateurId) {
        if (!utilisateurDepot.existsById(utilisateurId)) {
            // Jeton d'accès encore valide (15 min) d'un compte supprimé entre-temps
            throw new RegleMetierException("COMPTE_SUPPRIME", "Ce compte n'existe plus : reconnectez-vous ou continuez sans compte.");
        }
        return panierDepot.save(Panier.pourUtilisateur(utilisateurDepot.getReferenceById(utilisateurId)));
    }

    /**
     * Lignes invitées ajoutées au panier du compte : quantités additionnées puis plafonnées (stock, limite par
     * ligne) ; lignes indisponibles ignorées. Le code promo du compte est gardé, sinon celui du panier invité.
     */
    private void fusionner(Panier invite, Panier compte, List<String> avertissements) {
        for (LignePanier ligne : invite.getLignes()) {
            VarianteProduit variante = ligne.getVariante();
            int max = publiee(variante) ? quantiteCommandable(variante, limiteParLigne(variante)) : 0;
            int deja = compte.ligne(variante).map(LignePanier::getQuantite).orElse(0);
            int ajout = Math.min(ligne.getQuantite(), max - deja);
            if (ajout > 0) {
                LignePanier fusionnee = compte.ajouter(variante, ajout);
                // Ligne nouvelle : elle garde le prix vu en invité, un changement de prix reste signalé
                if (deja == 0) {
                    fusionnee.restaurerPrixVu(ligne.getPrixVu());
                }
            }
            if (ajout < ligne.getQuantite()) {
                avertissements.add(max == 0
                        ? "« %s » n'est plus disponible : il n'a pas été ajouté à votre panier.".formatted(nom(variante))
                        : plafonnement(variante, max));
            }
        }
        if (compte.getCodePromo() == null) {
            compte.setCodePromo(invite.getCodePromo());
        }
        panierDepot.delete(invite);
        panierDepot.flush();
        log.info("Panier invité {} fusionné dans le panier d'un client", invite.getId());
    }

    private static LignePanier ligneDuPanier(Courant courant, long ligneId) {
        return Optional.ofNullable(courant.panier()).stream()
                .flatMap(panier -> panier.getLignes().stream())
                .filter(ligne -> ligne.getId() != null && ligne.getId() == ligneId)
                .findFirst()
                .orElseThrow(() -> new RessourceIntrouvableException("Ligne de panier", ligneId));
    }

    // ------------------------------------------------------------------ calcul

    private Resultat repondre(Courant courant, Long zoneId) {
        Panier panier = courant.panier();
        if (panier != null) {
            // Identifiants des nouvelles lignes, avant de les renvoyer
            panierDepot.flush();
        }
        PanierReponse reponse = panier == null ? PanierReponse.vide() : calculer(panier, zone(zoneId));
        return new Resultat(reponse.avecAvertissements(courant.avertissements()), courant.nouveauJeton(),
                courant.effacerCookie());
    }

    /** Montants du panier tels que la commande les reprendra (même calcul que l'affichage du panier). */
    public PanierReponse calculer(Panier panier, ZoneLivraison zone) {
        List<LignePanierReponse> lignes = new ArrayList<>();
        List<EvaluationCodePromo.Ligne> lignesCalculees = new ArrayList<>();
        int nombreArticles = 0;
        long sousTotal = 0;
        boolean prixModifies = false;
        boolean toutCommandable = !panier.getLignes().isEmpty();
        int limiteSite = limiteDuSite();
        for (LignePanier ligne : panier.getLignes()) {
            VarianteProduit variante = ligne.getVariante();
            Produit produit = variante.getProduit();
            int limite = variante.getQuantiteMax() != null ? variante.getQuantiteMax() : limiteSite;
            boolean disponible = publiee(variante) && variante.getStock() > 0;
            int max = disponible ? quantiteCommandable(variante, limite) : 0;
            Probleme probleme = !disponible ? Probleme.INDISPONIBLE
                    : ligne.getQuantite() > max ? Probleme.QUANTITE_TROP_ELEVEE
                    : null;
            long totalLigne = variante.getPrix() * ligne.getQuantite();
            Long prixPrecedent = ligne.prixModifie() ? ligne.getPrixVu() : null;
            nombreArticles += ligne.getQuantite();
            if (disponible) {
                sousTotal += totalLigne;
                lignesCalculees.add(new EvaluationCodePromo.Ligne(totalLigne, categories(produit.getCategorie())));
                prixModifies |= prixPrecedent != null;
            }
            toutCommandable &= probleme == null;
            lignes.add(new LignePanierReponse(ligne.getId(), variante.getId(), variante.getSku(), produit.getNom(),
                    produit.getSlug(), produit.getMarque().getNom(), produit.getCategorie().getNom(),
                    variante.getLibelle(), image(produit), variante.getPrix(), prixPrecedent, ligne.getQuantite(),
                    quantiteAffichee(limite, max),
                    disponible ? Disponibilite.depuisStock(variante.getStock(), variante.getSeuilAlerte())
                            : Disponibilite.RUPTURE,
                    totalLigne, probleme));
        }

        PanierReponse.CodePromoReponse code = null;
        long remise = 0;
        boolean livraisonOfferte = false;
        if (panier.getCodePromo() != null) {
            CodePromo codePromo = codePromoDepot.findByCode(panier.getCodePromo()).orElse(null);
            EvaluationCodePromo.Resultat evaluation = EvaluationCodePromo.evaluer(codePromo, lignesCalculees,
                    utilisationsClient(panier, codePromo), horloge.instant());
            code = new PanierReponse.CodePromoReponse(panier.getCodePromo(), evaluation.valide(), evaluation.erreur(),
                    evaluation.message());
            if (evaluation.valide()) {
                remise = Math.min(evaluation.remise(), sousTotal);
                livraisonOfferte = evaluation.livraisonOfferte();
            }
        }
        Long frais = zone == null ? null : livraisonOfferte ? 0L : zone.getTarif();
        long total = Math.max(0, sousTotal - remise + (frais == null ? 0 : frais));
        return new PanierReponse(lignes, nombreArticles, sousTotal, code, remise, frais, livraisonOfferte, total,
                prixModifies, toutCommandable && !prixModifies, List.of());
    }

    private EvaluationCodePromo.Resultat evaluer(CodePromo code, Panier panier) {
        List<EvaluationCodePromo.Ligne> lignes = panier.getLignes().stream()
                .filter(ligne -> publiee(ligne.getVariante()) && ligne.getVariante().getStock() > 0)
                .map(ligne -> new EvaluationCodePromo.Ligne(ligne.getVariante().getPrix() * ligne.getQuantite(),
                        categories(ligne.getVariante().getProduit().getCategorie())))
                .toList();
        return EvaluationCodePromo.evaluer(code, lignes, utilisationsClient(panier, code), horloge.instant());
    }

    /** Client connecté seulement : pour un invité, le plafond par client est contrôlé à la commande (téléphone). */
    private Integer utilisationsClient(Panier panier, CodePromo code) {
        if (code == null || panier.getUtilisateur() == null || code.getUtilisationsParClient() == null) {
            return null;
        }
        return Math.toIntExact(commandeDepot.countByUtilisateurIdAndCodePromoAndStatutNot(
                panier.getUtilisateur().getId(), code.getCode(), StatutCommande.ANNULEE));
    }

    private ZoneLivraison zone(Long zoneId) {
        return zoneId == null ? null : zoneDepot.findById(zoneId).filter(ZoneLivraison::isActif).orElse(null);
    }

    // ------------------------------------------------------------------ règles de quantité

    static boolean publiee(VarianteProduit variante) {
        Produit produit = variante.getProduit();
        return variante.isActif() && produit.isActif() && !produit.estRetire();
    }

    private int limiteParLigne(VarianteProduit variante) {
        return variante.getQuantiteMax() != null ? variante.getQuantiteMax() : limiteDuSite();
    }

    private int limiteDuSite() {
        return parametresDepot.findById(ParametresSite.ID_UNIQUE)
                .map(ParametresSite::getQuantiteMaxParLigne)
                .orElse(QUANTITE_MAX_PAR_DEFAUT);
    }

    private static int quantiteCommandable(VarianteProduit variante, int limite) {
        return Math.clamp(variante.getStock(), 0, limite);
    }

    /**
     * Quantité annoncée au client : la limite par ligne si le stock la couvre, sinon le stock plafonné à
     * {@link StockInsuffisantException#QUANTITE_MAX_AFFICHEE} (le stock exact n'est jamais révélé au-delà).
     */
    private static int quantiteAffichee(int limite, int max) {
        return max >= limite ? limite : Math.min(max, StockInsuffisantException.QUANTITE_MAX_AFFICHEE);
    }

    private RuntimeException refusQuantite(VarianteProduit variante, int demandee) {
        int limite = limiteParLigne(variante);
        if (publiee(variante) && variante.getStock() >= limite) {
            return new RegleMetierException("QUANTITE_MAXIMALE",
                    "« %s » est limité à %d par commande.".formatted(nom(variante), limite));
        }
        return new StockInsuffisantException(variante.getSku(), demandee, publiee(variante) ? variante.getStock() : 0);
    }

    private String plafonnement(VarianteProduit variante, int max) {
        return "Quantité de « %s » ramenée à %d (%s).".formatted(nom(variante), max,
                max >= limiteParLigne(variante) ? "maximum par commande" : "stock disponible");
    }

    private static String nom(VarianteProduit variante) {
        return variante.getProduit().getNom();
    }

    /** La catégorie et toutes ses parentes (un code ciblant « Ordinateurs » couvre « PC portables »). */
    private static Set<Long> categories(Categorie categorie) {
        Set<Long> ids = new HashSet<>();
        Categorie courante = categorie;
        // L'ensemble arrête la remontée en cas de cycle
        while (courante != null && ids.add(courante.getId())) {
            courante = courante.getParent();
        }
        return ids;
    }

    private static ImageReponse image(Produit produit) {
        return produit.getMedias().stream()
                .filter(MediaProduit::isPrincipal)
                .findFirst()
                .map(media -> new ImageReponse(media.getUrl(), media.getTexteAlternatif()))
                .orElse(null);
    }

    // ------------------------------------------------------------------ jeton invité

    private static String genererJeton() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    static String empreinte(String jeton) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(jeton.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
