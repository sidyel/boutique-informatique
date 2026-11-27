import { NgOptimizedImage } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Accueil as AccueilReponse, Banniere } from '../../noyau/api/modeles';
import { CatalogueService } from '../../noyau/donnees/catalogue.service';
import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { siteWeb } from '../../noyau/seo/donnees-structurees';
import { SeoService } from '../../noyau/seo/seo.service';
import { Icone } from '../../partage/composants/icone/icone';
import { ICONES_CONFIANCE } from '../../partage/composants/icone/icones-confiance';
import { RangeeProduits } from '../../partage/composants/rangee-produits/rangee-produits';
import { Squelette } from '../../partage/composants/squelette/squelette';
import { BannierePromo } from './banniere-promo/banniere-promo';
import { Carrousel } from './carrousel/carrousel';

const MARQUES_AFFICHEES = 12;

/**
 * Accueil : carrousel, promesses, rayons, sélections de produits (titres choisis par le serveur selon les
 * données : jamais « Meilleures ventes » sans ventes réelles), bannière promotionnelle, marques.
 * Chaque bloc a son squelette pendant le chargement et disparaît s'il est vide ou en erreur.
 * Les réponses lues pendant le rendu serveur sont reprises par le navigateur (transfer cache).
 */
@Component({
  selector: 'bi-accueil',
  imports: [BannierePromo, Carrousel, Icone, NgOptimizedImage, RangeeProduits, RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  protected readonly site = inject(ParametresSiteService);
  protected readonly catalogue = inject(CatalogueService);

  protected readonly accueil = httpResource<AccueilReponse>(() => '/api/accueil');
  protected readonly carrousel = httpResource<Banniere[]>(() => ({
    url: '/api/bannieres',
    params: { emplacement: 'ACCUEIL_CARROUSEL' },
  }));
  protected readonly promo = httpResource<Banniere[]>(() => ({
    url: '/api/bannieres',
    params: { emplacement: 'ACCUEIL_PROMO' },
  }));

  protected readonly sections = computed(() =>
    (this.accueil.hasValue() ? this.accueil.value().sections : [])
      .filter((s) => s.produits.length)
      // Liste de suivi calculée une fois : un objet recréé à chaque rendu relancerait view_item_list
      .map((s) => ({ ...s, liste: { id: `accueil-${s.code}`, nom: s.titre } })),
  );
  protected readonly bannieres = computed(() => (this.carrousel.hasValue() ? this.carrousel.value() : []));
  protected readonly bannierePromo = computed(() => (this.promo.hasValue() ? (this.promo.value()[0] ?? null) : null));
  protected readonly marques = computed(() => this.catalogue.marques().slice(0, MARQUES_AFFICHEES));
  protected readonly promesses = computed(() =>
    this.site.elementsConfiance().map((e) => ({
      ...e,
      icone: e.icone ? ICONES_CONFIANCE[e.icone] : null,
    })),
  );

  constructor() {
    inject(SeoService).definirPage({
      titre: 'Matériel informatique au Sénégal',
      canonique: '/',
      donneesStructurees: (url) => [siteWeb(this.site.nomSite(), url)],
      // Aucun moyen de paiement nommé : la description est figée au rendu, avant le chargement des paramètres
      description:
        'PC portables, PC de bureau, composants, écrans, périphériques et réseau au Sénégal. ' +
        'Prix en FCFA, livraison à Dakar.',
    });
  }
}
