import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  afterRenderEffect,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { Banniere } from '../../../noyau/api/modeles';
import { srcsetCloudinary } from '../../../noyau/images/chargeur-cloudinary';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { Icone } from '../../../partage/composants/icone/icone';

/** Durée d'affichage d'une diapositive en défilement automatique. */
export const DUREE_DIAPO_MS = 6000;
/** Déplacement horizontal minimal d'un balayage au doigt. */
const SEUIL_BALAYAGE_PX = 50;
const LARGEURS_ORDINATEUR = [768, 1280, 1920] as const;
const LARGEURS_MOBILE = [400, 640, 800] as const;

/**
 * Carrousel de l'accueil (bannières `ACCUEIL_CARROUSEL`).
 * Accessibilité (WCAG 2.2.2) : bouton pause/lecture visible, arrêt au survol et quand le focus est dans le
 * carrousel, aucun défilement automatique si `prefers-reduced-motion`, points et flèches au clavier.
 * Poids : seule la première image est chargée d'emblée (`fetchpriority=high`) ; les suivantes le sont
 * juste avant leur affichage. `<picture>` : l'image portrait sur téléphone, le bandeau sur ordinateur.
 */
@Component({
  selector: 'bi-carrousel',
  imports: [Icone, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(pointerenter)': 'surEntreePointeur($event)',
    '(pointerleave)': 'survol.set(false)',
    '(focusin)': 'focusDedans.set(true)',
    '(focusout)': 'surSortieFocus($event)',
  },
  templateUrl: './carrousel.html',
  styleUrl: './carrousel.scss',
})
export class Carrousel {
  private readonly suivi = inject(SuiviService);

  readonly bannieres = input.required<Banniere[]>();

  protected readonly actif = signal(0);
  /** Pause demandée par le bouton (ou imposée par `prefers-reduced-motion`). */
  protected readonly enPause = signal(false);
  protected readonly survol = signal(false);
  protected readonly focusDedans = signal(false);
  /** Diapositives dont l'image peut être chargée : la courante et la suivante. */
  protected readonly chargees = signal<ReadonlySet<number>>(new Set([0]));

  protected readonly nombre = computed(() => this.bannieres().length);
  protected readonly defilementActif = computed(
    () => this.nombre() > 1 && !this.enPause() && !this.survol() && !this.focusDedans(),
  );

  protected readonly sources = computed(() =>
    this.bannieres().map((b) => ({
      ordinateur: srcsetCloudinary(b.imageOrdinateurUrl, LARGEURS_ORDINATEUR),
      mobile: b.imageMobileUrl ? (srcsetCloudinary(b.imageMobileUrl, LARGEURS_MOBILE) ?? b.imageMobileUrl) : null,
    })),
  );

  private readonly promotionsVues = new Set<number>();
  private departBalayage: number | null = null;

  constructor() {
    const destruction = inject(DestroyRef);
    afterNextRender(() => {
      if (globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
        this.enPause.set(true);
      }
      const minuterie = setInterval(() => {
        if (this.defilementActif() && !document.hidden) {
          this.aller(this.actif() + 1);
        }
      }, DUREE_DIAPO_MS);
      destruction.onDestroy(() => clearInterval(minuterie));
    });

    // view_promotion : une fois par bannière réellement affichée (navigateur uniquement)
    afterRenderEffect(() => {
      const banniere = this.bannieres()[this.actif()];
      if (banniere && !this.promotionsVues.has(banniere.id)) {
        this.promotionsVues.add(banniere.id);
        this.suivi.voirPromotion(banniere.id, banniere.titre);
      }
    });
  }

  protected aller(index: number): void {
    const n = this.nombre();
    if (!n) {
      return;
    }
    const cible = ((index % n) + n) % n;
    this.actif.set(cible);
    this.chargees.update((s) => new Set([...s, cible, (cible + 1) % n]));
  }

  protected basculerPause(): void {
    this.enPause.update((p) => !p);
  }

  protected choisir(banniere: Banniere): void {
    this.suivi.choisirPromotion(banniere.id, banniere.titre);
  }

  protected estInterne(lien: string): boolean {
    return lien.startsWith('/') && !lien.startsWith('//');
  }

  protected surTouche(evenement: KeyboardEvent): void {
    if (evenement.key === 'ArrowRight') {
      evenement.preventDefault();
      this.aller(this.actif() + 1);
    } else if (evenement.key === 'ArrowLeft') {
      evenement.preventDefault();
      this.aller(this.actif() - 1);
    }
  }

  protected surEntreePointeur(evenement: PointerEvent): void {
    if (evenement.pointerType === 'mouse') {
      this.survol.set(true);
    }
  }

  protected surSortieFocus(evenement: FocusEvent): void {
    const cible = evenement.relatedTarget as Node | null;
    if (!cible || !(evenement.currentTarget as HTMLElement).contains(cible)) {
      this.focusDedans.set(false);
    }
  }

  protected debutBalayage(evenement: PointerEvent): void {
    this.departBalayage = evenement.pointerType === 'mouse' ? null : evenement.clientX;
  }

  protected finBalayage(evenement: PointerEvent): void {
    if (this.departBalayage === null) {
      return;
    }
    const ecart = evenement.clientX - this.departBalayage;
    this.departBalayage = null;
    if (Math.abs(ecart) >= SEUIL_BALAYAGE_PX) {
      this.aller(this.actif() + (ecart < 0 ? 1 : -1));
    }
  }
}
