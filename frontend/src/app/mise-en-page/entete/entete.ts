import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { PanierService } from '../../noyau/panier/panier.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Icone } from '../../partage/composants/icone/icone';
import { Logo } from '../logo/logo';
import { MegaMenu } from '../mega-menu/mega-menu';
import { BarreRecherche } from '../recherche/barre-recherche';
import { TiroirNavigation } from '../tiroir-navigation/tiroir-navigation';

/**
 * En-tête : barre supérieure graphite (promesse de livraison, téléphone, WhatsApp), logo, recherche,
 * compte, panier ; méga-menu sur ordinateur, tiroir sur mobile et tablette.
 * Le message de la barre supérieure vient des paramètres du site (jamais écrit en dur).
 */
@Component({
  selector: 'bi-entete',
  imports: [BarreRecherche, Icone, Logo, MegaMenu, RouterLink, TiroirNavigation],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './entete.html',
  styleUrl: './entete.scss',
})
export class Entete {
  protected readonly site = inject(ParametresSiteService);
  protected readonly panier = inject(PanierService);
  private readonly suivi = inject(SuiviService);

  protected readonly tiroirOuvert = signal(false);
  private readonly boutonMenu = viewChild<ElementRef<HTMLButtonElement>>('boutonMenu');

  protected fermerTiroir(): void {
    this.tiroirOuvert.set(false);
    this.boutonMenu()?.nativeElement.focus();
  }

  /**
   * Icône du panier : ouvre le panier latéral s'il contient des articles. Le lien vers /panier reste pour un
   * panier vide, un clic avec Ctrl ou Maj (nouvel onglet) et un clic du milieu.
   */
  protected ouvrirPanier(evenement: MouseEvent): void {
    if (evenement.button !== 0 || evenement.ctrlKey || evenement.metaKey || evenement.shiftKey) return;
    if (this.panier.nombreArticles() === 0) return;
    evenement.preventDefault();
    this.panier.ouvrirTiroir(true);
  }

  protected contactWhatsApp(): void {
    this.suivi.contactWhatsApp('entete');
  }

  protected contactTelephone(): void {
    this.suivi.contactTelephone('entete');
  }
}
