import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Tracés des icônes Lucide (https://lucide.dev, licence ISC), recopiés ici pour n'embarquer que
 * celles qui servent (aucune dépendance npm, aucun JS de bibliothèque). Grille 24 × 24, trait 2.
 * Chaque entrée : liste d'éléments SVG simples.
 */
type Trace =
  | { d: string }
  | { cx: number; cy: number; r: number }
  | { x: number; y: number; width: number; height: number; rx: number };

const ICONES = {
  recherche: [{ cx: 11, cy: 11, r: 8 }, { d: 'm21 21-4.3-4.3' }],
  menu: [{ d: 'M4 6h16' }, { d: 'M4 12h16' }, { d: 'M4 18h16' }],
  fermer: [{ d: 'M18 6 6 18' }, { d: 'm6 6 12 12' }],
  panier: [
    { cx: 8, cy: 21, r: 1 },
    { cx: 19, cy: 21, r: 1 },
    { d: 'M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12' },
  ],
  compte: [{ d: 'M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2' }, { cx: 12, cy: 7, r: 4 }],
  telephone: [
    {
      d: 'M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z',
    },
  ],
  message: [{ d: 'M7.9 20A9 9 0 1 0 4 16.1L2 22Z' }],
  'chevron-bas': [{ d: 'm6 9 6 6 6-6' }],
  // Lucide « sliders-horizontal »
  filtres: [
    { d: 'M21 4h-7' },
    { d: 'M10 4H3' },
    { d: 'M21 12h-9' },
    { d: 'M8 12H3' },
    { d: 'M21 20h-5' },
    { d: 'M12 20H3' },
    { d: 'M14 2v4' },
    { d: 'M8 10v4' },
    { d: 'M16 18v4' },
  ],
  'chevron-droite': [{ d: 'm9 18 6-6-6-6' }],
  'chevron-gauche': [{ d: 'm15 18-6-6 6-6' }],
  pause: [
    { x: 14, y: 4, width: 4, height: 16, rx: 1 },
    { x: 6, y: 4, width: 4, height: 16, rx: 1 },
  ],
  lecture: [{ d: 'M6 3l14 9-14 9V3z' }],
  livraison: [
    { d: 'M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2' },
    { d: 'M15 18H9' },
    { d: 'M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14' },
    { cx: 17, cy: 18, r: 2 },
    { cx: 7, cy: 18, r: 2 },
  ],
  garantie: [
    {
      d: 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
    },
    { d: 'm9 12 2 2 4-4' },
  ],
  paiement: [{ x: 5, y: 2, width: 14, height: 20, rx: 2 }, { d: 'M12 18h.01' }],
  sav: [
    {
      d: 'M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z',
    },
  ],
  retour: [{ d: 'M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8' }, { d: 'M3 3v5h5' }],
  boutique: [
    { d: 'M3 9l1.5-5h15L21 9' },
    { d: 'M3 9h18v2a3 3 0 0 1-6 0 3 3 0 0 1-6 0 3 3 0 0 1-6 0V9z' },
    { d: 'M5 13v8h14v-8' },
    { d: 'M10 21v-5h4v5' },
  ],
  email: [{ x: 2, y: 4, width: 20, height: 16, rx: 2 }, { d: 'm22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7' }],
  lieu: [
    { d: 'M20 10c0 4.99-5.54 10.19-7.4 11.8a1 1 0 0 1-1.2 0C9.54 20.19 4 14.99 4 10a8 8 0 0 1 16 0' },
    { cx: 12, cy: 10, r: 3 },
  ],
  horloge: [{ cx: 12, cy: 12, r: 10 }, { d: 'M12 6v6l4 2' }],
} satisfies Record<string, Trace[]>;

export type NomIcone = keyof typeof ICONES;

/**
 * Icône décorative (`aria-hidden`) : le texte ou l'`aria-label` du bouton parent porte le sens.
 * Taille : `--taille-icone` par défaut, ou la valeur donnée (« 1rem », « var(--espace-6) »…).
 */
@Component({
  selector: 'bi-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', '[style.--taille]': 'taille()' },
  template: `
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      focusable="false"
    >
      @for (trace of traces(); track $index) {
        @if ('d' in trace) {
          <path [attr.d]="trace.d" />
        } @else if ('r' in trace) {
          <circle [attr.cx]="trace.cx" [attr.cy]="trace.cy" [attr.r]="trace.r" />
        } @else {
          <rect
            [attr.x]="trace.x"
            [attr.y]="trace.y"
            [attr.width]="trace.width"
            [attr.height]="trace.height"
            [attr.rx]="trace.rx"
          />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      width: var(--taille, var(--taille-icone));
      height: var(--taille, var(--taille-icone));
    }

    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class Icone {
  readonly nom = input.required<NomIcone>();
  readonly taille = input<string | null>(null);

  protected readonly traces = computed<Trace[]>(() => ICONES[this.nom()]);
}
