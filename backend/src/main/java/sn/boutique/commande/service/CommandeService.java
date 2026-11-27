package sn.boutique.commande.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.boutique.authentification.securite.GenerateurJetons;
import sn.boutique.catalogue.modele.MediaProduit;
import sn.boutique.catalogue.modele.Produit;
import sn.boutique.commande.depot.CommandeDepot;
import sn.boutique.commande.depot.ZoneLivraisonDepot;
import sn.boutique.commande.dto.CommandeCreeeReponse;
import sn.boutique.commande.dto.CreationCommandeRequete;
import sn.boutique.commande.dto.CreationCommandeRequete.AttributionRequete;
import sn.boutique.commande.modele.AdresseLivraison;
import sn.boutique.commande.modele.AttributionMarketing;
import sn.boutique.commande.modele.Commande;
import sn.boutique.commande.modele.ModeLivraison;
import sn.boutique.commande.modele.ModePaiement;
import sn.boutique.commande.modele.NumeroCommande;
import sn.boutique.commande.modele.Paiement;
import sn.boutique.commande.modele.StatutCommande;
import sn.boutique.commande.modele.TypeFournisseurPaiement;
import sn.boutique.commande.modele.ZoneLivraison;
import sn.boutique.commande.paiement.PaiementService;
import sn.boutique.compte.depot.UtilisateurDepot;
import sn.boutique.compte.modele.Utilisateur;
import sn.boutique.contenu.depot.ParametresSiteDepot;
import sn.boutique.contenu.modele.ParametresSite;
import sn.boutique.marketing.depot.CodePromoDepot;
import sn.boutique.marketing.modele.CodePromo;
import sn.boutique.marketing.service.ReservationCodePromo;
import sn.boutique.noyau.consentement.ConsentementService;
import sn.boutique.noyau.consentement.ConsentementService.Finalite;
import sn.boutique.noyau.erreur.ConflitException;
import sn.boutique.noyau.erreur.RegleMetierException;
import sn.boutique.noyau.erreur.RessourceIntrouvableException;
import sn.boutique.noyau.telephone.TelephoneSenegal;
import sn.boutique.panier.dto.PanierReponse;
import sn.boutique.panier.modele.LignePanier;
import sn.boutique.panier.modele.Panier;
import sn.boutique.panier.service.PanierService;
import sn.boutique.panier.service.PanierService.Demandeur;
import sn.boutique.stock.service.StockService;

/**
 * Création d'une commande à partir du panier, <strong>en une seule transaction</strong> (ADR 0008) :
 * clé d'idempotence, contrôles, réservation du stock, réservation du code promo, lignes figées, historique,
 * vidage du panier. Une erreur, où qu'elle survienne, annule tout.
 * <p>
 * Le paiement en ligne n'est pas démarré ici (appel réseau à un prestataire) : il l'est juste après le commit par
 * {@link PriseDeCommandeService}. Si le prestataire ne répond pas, la commande existe, le stock est réservé, et le
 * client reprend le paiement depuis sa page de suivi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandeService {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final Pattern FORMAT_CLE = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final Pattern CARACTERES_DE_CONTROLE = Pattern.compile("\\p{Cntrl}");
    private static final String AUTEUR_CLIENT = "CLIENT";

    private final CommandeDepot commandeDepot;
    private final ZoneLivraisonDepot zoneDepot;
    private final UtilisateurDepot utilisateurDepot;
    private final ParametresSiteDepot parametresDepot;
    private final CodePromoDepot codePromoDepot;
    private final PanierService panierService;
    private final StockService stockService;
    private final ReservationCodePromo reservationCodePromo;
    private final CleIdempotenceService cles;
    private final PaiementService paiementService;
    private final ConsentementService consentement;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    /**
     * Origine technique de la requête, enregistrée avec la commande (lutte contre la fraude).
     *
     * @param adresseIp adresse vue par le serveur (valve RemoteIp) ; jamais celle d'un en-tête falsifiable
     */
    public record ContexteRequete(String adresseIp, String userAgent) {
    }

    /**
     * Commande créée (ou retrouvée par sa clé d'idempotence).
     *
     * @param jetonSuivi jeton du lien de suivi : n'existe qu'ici, la base n'en garde que l'empreinte
     */
    public record Creation(Commande commande, boolean rejouee, String jetonSuivi) {

        @Override
        public String toString() {
            return "Creation[commande=" + commande.getNumero() + ", rejouee=" + rejouee + "]";
        }
    }

    @Transactional
    public CommandeCreeeReponse creer(Demandeur demandeur, String cle, CreationCommandeRequete requete,
                                      ContexteRequete contexte) {
        Creation creation = creerCommande(demandeur, cle, requete, contexte);
        return reponse(creation.commande(), creation.rejouee(), creation.jetonSuivi());
    }

    Creation creerCommande(Demandeur demandeur, String cle, CreationCommandeRequete requete,
                           ContexteRequete contexte) {
        if (cle == null || !FORMAT_CLE.matcher(cle).matches()) {
            throw new RegleMetierException("CLE_IDEMPOTENCE_INVALIDE",
                    "La demande n'est pas valide : rechargez la page et recommencez.");
        }
        String portee = portee(demandeur);
        CleIdempotenceService.Etat etat = cles.reserver(portee, cle, requete.empreinte());
        if (!etat.nouvelle()) {
            Commande commande = retrouver(etat, requete);
            // Le jeton n'est pas stocké : la réponse perdue ne peut pas être redonnée. Un nouveau jeton remplace
            // l'ancien, et le nouveau lien est renvoyé au client (e-mail, message) puisque l'ancien est mort.
            String jeton = commande.renouvelerJetonSuivi();
            evenements.publishEvent(new LienSuiviRenouvele(commande.getId(), jeton));
            return new Creation(commande, true, jeton);
        }

        ParametresSite parametres = parametresDepot.findById(ParametresSite.ID_UNIQUE)
                .orElseThrow(() -> new IllegalStateException("Paramètres du site absents"));
        Panier panier = panierService.panierPourCommande(demandeur)
                .orElseThrow(() -> new RegleMetierException("PANIER_VIDE", "Votre panier est vide."));
        ZoneLivraison zone = controlerLivraison(requete, parametres);
        PanierReponse montants = panierService.calculer(panier, zone);
        controlerPanier(montants, requete);
        controlerPaiement(requete.modePaiement(), montants.total(), requete.telephone(), parametres);

        Utilisateur utilisateur = demandeur.utilisateurId() == null ? null
                : utilisateurDepot.getReferenceById(demandeur.utilisateurId());
        String telephone = TelephoneSenegal.normaliserOuEchouer(requete.telephone());
        reserverCodePromo(montants, demandeur, telephone);

        Instant maintenant = horloge.instant();
        boolean enLigne = requete.modePaiement().enLigne();
        Commande commande = new Commande(
                NumeroCommande.formater(maintenant.atZone(DAKAR).getYear(), commandeDepot.prochaineValeurNumero()),
                email(requete.email(), utilisateur), telephone, requete.modeLivraison(), requete.modePaiement());
        commande.setNomClient(nettoyer(requete.nomComplet(), 120));
        commande.setUtilisateur(utilisateur);
        commande.setZoneLivraison(zone);
        commande.setAdresseLivraison(adresse(requete, telephone));
        commande.setFraisLivraison(montants.fraisLivraison() == null ? 0 : montants.fraisLivraison());
        commande.setRemise(montants.remise());
        commande.setCodePromo(montants.codePromo() == null ? null : montants.codePromo().code());
        commande.setAttribution(attribution(requete.attribution(), contexte));
        String jetonSuivi = commande.renouvelerJetonSuivi();
        for (LignePanier ligne : panier.getLignes()) {
            commande.ajouterLigne(ligne.getVariante(), imagePrincipale(ligne.getVariante().getProduit()),
                    ligne.getQuantite());
        }
        commande.recalculerTotaux();
        if (commande.getTotal() != montants.total()) {
            // Ne devrait jamais arriver : le panier et la commande utilisent les mêmes lignes et les mêmes frais
            throw new IllegalStateException("Total de la commande %s (%d) différent du panier (%d)"
                    .formatted(commande.getNumero(), commande.getTotal(), montants.total()));
        }
        if (enLigne) {
            commande.initialiserStatut(StatutCommande.EN_ATTENTE_PAIEMENT, AUTEUR_CLIENT, "Commande créée");
            commande.setExpireLe(maintenant.plus(Duration.ofMinutes(parametres.getDelaiPaiementMobileMinutes())));
        } else {
            commande.initialiserStatut(StatutCommande.EN_ATTENTE_CONFIRMATION, AUTEUR_CLIENT,
                    "Commande créée, paiement à la livraison : en attente de l'appel du service client");
            commande.setExpireLe(maintenant.plus(Duration.ofHours(parametres.getPaiementLivraisonDelaiAnnulationHeures())));
            commande.getPaiements().add(new Paiement(commande, TypeFournisseurPaiement.A_LA_LIVRAISON,
                    commande.getTotal()));
        }
        commandeDepot.saveAndFlush(commande);

        stockService.reserver(commande);
        panierService.vider(panier);
        cles.lier(portee, cle, commande.getId());
        evenements.publishEvent(new CommandeCreee(commande.getId(), jetonSuivi));
        log.info("Commande {} créée ({} FCFA, {})", commande.getNumero(), commande.getTotal(),
                commande.getModePaiement());
        return new Creation(commande, false, jetonSuivi);
    }

    // ------------------------------------------------------------------ idempotence

    /** Clé liée au client : un compte, ou le panier invité (empreinte du jeton du cookie). */
    private static String portee(Demandeur demandeur) {
        if (demandeur.utilisateurId() != null) {
            return "u:" + demandeur.utilisateurId();
        }
        String jeton = demandeur.jetonInvite();
        if (jeton == null || jeton.isBlank()) {
            throw new RegleMetierException("PANIER_VIDE", "Votre panier est vide.");
        }
        return "i:" + GenerateurJetons.empreinte(jeton);
    }

    private Commande retrouver(CleIdempotenceService.Etat etat, CreationCommandeRequete requete) {
        if (!requete.empreinte().equals(etat.empreinte())) {
            throw new ConflitException("CLE_IDEMPOTENCE_REUTILISEE",
                    "Cette demande a déjà servi pour une autre commande : rechargez la page et recommencez.");
        }
        if (etat.commandeId() == null) {
            throw new ConflitException("COMMANDE_EN_COURS",
                    "Votre commande est en cours de traitement : patientez un instant.");
        }
        // Verrouillée : deux rejeux simultanés se suivent au lieu de se disputer le jeton
        return commandeDepot.findByIdPourMiseAJour(etat.commandeId())
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", etat.commandeId()));
    }

    // ------------------------------------------------------------------ contrôles

    private ZoneLivraison controlerLivraison(CreationCommandeRequete requete, ParametresSite parametres) {
        if (requete.modeLivraison() == ModeLivraison.RETRAIT_MAGASIN) {
            if (!parametres.isBoutiquePhysique()) {
                throw new RegleMetierException("RETRAIT_INDISPONIBLE",
                        "Le retrait en boutique n'est pas proposé : choisissez la livraison à domicile.");
            }
            return null;
        }
        ZoneLivraison zone = requete.zoneId() == null ? null
                : zoneDepot.findById(requete.zoneId()).filter(ZoneLivraison::isActif).orElse(null);
        if (zone == null) {
            throw new RegleMetierException("ZONE_INVALIDE", "Choisissez une zone de livraison.");
        }
        if (vide(requete.ville()) || vide(requete.quartier()) || (vide(requete.adresse())
                && vide(requete.pointDeRepere()))) {
            throw new RegleMetierException("ADRESSE_INCOMPLETE",
                    "Indiquez la ville, le quartier, et l'adresse ou un point de repère.");
        }
        return zone;
    }

    private static void controlerPanier(PanierReponse montants, CreationCommandeRequete requete) {
        if (montants.lignes().isEmpty()) {
            throw new RegleMetierException("PANIER_VIDE", "Votre panier est vide.");
        }
        if (montants.codePromo() != null && !montants.codePromo().valide()) {
            throw new RegleMetierException(montants.codePromo().erreur(), montants.codePromo().message());
        }
        if (!montants.commandable()) {
            throw new ConflitException(montants.prixModifies() ? "PRIX_MODIFIES" : "PANIER_NON_COMMANDABLE",
                    montants.prixModifies()
                            ? "Le prix d'un article a changé : vérifiez votre panier avant de commander."
                            : "Un article n'est plus disponible dans la quantité demandée : vérifiez votre panier.");
        }
        if (requete.totalAttendu() != montants.total()) {
            throw new ConflitException("TOTAL_MODIFIE",
                    "Le total de votre commande a changé : il est maintenant de %d FCFA. Vérifiez-le avant de payer."
                            .formatted(montants.total()));
        }
    }

    private void controlerPaiement(ModePaiement mode, long total, String telephone, ParametresSite parametres) {
        if (mode.enLigne()) {
            if (!paiementService.enLigneDisponible()) {
                throw new RegleMetierException("MODE_PAIEMENT_INDISPONIBLE",
                        "Le paiement en ligne n'est pas disponible pour le moment : choisissez le paiement à la "
                                + "livraison ou contactez-nous.");
            }
            return;
        }
        if (mode != ModePaiement.A_LA_LIVRAISON || !parametres.isPaiementLivraisonActif()) {
            throw new RegleMetierException("MODE_PAIEMENT_INDISPONIBLE", "Ce mode de paiement n'est pas proposé.");
        }
        if (total > parametres.getPaiementLivraisonMontantMax()) {
            throw new RegleMetierException("PAIEMENT_LIVRAISON_MONTANT_MAX",
                    "Le paiement à la livraison est limité à %d FCFA : payez en ligne pour cette commande."
                            .formatted(parametres.getPaiementLivraisonMontantMax()));
        }
        long enAttente = commandeDepot.countByTelephoneClientAndStatut(TelephoneSenegal.normaliserOuEchouer(telephone),
                StatutCommande.EN_ATTENTE_CONFIRMATION);
        if (enAttente >= parametres.getPaiementLivraisonMaxNonConfirmees()) {
            throw new RegleMetierException("PAIEMENT_LIVRAISON_LIMITE",
                    "Vous avez déjà %d commande(s) à la livraison en attente de confirmation : notre équipe vous "
                            .formatted(enAttente) + "appelle. Vous pouvez payer en ligne cette nouvelle commande.");
        }
    }

    /**
     * Utilisation du code promo réservée maintenant, rendue si la commande est annulée ou expire. Plafond total
     * (UPDATE conditionnel) puis plafond par client (compte, ou numéro de téléphone pour un invité).
     */
    private void reserverCodePromo(PanierReponse montants, Demandeur demandeur, String telephone) {
        if (montants.codePromo() == null) {
            return;
        }
        String code = montants.codePromo().code();
        if (!reservationCodePromo.reserver(code)) {
            throw new RegleMetierException("CODE_PROMO_EPUISE",
                    "Ce code promo a atteint son nombre maximal d'utilisations.");
        }
        CodePromo codePromo = codePromoDepot.findByCode(code).orElseThrow();
        if (codePromo.getUtilisationsParClient() != null) {
            long deja = demandeur.utilisateurId() != null
                    ? commandeDepot.countByUtilisateurIdAndCodePromoAndStatutNot(demandeur.utilisateurId(), code,
                            StatutCommande.ANNULEE)
                    : commandeDepot.countByTelephoneClientAndCodePromoAndStatutNot(telephone, code,
                            StatutCommande.ANNULEE);
            if (deja >= codePromo.getUtilisationsParClient()) {
                throw new RegleMetierException("CODE_PROMO_DEJA_UTILISE", "Vous avez déjà utilisé ce code promo.");
            }
        }
    }

    // ------------------------------------------------------------------ construction

    private static String email(String saisie, Utilisateur utilisateur) {
        String email = nettoyer(saisie, 254);
        if (email == null && utilisateur != null) {
            return utilisateur.getEmail();
        }
        return email == null ? null : email.toLowerCase(java.util.Locale.ROOT);
    }

    private static AdresseLivraison adresse(CreationCommandeRequete requete, String telephone) {
        String telephoneLivraison = requete.telephoneLivraison() == null || requete.telephoneLivraison().isBlank()
                ? telephone : requete.telephoneLivraison();
        if (requete.modeLivraison() == ModeLivraison.RETRAIT_MAGASIN) {
            return null;
        }
        return new AdresseLivraison(nettoyer(requete.nomComplet(), 120), telephoneLivraison,
                nettoyer(requete.ville(), 80), nettoyer(requete.quartier(), 80),
                nettoyer(requete.adresse(), 255), nettoyer(requete.pointDeRepere(), 255));
    }

    /**
     * Les UTM, la page d'arrivée et le référent sont gardés (nettoyés, tronqués). Les identifiants de clic et
     * cookies Meta ne le sont qu'avec le consentement du visiteur (coquille « refusé » jusqu'en phase 7).
     */
    private AttributionMarketing attribution(AttributionRequete saisie, ContexteRequete contexte) {
        AttributionMarketing attribution = new AttributionMarketing();
        attribution.setAdresseIp(nettoyer(contexte.adresseIp(), 45));
        attribution.setUserAgent(nettoyer(contexte.userAgent(), 255));
        if (saisie == null) {
            return attribution;
        }
        attribution.setUtmSource(nettoyer(saisie.utmSource(), 100));
        attribution.setUtmMedium(nettoyer(saisie.utmMedium(), 100));
        attribution.setUtmCampagne(nettoyer(saisie.utmCampagne(), 150));
        attribution.setUtmContenu(nettoyer(saisie.utmContenu(), 150));
        attribution.setUtmTerme(nettoyer(saisie.utmTerme(), 150));
        attribution.setPageArrivee(nettoyer(saisie.pageArrivee(), 500));
        attribution.setReferent(nettoyer(saisie.referent(), 500));
        attribution.setPremiereSource(nettoyer(saisie.premiereSource(), 100));
        attribution.setPremierMedium(nettoyer(saisie.premierMedium(), 100));
        attribution.setPremiereCampagne(nettoyer(saisie.premiereCampagne(), 150));
        attribution.setPremierePageArrivee(nettoyer(saisie.premierePageArrivee(), 500));
        if (consentement.autorise(Finalite.MESURE_PUBLICITAIRE)) {
            attribution.setGclid(nettoyer(saisie.gclid(), 255));
            attribution.setFbclid(nettoyer(saisie.fbclid(), 255));
            attribution.setFbp(nettoyer(saisie.fbp(), 100));
            attribution.setFbc(nettoyer(saisie.fbc(), 300));
        }
        return attribution;
    }

    private static String imagePrincipale(Produit produit) {
        return produit.getMedias().stream().filter(MediaProduit::isPrincipal).findFirst()
                .map(MediaProduit::getUrl).orElse(null);
    }

    /** Sans caractères de contrôle (journaux, e-mails), espaces superflus retirés, tronqué ; vide devient null. */
    static String nettoyer(String texte, int longueurMax) {
        if (texte == null) {
            return null;
        }
        String propre = CARACTERES_DE_CONTROLE.matcher(texte).replaceAll(" ").strip();
        if (propre.isEmpty()) {
            return null;
        }
        return propre.length() > longueurMax ? propre.substring(0, longueurMax) : propre;
    }

    private static boolean vide(String texte) {
        return texte == null || texte.isBlank();
    }

    private static CommandeCreeeReponse reponse(Commande commande, boolean rejouee, String jetonSuivi) {
        return new CommandeCreeeReponse(commande.getNumero(), jetonSuivi, commande.getStatut(),
                commande.getModePaiement(), commande.getTotal(), commande.getExpireLe(), null, false, rejouee);
    }

    /**
     * Publié dans la transaction de création ; les écouteurs agissent après le commit. Porte le jeton de suivi en
     * clair (en mémoire seulement) pour le lien envoyé au client : jamais journalisé.
     */
    public record CommandeCreee(long commandeId, String jetonSuivi) {

        @Override
        public String toString() {
            return "CommandeCreee[commandeId=" + commandeId + "]";
        }
    }

    /** Un nouveau lien de suivi a remplacé l'ancien (requête rejouée, demande du service client). */
    public record LienSuiviRenouvele(long commandeId, String jetonSuivi) {

        @Override
        public String toString() {
            return "LienSuiviRenouvele[commandeId=" + commandeId + "]";
        }
    }
}
