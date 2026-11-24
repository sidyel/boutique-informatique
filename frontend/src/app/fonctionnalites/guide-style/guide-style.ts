import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { SeoService } from '../../noyau/seo/seo.service';
import { Badge, TypeBadge } from '../../partage/composants/badge/badge';
import { Bouton, TailleBouton, VarianteBouton } from '../../partage/composants/bouton/bouton';
import { Champ } from '../../partage/composants/champ/champ';
import { Prix } from '../../partage/composants/prix/prix';
import { Squelette } from '../../partage/composants/squelette/squelette';

interface Nuance {
  jeton: string;
  valeur: string;
  /** Contraste WCAG mesuré, avec le fond indiqué. */
  contraste?: string;
}

interface GroupeNuances {
  titre: string;
  nuances: Nuance[];
}

/**
 * Vitrine du système de design : jetons et composants de base.
 * Page de travail, non indexée.
 */
@Component({
  selector: 'bi-guide-style',
  imports: [Badge, Bouton, Champ, Prix, Squelette, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guide-style.html',
  styleUrl: './guide-style.scss',
})
export class GuideStyle {
  protected readonly groupesNuances: GroupeNuances[] = [
    {
      titre: 'Neutres (graphite)',
      nuances: [
        { jeton: '--couleur-fond', valeur: '#F6F7F8' },
        { jeton: '--couleur-surface', valeur: '#FFFFFF' },
        { jeton: '--couleur-surface-alt', valeur: '#EEF0F2' },
        { jeton: '--couleur-bordure', valeur: '#E4E7EB' },
        { jeton: '--couleur-bordure-champ', valeur: '#6B7480', contraste: '4,74:1 sur blanc' },
        { jeton: '--couleur-texte-secondaire', valeur: '#5B6573', contraste: '5,51:1 sur fond' },
        { jeton: '--couleur-texte', valeur: '#1C2128', contraste: '15,09:1 sur fond' },
        { jeton: '--couleur-sombre', valeur: '#0E1116', contraste: '18,91:1 avec blanc' },
      ],
    },
    {
      titre: 'Accent (orange signal)',
      nuances: [
        { jeton: '--couleur-accent-leger', valeur: '#FFF1E8' },
        { jeton: '--couleur-accent-decor', valeur: '#EA580C', contraste: 'décor uniquement' },
        { jeton: '--couleur-accent', valeur: '#C2410C', contraste: '5,18:1 avec blanc' },
        { jeton: '--couleur-accent-survol', valeur: '#9A3412', contraste: '7,31:1 avec blanc' },
      ],
    },
    {
      titre: 'États',
      nuances: [
        { jeton: '--couleur-succes', valeur: '#15803D', contraste: '4,60:1 sur son fond' },
        { jeton: '--couleur-alerte', valeur: '#B45309', contraste: '4,67:1 sur son fond' },
        { jeton: '--couleur-promo', valeur: '#DC2626', contraste: '4,83:1 avec blanc' },
        { jeton: '--couleur-promo-fonce', valeur: '#B91C1C', contraste: '5,74:1 sur son fond' },
        { jeton: '--couleur-info', valeur: '#1D4ED8', contraste: '6,03:1 sur son fond' },
      ],
    },
  ];

  protected readonly tailles = [
    { jeton: '--taille-4xl', usage: 'Titre de page (h1)' },
    { jeton: '--taille-3xl', usage: 'Titre de section (h2)' },
    { jeton: '--taille-2xl', usage: 'Sous-section (h3)' },
    { jeton: '--taille-xl', usage: 'Prix sur une carte' },
    { jeton: '--taille-lg', usage: 'Chapeau' },
    { jeton: '--taille-base', usage: 'Texte courant (16 px)' },
    { jeton: '--taille-sm', usage: 'Caractéristiques, méta' },
    { jeton: '--taille-xs', usage: 'Badges, mentions' },
  ];

  protected readonly espaces = [1, 2, 3, 4, 5, 6, 8, 10, 12, 16];
  protected readonly rayons = ['--rayon-1', '--rayon-2', '--rayon-3'];
  protected readonly ombres = ['--ombre-1', '--ombre-2'];

  protected readonly variantesBouton: VarianteBouton[] = [
    'principal',
    'secondaire',
    'contour',
    'fantome',
    'danger',
  ];
  protected readonly taillesBouton: TailleBouton[] = ['sm', 'md', 'lg'];

  protected readonly badges: { type: TypeBadge; texte: string }[] = [
    { type: 'neuf', texte: 'Neuf' },
    { type: 'reconditionne', texte: 'Reconditionné' },
    { type: 'occasion', texte: 'Occasion' },
    { type: 'promo', texte: 'Promo' },
    { type: 'stock', texte: 'En stock' },
    { type: 'stock-faible', texte: 'Plus que 2' },
    { type: 'rupture', texte: 'Rupture' },
    { type: 'info', texte: 'Livraison 24 h' },
  ];

  protected readonly caracteristiques = [
    ['Processeur', 'AMD Ryzen 7 7735HS · 8 cœurs'],
    ['Mémoire', '16 Go DDR5-4800'],
    ['Stockage', 'SSD NVMe 512 Go'],
    ['Graphique', 'RTX 4050 · 6 Go · 140 W'],
    ['Écran', '15,6" FHD IPS 144 Hz'],
  ];

  protected readonly chargement = signal(false);
  protected readonly telephone = new FormControl('77 123 45 67', { nonNullable: true });
  protected readonly email = new FormControl('awa.ndiaye@', { nonNullable: true });
  protected readonly desactive = new FormControl({ value: 'CMD-2026-000123', disabled: true });

  constructor() {
    inject(SeoService).definirPage({ titre: 'Guide de style', indexable: false });
  }

  protected basculerChargement(): void {
    this.chargement.set(true);
    setTimeout(() => this.chargement.set(false), 1500);
  }
}
