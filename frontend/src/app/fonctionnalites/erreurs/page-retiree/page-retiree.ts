import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Page, ProduitResume } from '../../../noyau/api/modeles';
import { chargementMemorise } from '../../../noyau/donnees/chargement-memorise';
import { RedirectionReponse } from '../../../noyau/seo/reponse-definitive';
import { SeoService } from '../../../noyau/seo/seo.service';
import { RangeeProduits } from '../../../partage/composants/rangee-produits/rangee-produits';

const PRODUITS_PROPOSES = 8;

/**
 * Page 410 (retrait définitif, ADR 0007) : le statut est posé par {@link reponseDefinitive} pendant le rendu
 * serveur. Propose des produits du même rayon et un lien vers ce rayon, pour ne pas perdre le visiteur arrivé
 * par un ancien lien ou une ancienne publicité.
 */
@Component({
  selector: 'bi-page-retiree',
  imports: [RangeeProduits, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="conteneur retiree">
      <p class="code">Plus disponible</p>
      <h1>
        @if (type() === 'produit') {
          Ce produit n'est plus vendu
        } @else {
          Cette catégorie n'existe plus
        }
      </h1>
      <p class="nom">« {{ retrait().nom }} » a été retiré définitivement de notre catalogue.</p>
      <p class="liens">
        @if (retrait().categorie; as rayon) {
          <a class="lien" [routerLink]="['/categorie', rayon.slug]">Voir le rayon {{ rayon.nom }}</a>
        }
        <a class="lien lien--secondaire" routerLink="/">Revenir à l'accueil</a>
      </p>
    </section>
    @if (proposes().length) {
      <div class="conteneur">
        <bi-rangee-produits titre="Produits similaires disponibles" [produits]="proposes()" [liste]="liste()" />
      </div>
    }
  `,
  styles: `
    .retiree {
      padding-block: var(--espace-12) var(--espace-8);
      max-width: 48rem;
    }
    .code {
      font-family: var(--police-mono);
      font-size: var(--taille-sm);
      color: var(--couleur-accent);
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }
    h1 {
      margin-block: var(--espace-2) var(--espace-4);
    }
    .nom {
      color: var(--couleur-texte-secondaire);
      overflow-wrap: anywhere;
    }
    .liens {
      display: flex;
      flex-wrap: wrap;
      gap: var(--espace-3) var(--espace-6);
      margin-block-start: var(--espace-6);
    }
    .lien {
      display: inline-flex;
      align-items: center;
      min-height: var(--cible-tactile);
      font-weight: var(--graisse-semi-gras);
    }
    .lien--secondaire {
      color: var(--couleur-texte-secondaire);
    }
  `,
})
export class PageRetiree {
  readonly retrait = input.required<NonNullable<RedirectionReponse['retrait']>>();
  readonly type = input<'produit' | 'categorie'>('produit');

  private readonly produits = chargementMemorise<Page<ProduitResume>>(() => {
    const rayon = this.retrait().categorie;
    return rayon ? `/api/produits?categorie=${rayon.slug}&taille=${PRODUITS_PROPOSES}` : null;
  });
  protected readonly proposes = computed(() => this.produits.valeur()?.elements ?? []);
  protected readonly liste = computed(() => ({
    id: `retire-${this.retrait().categorie?.slug ?? 'accueil'}`,
    nom: 'Produits similaires (produit retiré)',
  }));

  constructor() {
    const seo = inject(SeoService);
    // Pas de canonique ni d'indexation : la page n'existe plus
    effect(() =>
      seo.definirPage({ titre: this.type() === 'produit' ? 'Produit retiré' : 'Catégorie retirée', indexable: false }),
    );
  }
}
