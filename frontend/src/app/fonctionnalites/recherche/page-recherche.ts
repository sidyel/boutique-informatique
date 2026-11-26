import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, afterRenderEffect, computed, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { Filtres, Page, ProduitResume, ResultatRecherche } from '../../noyau/api/modeles';
import { chargementMemorise } from '../../noyau/donnees/chargement-memorise';
import { SeoService } from '../../noyau/seo/seo.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { RangeeProduits } from '../../partage/composants/rangee-produits/rangee-produits';
import { Criteres, TRIS_RECHERCHE, lireCriteres, nombreFiltres, versParametres } from '../liste-produits/criteres';
import { DemandeWhatsapp } from '../liste-produits/demande-whatsapp/demande-whatsapp';
import { ListeProduits } from '../liste-produits/liste-produits';
import { parametresRoute } from '../liste-produits/parametres-route';
import { SourceListe, urlAvecParametres } from '../liste-produits/source-liste';

/** Limites de la saisie, identiques à celles de l'API (`MoteurRecherchePostgres`). */
export const LONGUEUR_MIN = 2;
export const LONGUEUR_MAX = 100;

/** Facettes de la recherche (catégories et marques) au format commun des listes. */
export function filtresRecherche(resultat: ResultatRecherche): Filtres {
  return {
    totalProduits: resultat.totalElements,
    prix: null,
    sousCategories: resultat.categories,
    marques: resultat.marques,
    etats: [],
    disponible: null,
    caracteristiques: [],
  };
}

function urlRecherche(q: string, criteres: Criteres, taille?: number): string {
  return urlAvecParametres('/api/recherche', {
    q,
    ...versParametres(criteres, 'pertinence'),
    ...(taille ? { taille: String(taille) } : {}),
  });
}

/**
 * Résultats de recherche `/recherche?q=` : orthographe approchée signalée, filtres par catégorie et marque,
 * tri. Rien trouvé : rayons et produits suggérés, et demande sur WhatsApp avec la recherche préremplie.
 * Page jamais indexée (`noindex`). Événement « search » envoyé ici, une fois par recherche affichée.
 */
@Component({
  selector: 'bi-page-recherche',
  imports: [DemandeWhatsapp, ListeProduits, RangeeProduits, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-recherche.html',
  styleUrl: './page-recherche.scss',
})
export class PageRecherche {
  private readonly http = inject(HttpClient);
  private readonly parametres = parametresRoute();

  protected readonly terme = computed(() => {
    const brut = this.parametres()['q'];
    return String(Array.isArray(brut) ? (brut[0] ?? '') : (brut ?? ''))
      .trim()
      .replace(/\s+/g, ' ');
  });
  protected readonly termeValide = computed(
    () => this.terme().length >= LONGUEUR_MIN && this.terme().length <= LONGUEUR_MAX,
  );
  protected readonly criteres = computed(() => lireCriteres(this.parametres()));

  protected readonly resultat = chargementMemorise<ResultatRecherche>(() =>
    this.termeValide() ? urlRecherche(this.terme(), this.criteres()) : null,
  );

  /** Résultat de la recherche courante (pas celui de la précédente pendant le chargement). */
  protected readonly courant = computed(() => {
    const resultat = this.resultat.valeur();
    return resultat && this.termeValide() ? resultat : undefined;
  });
  protected readonly page = computed<Page<ProduitResume> | undefined>(() => this.courant());
  protected readonly filtres = computed(() => {
    const resultat = this.courant();
    return resultat ? filtresRecherche(resultat) : undefined;
  });
  /** Aucun résultat, même approchant, et aucun filtre à retirer : suggestions de la boutique. */
  protected readonly rienTrouve = computed(
    () => this.courant()?.totalElements === 0 && nombreFiltres(this.criteres()) === 0,
  );
  protected readonly suggestions = computed(() => this.courant()?.suggestions ?? null);
  protected readonly messageWhatsApp = computed(
    () => `Bonjour, je cherche « ${this.terme()} » et je ne le trouve pas sur le site. Pouvez-vous m'aider ?`,
  );

  protected readonly liste = { id: 'recherche', nom: 'Résultats de recherche' };
  protected readonly listeSuggestions = { id: 'recherche-suggestions', nom: 'Suggestions de recherche' };

  protected readonly source: SourceListe = {
    triParDefaut: 'pertinence',
    tris: TRIS_RECHERCHE,
    categoriesEnFiltre: true,
    filtresPour: (criteres) =>
      this.http.get<ResultatRecherche>(urlRecherche(this.terme(), criteres, 1)).pipe(map(filtresRecherche)),
    messageWhatsApp: () => this.messageWhatsApp(),
    origineWhatsApp: 'recherche-vide',
  };

  constructor() {
    const seo = inject(SeoService);
    const suivi = inject(SuiviService);
    let dernierTerme = '';
    // Navigateur uniquement : une fois par recherche affichée (barre, lien partagé ou publicité)
    afterRenderEffect(() => {
      const terme = this.terme();
      if (this.termeValide() && terme !== dernierTerme) {
        dernierTerme = terme;
        suivi.rechercher(terme);
      }
    });
    // Aussi pendant le rendu serveur (titre dans le HTML envoyé)
    effect(() => {
      seo.definirPage({
        titre: this.termeValide() ? `Recherche : ${this.terme()}` : 'Recherche',
        indexable: false,
        canonique: this.termeValide() ? `/recherche?q=${encodeURIComponent(this.terme())}` : '/recherche',
      });
    });
  }
}
