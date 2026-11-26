import {
  ChangeDetectionStrategy,
  Component,
  afterRenderEffect,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Avis, FicheProduit, Page } from '../../noyau/api/modeles';
import { chargementMemorise } from '../../noyau/donnees/chargement-memorise';
import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { RecemmentConsultesService } from '../../noyau/donnees/recemment-consultes.service';
import { filAriane, produit as produitLd } from '../../noyau/seo/donnees-structurees';
import { SeoService } from '../../noyau/seo/seo.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Badge } from '../../partage/composants/badge/badge';
import { Etoiles } from '../../partage/composants/etoiles/etoiles';
import { EtapeAriane, FilAriane } from '../../partage/composants/fil-ariane/fil-ariane';
import { Squelette } from '../../partage/composants/squelette/squelette';
import { formaterPrixFcfa } from '../../partage/pipes/prix-fcfa.pipe';
import { reponseDefinitive } from '../../noyau/seo/reponse-definitive';
import { PageRetiree } from '../erreurs/page-retiree/page-retiree';
import { PageIntrouvable } from '../erreurs/page-introuvable/page-introuvable';
import { parametreChemin, parametresRoute } from '../liste-produits/parametres-route';
import { AvisProduit, noteAffichee } from './avis-produit/avis-produit';
import { BlocAchat } from './bloc-achat/bloc-achat';
import { ChoixVariante, ChoixVarianteFiche } from './choix-variante/choix-variante';
import {
  caracteristiquesPour,
  descriptionProduit,
  imagesVariante,
  lireIdVariante,
  mediasPour,
  nomComplet,
  resumeProduit,
  titreProduit,
  varianteInitiale,
} from './fiche';
import { Galerie } from './galerie/galerie';
import { ProduitsRecents, ProduitsSimilaires } from './produits-lies/produits-lies';

/** Lignes du tableau de caractéristiques visibles sur mobile avant « Voir toutes les caractéristiques ». */
const LIGNES_REPLIEES = 10;

/**
 * Fiche produit `/produit/:slug?variante=` : galerie, choix de la variante, bloc d'achat, caractéristiques,
 * description, avis, produits similaires et récemment consultés.
 * - Rendue côté serveur avec la variante demandée ; données reprises par le navigateur (transfer cache).
 * - Produit inconnu ou retiré : vraie 404.
 * - Choisir une variante remplace l'entrée d'historique (`?variante=`) sans faire défiler la page.
 * - URL canonique (sans `?variante=`), JSON-LD et 301 : partie 3d.
 */
@Component({
  selector: 'bi-page-fiche-produit',
  imports: [
    AvisProduit,
    Badge,
    BlocAchat,
    ChoixVarianteFiche,
    Etoiles,
    FilAriane,
    Galerie,
    PageIntrouvable,
    PageRetiree,
    ProduitsRecents,
    ProduitsSimilaires,
    RouterLink,
    Squelette,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-fiche-produit.html',
  styleUrl: './page-fiche-produit.scss',
})
export class PageFicheProduit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly site = inject(ParametresSiteService);
  private readonly suivi = inject(SuiviService);
  private readonly recents = inject(RecemmentConsultesService);

  protected readonly slug = parametreChemin('slug');
  private readonly parametres = parametresRoute();

  protected readonly ficheChargee = chargementMemorise<FicheProduit>(() => `/api/produits/${this.slug()}`);
  /** Chargés en même temps que la fiche (rendus par le serveur) ; seuls les 5 derniers. */
  protected readonly avisCharges = chargementMemorise<Page<Avis>>(() => `/api/produits/${this.slug()}/avis?taille=5`);

  /** API 404 : ancien slug (301), produit retiré (410 ou 301 vers le successeur) ou vraie 404. */
  private readonly definitive = reponseDefinitive(() =>
    !this.ficheChargee.chargement() && this.ficheChargee.statutErreur() === 404 ? `/produit/${this.slug()}` : null,
  );
  protected readonly introuvable = this.definitive.introuvable;
  protected readonly retrait = this.definitive.retrait;

  /** Fiche du slug courant (pas la précédente pendant un passage d'un produit à un autre). */
  protected readonly fiche = computed(() => {
    const fiche = this.ficheChargee.valeur();
    return fiche?.slug === this.slug() ? fiche : undefined;
  });

  protected readonly variante = computed(() => {
    const fiche = this.fiche();
    return fiche ? varianteInitiale(fiche.variantes, lireIdVariante(this.parametres()['variante'])) : undefined;
  });

  protected readonly nom = computed(() => {
    const fiche = this.fiche();
    return fiche ? nomComplet(fiche) : '';
  });
  protected readonly medias = computed(() => mediasPour(this.fiche()?.medias ?? [], this.variante()?.id));
  protected readonly caracteristiques = computed(() =>
    caracteristiquesPour(this.fiche()?.caracteristiques ?? [], this.variante()),
  );
  protected readonly nombreLignes = computed(() =>
    this.caracteristiques().reduce((total, groupe) => total + groupe.elements.length, 0),
  );
  protected readonly ariane = computed<EtapeAriane[]>(() => {
    const fiche = this.fiche();
    if (!fiche) return [];
    return [...fiche.filAriane.map((l) => ({ nom: l.nom, lien: `/categorie/${l.slug}` })), { nom: fiche.nom }];
  });
  protected readonly avis = computed(() => {
    const page = this.avisCharges.valeur();
    return this.fiche() ? page : undefined;
  });

  /** Message après le choix d'une combinaison inexistante. */
  protected readonly messageVariante = signal<string | null>(null);
  protected readonly caracteristiquesDepliees = signal(false);
  protected readonly lignesRepliees = LIGNES_REPLIEES;
  protected readonly noteAffichee = noteAffichee;

  constructor() {
    const seo = inject(SeoService);
    effect(() => {
      const fiche = this.fiche();
      const variante = this.variante();
      if (!fiche || !variante) return;
      const nom = this.nom();
      seo.definirPage({
        titre: titreProduit(nom, this.site.nomSite(), fiche.metaTitre),
        description: descriptionProduit(fiche, nom, formaterPrixFcfa(fiche.prixMinimum)),
        // Une seule canonique pour toutes les variantes (sans ?variante=, ADR 0007)
        canonique: `/produit/${fiche.slug}`,
        type: 'product',
        image: this.medias().find((m) => m.type === 'IMAGE')?.url ?? null,
        prix: variante.prix,
        donneesStructurees: (url) => [
          produitLd(fiche, nom, (v) => imagesVariante(fiche, v.id), this.avis(), url),
          filAriane(this.ariane(), url),
        ],
      });
    });

    // Navigateur uniquement : une fois par variante affichée (view_item), et mémorisée comme consultée
    let derniereVariante: number | null = null;
    afterRenderEffect(() => {
      const fiche = this.fiche();
      const variante = this.variante();
      if (!fiche || !variante || variante.id === derniereVariante) return;
      derniereVariante = variante.id;
      untracked(() => {
        const resume = resumeProduit(fiche, variante, this.medias());
        this.suivi.voirProduit(resume);
        this.recents.ajouter(resume);
      });
    });

    // Autre produit (lien d'un produit similaire) : on oublie le message de variante
    effect(() => {
      this.slug();
      untracked(() => {
        this.messageVariante.set(null);
        this.caracteristiquesDepliees.set(false);
      });
    });
  }

  /** Choix d'une variante : l'entrée d'historique est remplacée et la page ne défile pas. */
  protected choisirVariante(choix: ChoixVariante): void {
    this.messageVariante.set(choix.message);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { variante: choix.variante.id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
      scroll: 'manual',
    });
  }
}
