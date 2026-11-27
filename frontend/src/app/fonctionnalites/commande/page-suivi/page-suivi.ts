import { DOCUMENT, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { SuiviCommande } from '../../../noyau/api/modeles';
import { CommandeService, ErreurCommande } from '../../../noyau/commande/commande.service';
import { ParametresSiteService, lienWhatsApp } from '../../../noyau/donnees/parametres-site.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Icone } from '../../../partage/composants/icone/icone';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';
import { EtapeSuivi, LIBELLES_STATUT, parcours } from './suivi-statuts';

/** Après le retour de la page de paiement : on relit l'état toutes les 3 s, pendant 90 s au plus. */
const INTERVALLE_VERIFICATION = 3_000;
const VERIFICATIONS_MAX = 30;

/**
 * Confirmation et suivi d'une commande par son lien (`/suivi/:jeton`), sans compte. noindex, `no-referrer` : le
 * jeton du lien ne part dans aucun en-tête Referer. Le contenu (personnel) n'est chargé que dans le navigateur.
 *
 * **La page n'affiche que l'état fixé par le serveur.** Le retour de la page de paiement ne change rien à
 * l'état : la page relit simplement la commande en attendant la confirmation du prestataire.
 */
@Component({
  selector: 'bi-page-suivi',
  imports: [Bouton, DatePipe, Icone, PrixFcfaPipe, RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-suivi.html',
  styleUrl: './page-suivi.scss',
})
export class PageSuivi {
  private readonly commandes = inject(CommandeService);
  private readonly suivi = inject(SuiviService);
  private readonly site = inject(ParametresSiteService);
  private readonly document = inject(DOCUMENT);
  private readonly meta = inject(Meta);
  private readonly destruction = inject(DestroyRef);

  /** Jeton du lien (paramètre de l'URL). */
  readonly jeton = input.required<string>();
  /** Premier affichage juste après la commande (`?nouvelle=1`). */
  readonly nouvelle = input<string>();
  /** Retour de la page de paiement : `paiement` (payé ou non), `annule`, ou `reprise` (paiement non démarré). */
  readonly retour = input<string>();

  protected readonly commande = signal<SuiviCommande | null>(null);
  protected readonly introuvable = signal(false);
  protected readonly erreurReseau = signal(false);
  protected readonly verification = signal(false);
  protected readonly reprise = signal(false);
  protected readonly erreurReprise = signal<string | null>(null);
  protected readonly lienCopie = signal(false);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly etapes = computed<EtapeSuivi[]>(() => {
    const c = this.commande();
    return c ? parcours(c.statut, c.modePaiement, c.modeLivraison) : [];
  });
  protected readonly aLaLivraison = computed(() => this.commande()?.modePaiement === 'A_LA_LIVRAISON');
  protected readonly retrait = computed(() => this.commande()?.modeLivraison === 'RETRAIT_MAGASIN');
  /** Facture et avoirs : liens directs (une touche ouvre ou télécharge le PDF), sans script. */
  protected readonly urlFacture = computed(() => `/api/commandes/suivi/${encodeURIComponent(this.jeton())}/facture`);
  protected urlAvoir(numero: string): string {
    return `/api/commandes/suivi/${encodeURIComponent(this.jeton())}/avoirs/${encodeURIComponent(numero)}`;
  }
  protected readonly lienSuivi = computed(() => `${this.document.location?.origin ?? ''}/suivi/${this.jeton()}`);
  protected readonly lienWhatsApp = computed(() => {
    const c = this.commande();
    const message = c ? `Bonjour, à propos de ma commande ${c.numero}.` : undefined;
    return lienWhatsApp(this.site.parametres()?.whatsapp, message);
  });
  protected readonly echeance = computed(() => this.commande()?.expireLe ?? null);

  private arrete = false;

  constructor() {
    inject(SeoService).definirPage({ titre: 'Suivi de ma commande', indexable: false });
    // Le jeton du lien ne doit partir dans aucun en-tête Referer (liens sortants, WhatsApp, réseaux)
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    this.destruction.onDestroy(() => {
      this.arrete = true;
      this.meta.removeTag("name='referrer'");
    });
    afterNextRender(() => void this.demarrer());
  }

  private async demarrer(): Promise<void> {
    await this.charger();
    if (this.retour() !== 'paiement') return;
    // Retour de la page de paiement : l'état vient du prestataire, pas du navigateur. On attend sa confirmation.
    this.verification.set(this.commande()?.statut === 'EN_ATTENTE_PAIEMENT');
    for (let i = 0; i < VERIFICATIONS_MAX && !this.arrete && this.commande()?.statut === 'EN_ATTENTE_PAIEMENT'; i++) {
      await new Promise((resolve) => setTimeout(resolve, INTERVALLE_VERIFICATION));
      await this.charger();
    }
    this.verification.set(false);
  }

  private async charger(): Promise<void> {
    try {
      const commande = await this.commandes.suivi(this.jeton());
      this.commande.set(commande);
      this.erreurReseau.set(false);
      if (commande.achatASuivre) void this.suivreAchat(commande);
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) this.introuvable.set(true);
      else this.erreurReseau.set(true);
    }
  }

  /** Événement « achat » : une seule fois par commande, et seulement quand le serveur a confirmé la vente. */
  private async suivreAchat(commande: SuiviCommande): Promise<void> {
    if (!(await this.commandes.revendiquerAchat(this.jeton()))) return;
    this.suivi.achat(
      commande.numero,
      commande.lignes.map((l) => ({
        sku: l.sku,
        nom: l.nomProduit,
        marque: '',
        categorie: '',
        variante: l.libelleVariante,
        prix: l.prixUnitaire,
        quantite: l.quantite,
      })),
      commande.total,
      `achat-${commande.numero}`,
    );
  }

  protected async reprendrePaiement(): Promise<void> {
    if (this.reprise()) return;
    this.reprise.set(true);
    this.erreurReprise.set(null);
    try {
      this.document.location.assign(await this.commandes.reprendrePaiement(this.jeton()));
    } catch (e) {
      this.erreurReprise.set((e as ErreurCommande).message);
      this.reprise.set(false);
    }
  }

  protected async copierLien(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.lienSuivi());
      this.lienCopie.set(true);
      setTimeout(() => this.lienCopie.set(false), 3_000);
    } catch {
      // Presse-papiers refusé : le lien reste affiché, sélectionnable
    }
  }

  protected recharger(): void {
    this.erreurReseau.set(false);
    void this.charger();
  }
}
