import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ProduitResume } from '../../../noyau/api/modeles';
import { ListeSuivie, SuiviService } from '../../../noyau/suivi/suivi.service';
import { Badge } from '../badge/badge';
import { Prix } from '../prix/prix';

/** Au plus 4 lignes de caractéristiques (ADR 0004 §6) : la carte garde une hauteur prévisible. */
const CARACTERISTIQUES_MAX = 4;

/**
 * Carte produit des listes (accueil, catégorie, recherche, marque).
 * - Photo carrée sur fond blanc, `contain` : jamais rognée, quelle que soit la photo du fournisseur.
 * - Un seul signal rouge : le « −x % » du prix (aucun badge « Promo » en plus).
 * - Le lien ouvre la variante présentée (`?variante=`), celle dont la carte montre le prix.
 * - Toute la carte est cliquable par le lien du titre (zone étendue), sans lien imbriqué.
 */
@Component({
  selector: 'bi-carte-produit',
  imports: [Badge, NgOptimizedImage, Prix, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './carte-produit.html',
  styleUrl: './carte-produit.scss',
})
export class CarteProduit {
  private readonly suivi = inject(SuiviService);

  readonly produit = input.required<ProduitResume>();
  /** Liste d'origine et position : `select_item` du suivi. */
  readonly liste = input.required<ListeSuivie>();
  readonly position = input(0);
  /** Les premières cartes visibles au chargement ne sont pas différées. */
  readonly prioritaire = input(false);

  protected readonly caracteristiques = computed(() =>
    (this.produit().caracteristiquesCles ?? []).slice(0, CARACTERISTIQUES_MAX),
  );

  protected choisir(): void {
    this.suivi.choisirProduit(this.liste(), this.produit(), this.position());
  }
}
