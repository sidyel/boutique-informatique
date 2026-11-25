import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { CatalogueService } from '../../noyau/donnees/catalogue.service';
import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Icone } from '../../partage/composants/icone/icone';
import { Logo } from '../logo/logo';

/**
 * Tiroir de navigation (mobile et tablette) : mêmes liens que le méga-menu, plus les contacts directs.
 * Fenêtre modale accessible : focus placé sur « Fermer » à l'ouverture et piégé dans le tiroir, Échap
 * ferme, le focus revient au bouton qui l'a ouvert, la page derrière ne défile pas.
 */
@Component({
  selector: 'bi-tiroir-navigation',
  imports: [Icone, Logo, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tiroir-navigation.html',
  styleUrl: './tiroir-navigation.scss',
})
export class TiroirNavigation {
  private readonly document = inject(DOCUMENT);
  private readonly suivi = inject(SuiviService);
  protected readonly catalogue = inject(CatalogueService);
  protected readonly site = inject(ParametresSiteService);

  readonly ouvert = input(false);
  readonly fermer = output<void>();

  /** Catégorie dont les sous-catégories sont dépliées. */
  protected readonly depliee = signal<string | null>(null);

  private readonly panneau = viewChild<ElementRef<HTMLElement>>('panneau');

  constructor() {
    afterRenderEffect(() => {
      const ouvert = this.ouvert();
      this.document.body.style.overflow = ouvert ? 'hidden' : '';
      if (ouvert) {
        this.panneau()?.nativeElement.querySelector<HTMLElement>('.tiroir__fermer')?.focus();
      }
    });
  }

  protected basculer(slug: string): void {
    this.depliee.update((s) => (s === slug ? null : slug));
  }

  protected contactWhatsApp(): void {
    this.suivi.contactWhatsApp('entete');
  }

  protected contactTelephone(): void {
    this.suivi.contactTelephone('entete');
  }

  /** Piège à focus : Tab et Maj+Tab restent dans le tiroir. */
  protected surTouche(evenement: KeyboardEvent): void {
    if (evenement.key === 'Escape') {
      this.fermer.emit();
      return;
    }
    if (evenement.key !== 'Tab') {
      return;
    }
    const focusables = Array.from(
      this.panneau()?.nativeElement.querySelectorAll<HTMLElement>('a[href], button:not([disabled])') ?? [],
    );
    if (!focusables.length) {
      return;
    }
    const premier = focusables[0];
    const dernier = focusables[focusables.length - 1];
    const actif = this.document.activeElement;
    if (evenement.shiftKey && actif === premier) {
      evenement.preventDefault();
      dernier.focus();
    } else if (!evenement.shiftKey && actif === dernier) {
      evenement.preventDefault();
      premier.focus();
    }
  }
}
