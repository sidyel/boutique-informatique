import { DatePipe } from '@angular/common';
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
import { RouterLink } from '@angular/router';

import { Banniere } from '../../../noyau/api/modeles';
import { srcsetCloudinary } from '../../../noyau/images/chargeur-cloudinary';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { Icone } from '../../../partage/composants/icone/icone';

const MINUTE_MS = 60_000;

/** « 2 j 05 h 12 min » ; `null` quand l'échéance est passée. */
export function tempsRestant(finMs: number, maintenantMs: number): string | null {
  const reste = finMs - maintenantMs;
  if (reste <= 0) {
    return null;
  }
  const minutes = Math.ceil(reste / MINUTE_MS);
  const jours = Math.floor(minutes / 1440);
  const heures = Math.floor((minutes % 1440) / 60);
  const min = minutes % 60;
  const deux = (n: number) => String(n).padStart(2, '0');
  return jours > 0 ? `${jours} j ${deux(heures)} h ${deux(min)} min` : `${deux(heures)} h ${deux(min)} min`;
}

/**
 * Bannière promotionnelle de l'accueil (`ACCUEIL_PROMO`).
 * Compte à rebours **uniquement** si l'opération a une date de fin réelle (`dateFin`) : jamais d'urgence
 * inventée. Rendu serveur : la date de fin, fixe ; navigateur : le temps restant, à la minute près.
 */
@Component({
  selector: 'bi-banniere-promo',
  imports: [DatePipe, Icone, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './banniere-promo.html',
  styleUrl: './banniere-promo.scss',
})
export class BannierePromo {
  private readonly suivi = inject(SuiviService);

  readonly banniere = input.required<Banniere>();

  /** `null` pendant le rendu serveur et l'hydratation : aucun écart entre les deux rendus. */
  private readonly maintenant = signal<number | null>(null);

  protected readonly finMs = computed(() => {
    const fin = this.banniere().dateFin;
    return fin ? Date.parse(fin) : null;
  });

  protected readonly compteARebours = computed(() => {
    const fin = this.finMs();
    const maintenant = this.maintenant();
    return fin === null || maintenant === null ? null : tempsRestant(fin, maintenant);
  });

  protected readonly srcsetOrdinateur = computed(() =>
    srcsetCloudinary(this.banniere().imageOrdinateurUrl, [768, 1280, 1920]),
  );
  protected readonly srcsetMobile = computed(() => {
    const url = this.banniere().imageMobileUrl;
    return url ? (srcsetCloudinary(url, [400, 640, 800]) ?? url) : null;
  });

  constructor() {
    const destruction = inject(DestroyRef);
    afterNextRender(() => {
      this.suivi.voirPromotion(this.banniere().id, this.banniere().titre);
      if (this.finMs() === null) {
        return;
      }
      this.maintenant.set(Date.now());
      const minuterie = setInterval(() => this.maintenant.set(Date.now()), MINUTE_MS / 2);
      destruction.onDestroy(() => clearInterval(minuterie));
    });
  }

  protected choisir(): void {
    this.suivi.choisirPromotion(this.banniere().id, this.banniere().titre);
  }
}
