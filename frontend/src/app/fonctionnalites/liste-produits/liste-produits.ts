import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Filtres, Page, ProduitResume } from '../../noyau/api/modeles';
import { ListeSuivie, SuiviService } from '../../noyau/suivi/suivi.service';
import { CarteProduit } from '../../partage/composants/carte-produit/carte-produit';
import { Icone } from '../../partage/composants/icone/icone';
import { Squelette } from '../../partage/composants/squelette/squelette';
import { CodeTri, Criteres, nombreFiltres, pucesActives, sansFiltres, versParametres } from './criteres';
import { DemandeWhatsapp } from './demande-whatsapp/demande-whatsapp';
import { FeuilleFiltres } from './feuille-filtres/feuille-filtres';
import { Pagination } from './pagination/pagination';
import { PanneauFiltres } from './panneau-filtres/panneau-filtres';
import { SourceListe } from './source-liste';

/** Cartes chargées sans attendre (2 colonnes sur téléphone : la première rangée contient l'image LCP). */
const CARTES_PRIORITAIRES = 2;
/**
 * Cartes hydratées au démarrage : la première rangée quelle que soit la largeur (4 colonnes au plus).
 * Les autres sont rendues par le serveur (HTML et SEO identiques) mais leur JavaScript ne s'exécute
 * qu'à leur arrivée à l'écran : moins de travail au démarrage sur un Android d'entrée de gamme.
 */
const CARTES_HYDRATEES_AU_DEMARRAGE = 4;
const CARTES_SQUELETTE = 8;

/**
 * Vue commune des listes de produits (catégorie, marque, recherche) : filtres actifs, tri, grille,
 * pagination, liste vide ; facettes en colonne sur ordinateur, dans une feuille sur téléphone.
 *
 * Historique : un filtre ou un tri REMPLACE l'entrée d'historique courante (pas une entrée par case
 * cochée) et ne fait pas défiler la page ; un changement de page en CRÉE une (lien `?page=n`). Revenir
 * d'une fiche produit retrouve donc la même page, les mêmes filtres et la même position (réponses en
 * mémoire : voir `chargementMemorise`).
 */
@Component({
  selector: 'bi-liste-produits',
  imports: [
    CarteProduit,
    DemandeWhatsapp,
    FeuilleFiltres,
    Icone,
    Pagination,
    PanneauFiltres,
    RouterLink,
    Squelette,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './liste-produits.html',
  styleUrl: './liste-produits.scss',
})
export class ListeProduits {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly suivi = inject(SuiviService);

  readonly produits = input.required<Page<ProduitResume> | undefined>();
  readonly filtres = input.required<Filtres | undefined>();
  readonly criteres = input.required<Criteres>();
  readonly chargement = input(false);
  /** Paramètres refusés par l'API (400) : lien vers la liste sans filtres. */
  readonly invalide = input(false);
  readonly liste = input.required<ListeSuivie>();
  readonly source = input.required<SourceListe>();

  private readonly resultats = viewChild<ElementRef<HTMLElement>>('resultats');

  protected readonly squelettes = Array.from({ length: CARTES_SQUELETTE }, (_, i) => i);
  protected readonly prioritaires = CARTES_PRIORITAIRES;
  protected readonly hydrateesAuDemarrage = CARTES_HYDRATEES_AU_DEMARRAGE;
  /** Nombre de demandes d'ouverture de la feuille (son code n'est chargé qu'au premier toucher). */
  protected readonly demandesFeuille = signal(0);

  protected readonly nombreActifs = computed(() => nombreFiltres(this.criteres()));
  protected readonly puces = computed(() => pucesActives(this.criteres(), this.filtres()));
  protected readonly triCourant = computed<CodeTri>(() => this.criteres().tri ?? this.source().triParDefaut);
  protected readonly parametresPage = computed(() => {
    const criteres = this.criteres();
    const tri = this.source().triParDefaut;
    return (page: number) => versParametres({ ...criteres, page }, tri);
  });
  protected readonly parametresSansFiltres = computed(() =>
    versParametres(sansFiltres(this.criteres()), this.source().triParDefaut),
  );
  protected readonly messageWhatsApp = computed(() => this.source().messageWhatsApp(this.criteres()));

  constructor() {
    // Navigateur uniquement : une fois par contenu affiché
    afterRenderEffect(() => {
      const page = this.produits();
      if (page?.elements.length) this.suivi.voirListe(this.liste(), page.elements);
    });
  }

  /**
   * Applique des critères : l'entrée d'historique est remplacée et la page ne défile pas (la colonne de
   * filtres reste sous le doigt). Depuis la feuille mobile, on remonte en haut des résultats.
   */
  protected appliquer(criteres: Criteres, remonter = false): void {
    void this.router
      .navigate([], {
        relativeTo: this.route,
        queryParams: versParametres(criteres, this.source().triParDefaut),
        replaceUrl: true,
        scroll: 'manual',
      })
      .then((ok) => {
        if (ok && remonter) this.resultats()?.nativeElement.scrollIntoView({ block: 'start' });
      });
  }

  protected trier(code: string): void {
    this.appliquer({ ...this.criteres(), tri: code as CodeTri, page: 1 });
  }

  protected ouvrirFeuille(): void {
    this.demandesFeuille.update((n) => n + 1);
  }
}
