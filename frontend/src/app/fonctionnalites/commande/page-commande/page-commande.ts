import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import {
  CreationCommande,
  ModePaiement,
  OptionsCommande,
  ZoneLivraison,
} from '../../../noyau/api/modeles';
import { AttributionService } from '../../../noyau/attribution/attribution.service';
import { CommandeService, ErreurCommande } from '../../../noyau/commande/commande.service';
import { ecrireSession, effacerSession, lireSession } from '../../../noyau/donnees/stockage-session';
import { articlesSuivis } from '../../../noyau/panier/articles-suivis';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { normaliserTelephone } from '../../../noyau/telephone-senegal';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';
import { Icone } from '../../../partage/composants/icone/icone';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';
import {
  BrouillonCommande,
  CLE_BROUILLON,
  Erreurs,
  aDesErreurs,
  brouillonVide,
  erreursCoordonnees,
  erreursLivraison,
  erreursPaiement,
  lireBrouillon,
} from '../brouillon-commande';

const ETAPES = ['Coordonnées', 'Livraison', 'Paiement', 'Récapitulatif'] as const;

/**
 * Tunnel de commande (`/commande`, noindex) : une seule page, quatre étapes repliables, invité possible.
 *
 * - Téléphone obligatoire (+221), e-mail facultatif mais recommandé.
 * - Un bouton d'action principale, toujours à portée du pouce (barre fixe en bas sur mobile).
 * - Les saisies sont gardées dans sessionStorage (rechargement, retour arrière) et effacées après la commande.
 * - « Commander » est désactivé pendant l'envoi ; la clé d'idempotence (CommandeService) rend un nouvel essai sans
 *   risque après une coupure réseau.
 * - Les montants affichés sont ceux du panier du serveur ; le total est renvoyé avec la commande et refusé s'il a
 *   changé entre-temps.
 */
@Component({
  selector: 'bi-page-commande',
  imports: [Bouton, Champ, FormsModule, Icone, PrixFcfaPipe, RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-commande.html',
  styleUrl: './page-commande.scss',
})
export class PageCommande {
  protected readonly panier = inject(PanierService);
  private readonly commandes = inject(CommandeService);
  private readonly suivi = inject(SuiviService);
  private readonly attribution = inject(AttributionService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  protected readonly etapes = ETAPES;
  protected readonly brouillon = signal<BrouillonCommande>(brouillonVide());
  protected readonly zones = signal<ZoneLivraison[]>([]);
  protected readonly options = signal<OptionsCommande | null>(null);
  /** Étape ouverte (0 à 3) et étapes déjà validées (pour pouvoir y revenir). */
  protected readonly etape = signal(0);
  protected readonly jusquA = signal(0);
  protected readonly erreurs = signal<Erreurs>({});
  protected readonly envoi = signal(false);
  protected readonly echec = signal<ErreurCommande | null>(null);
  protected readonly pret = signal(false);
  /** Récapitulatif déplié sur ordinateur (colonne de droite), replié sur mobile : le formulaire passe d'abord. */
  protected readonly recapOuvert = signal(false);

  private restaure = false;
  private commencee = false;

  protected readonly zone = computed(() => this.zones().find((z) => z.id === this.brouillon().zoneId) ?? null);
  protected readonly retrait = computed(() => this.brouillon().modeLivraison === 'RETRAIT_MAGASIN');
  /** Frais de livraison : 0 en retrait ou si offerts, null tant qu'aucune zone n'est choisie. */
  protected readonly frais = computed<number | null>(() => {
    if (this.retrait() || this.panier.panier()?.livraisonOfferte) return 0;
    return this.zone()?.tarif ?? null;
  });
  /** Total que le client voit, et que le serveur revérifie. */
  protected readonly total = computed(() => {
    const p = this.panier.panier();
    return p ? Math.max(0, p.sousTotal - p.remise + (this.frais() ?? 0)) : 0;
  });
  protected readonly livraisonPossible = computed(() => {
    const o = this.options();
    return !!o && o.paiementLivraison && this.total() <= o.paiementLivraisonMax;
  });
  protected readonly modesDisponibles = computed<ModePaiement[]>(() => {
    const o = this.options();
    if (!o) return [];
    const modes: ModePaiement[] = [];
    if (o.paiementEnLigne) modes.push('MOBILE_MONEY');
    if (this.livraisonPossible()) modes.push('A_LA_LIVRAISON');
    return modes;
  });
  protected readonly libelleAction = computed(() => {
    if (this.etape() < 3) return 'Continuer';
    return this.brouillon().modePaiement === 'A_LA_LIVRAISON' ? 'Confirmer la commande' : 'Payer maintenant';
  });
  protected readonly panierPret = computed(() => {
    const p = this.panier.panier();
    return !!p && p.lignes.length > 0 && p.commandable;
  });

  constructor() {
    inject(SeoService).definirPage({ titre: 'Commander', indexable: false });
    // Sauvegarde des saisies (après la restauration, sinon un brouillon vide écraserait le vrai)
    effect(() => {
      const valeur = this.brouillon();
      if (this.restaure) ecrireSession(CLE_BROUILLON, JSON.stringify(valeur));
    });
    afterNextRender(() => void this.demarrer());
  }

  private async demarrer(): Promise<void> {
    this.brouillon.set(lireBrouillon(lireSession(CLE_BROUILLON)));
    this.restaure = true;
    this.recapOuvert.set(this.document.defaultView?.matchMedia('(min-width: 64rem)').matches ?? false);
    const [options, zones] = await Promise.allSettled([
      this.commandes.options(),
      firstValueFrom(this.http.get<ZoneLivraison[]>('/api/livraison/zones')),
      this.panier.charger(),
    ]);
    if (options.status === 'fulfilled') this.options.set(options.value);
    if (zones.status === 'fulfilled') this.zones.set(zones.value);
    // Zone déjà choisie sur la page panier
    if (this.brouillon().zoneId === null && this.panier.zoneId() !== null) {
      this.maj('zoneId', this.panier.zoneId());
    }
    if (!this.options()?.retraitMagasin && this.retrait()) this.maj('modeLivraison', 'DOMICILE');
    this.pret.set(true);
    const p = this.panier.panier();
    if (p && this.panierPret() && !this.commencee) {
      this.commencee = true;
      this.suivi.commencerCommande(articlesSuivis(p), Math.max(0, p.sousTotal - p.remise));
    }
  }

  protected maj<K extends keyof BrouillonCommande>(cle: K, valeur: BrouillonCommande[K]): void {
    this.brouillon.update((b) => ({ ...b, [cle]: valeur }));
    if (this.erreurs()[cle as keyof Erreurs]) this.erreurs.update((e) => ({ ...e, [cle]: undefined }));
  }

  protected choisirZone(valeur: string): void {
    this.maj('zoneId', valeur ? Number(valeur) : null);
    if (valeur) this.erreurs.update((e) => ({ ...e, zone: undefined }));
  }

  /** Revenir sur une étape déjà validée pour la modifier. */
  protected ouvrir(index: number): void {
    if (index <= this.jusquA() && !this.envoi()) {
      this.etape.set(index);
      this.erreurs.set({});
    }
  }

  /** Bouton principal : valide l'étape ouverte puis passe à la suivante ; à la dernière, envoie la commande. */
  protected continuer(): void {
    if (this.envoi()) return;
    const b = this.brouillon();
    const etape = this.etape();
    const erreurs =
      etape === 0 ? erreursCoordonnees(b)
      : etape === 1 ? erreursLivraison(b)
      : etape === 2 ? erreursPaiement(b, this.modesDisponibles())
      : {};
    this.erreurs.set(erreurs);
    if (aDesErreurs(erreurs)) {
      this.document.getElementById('erreur-etape')?.focus();
      return;
    }
    if (etape < 3) {
      this.suivreEtape(etape);
      this.etape.set(etape + 1);
      this.jusquA.update((j) => Math.max(j, etape + 1));
      return;
    }
    void this.envoyer();
  }

  private suivreEtape(etape: number): void {
    const p = this.panier.panier();
    if (!p) return;
    const lignes = articlesSuivis(p);
    const valeur = Math.max(0, p.sousTotal - p.remise);
    if (etape === 1) this.suivi.choisirLivraison(lignes, valeur, this.brouillon().modeLivraison, this.frais() ?? 0);
    if (etape === 2 && this.brouillon().modePaiement) {
      this.suivi.choisirPaiement(lignes, valeur, this.brouillon().modePaiement!);
    }
  }

  private async envoyer(): Promise<void> {
    const b = this.brouillon();
    this.echec.set(null);
    this.envoi.set(true);
    const domicile = b.modeLivraison === 'DOMICILE';
    const vide = (v: string) => (v.trim() ? v.trim() : null);
    const demande: CreationCommande = {
      nomComplet: b.nom.trim(),
      telephone: normaliserTelephone(b.telephone) ?? b.telephone,
      email: vide(b.email),
      modeLivraison: b.modeLivraison,
      zoneId: domicile ? b.zoneId : null,
      ville: domicile ? vide(b.ville) : null,
      quartier: domicile ? vide(b.quartier) : null,
      adresse: domicile ? vide(b.adresse) : null,
      pointDeRepere: domicile ? vide(b.pointDeRepere) : null,
      telephoneLivraison: domicile ? vide(b.telephoneLivraison) : null,
      modePaiement: b.modePaiement!,
      totalAttendu: this.total(),
      attribution: this.attribution.lire(),
    };
    try {
      const commande = await this.commandes.commander(demande);
      effacerSession(CLE_BROUILLON);
      this.restaure = false;
      if (commande.urlPaiement) {
        // Le lien de suivi est gardé le temps du paiement (cet onglet seulement) : au retour, la page de retour,
        // dont l'adresse ne contient pas ce jeton, peut proposer d'ouvrir le suivi
        ecrireSession('suivi_dernier', JSON.stringify({ numero: commande.numero, jeton: commande.jetonSuivi }));
        // Page du prestataire : le bouton reste désactivé jusqu'au départ de la page
        this.document.location.assign(commande.urlPaiement);
        return;
      }
      void this.panier.charger();
      await this.router.navigate(['/suivi', commande.jetonSuivi], {
        queryParams: { nouvelle: 1, ...(commande.paiementAReprendre ? { retour: 'reprise' } : {}) },
        replaceUrl: true,
      });
    } catch (e) {
      this.echec.set(e as ErreurCommande);
      this.envoi.set(false);
      this.document.getElementById('erreur-commande')?.focus();
    }
  }

  protected revoirLePanier(): void {
    void this.panier.charger();
    void this.router.navigate(['/panier']);
  }
}
