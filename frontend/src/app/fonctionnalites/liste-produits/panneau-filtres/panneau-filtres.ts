import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { EtatProduit, Filtres, OptionFiltre } from '../../../noyau/api/modeles';
import {
  Criteres,
  avecFiltre,
  basculer,
  basculerCaracteristique,
  tranchesPrix,
} from '../criteres';

/** Au-delà, une liste de valeurs est repliée derrière « Voir plus ». */
const VALEURS_VISIBLES = 6;

interface Groupe {
  id: string;
  titre: string;
  options: OptionFiltre[];
  basculer: (valeur: string) => Criteres;
}

/**
 * Facettes d'une liste : catégories (si filtre), prix (tranches rondes et saisie libre), marques,
 * disponibilité, état, caractéristiques. Chaque option affiche son nombre de produits ; une option à 0
 * est grisée (sauf si elle est cochée, pour pouvoir la décocher). Le panneau ne navigue pas lui-même :
 * il émet les critères voulus, appliqués tout de suite (colonne latérale) ou au bouton « Voir … »
 * (feuille mobile).
 */
@Component({
  selector: 'bi-panneau-filtres',
  imports: [NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './panneau-filtres.html',
  styleUrl: './panneau-filtres.scss',
})
export class PanneauFiltres {
  readonly filtres = input.required<Filtres>();
  readonly criteres = input.required<Criteres>();
  readonly categoriesEnFiltre = input(false);

  readonly changer = output<Criteres>();

  /** Groupes dépliés par « Voir plus ». */
  protected readonly deplies = signal<ReadonlySet<string>>(new Set());

  protected readonly tranches = computed(() => {
    const prix = this.filtres().prix;
    return prix ? tranchesPrix(prix.min, prix.max) : [];
  });

  protected readonly groupesCategorie = computed<Groupe[]>(() => {
    const criteres = this.criteres();
    const options = this.filtres().sousCategories;
    if (!this.categoriesEnFiltre() || !options.length) return [];
    return [
      {
        id: 'categorie',
        titre: 'Catégorie',
        options,
        // Une seule catégorie à la fois (le paramètre de l'API est unique)
        basculer: (valeur) => avecFiltre(criteres, { categorie: criteres.categorie === valeur ? null : valeur }),
      },
    ];
  });

  protected readonly groupesMarque = computed<Groupe[]>(() => {
    const criteres = this.criteres();
    const options = this.filtres().marques;
    if (!options.length) return [];
    return [
      {
        id: 'marque',
        titre: 'Marque',
        options,
        basculer: (valeur) => avecFiltre(criteres, { marques: basculer(criteres.marques, valeur) }),
      },
    ];
  });

  protected readonly groupesEtat = computed<Groupe[]>(() => {
    const criteres = this.criteres();
    const etats = this.filtres().etats;
    // Un seul état possible (tout est neuf) : le filtre n'apprendrait rien au client
    if (etats.length < 2 && !criteres.etats.length) return [];
    return [
      {
        id: 'etat',
        titre: 'État',
        options: etats,
        basculer: (valeur) =>
          avecFiltre(criteres, { etats: basculer(criteres.etats, valeur as EtatProduit) }),
      },
    ];
  });

  protected readonly groupesCaracteristiques = computed<Groupe[]>(() => {
    const criteres = this.criteres();
    return this.filtres()
      .caracteristiques.filter((c) => c.valeurs.length > 1 || criteres.caracteristiques[c.cle]?.length)
      .map((c) => ({
        id: `f-${c.cle}`,
        titre: c.libelle,
        options: c.valeurs,
        basculer: (valeur: string) => basculerCaracteristique(criteres, c.cle, valeur),
      }));
  });

  protected visibles(groupe: Groupe): OptionFiltre[] {
    if (this.deplies().has(groupe.id) || groupe.options.length <= VALEURS_VISIBLES + 1) return groupe.options;
    // Les options cochées restent toujours visibles
    return groupe.options.filter((o, i) => i < VALEURS_VISIBLES || o.selectionne);
  }

  protected deplier(groupe: Groupe): void {
    this.deplies.update((ids) => new Set([...ids, groupe.id]));
  }

  protected choisirTranche(prixMin: number | null, prixMax: number | null): void {
    const criteres = this.criteres();
    const dejaChoisie = criteres.prixMin === prixMin && criteres.prixMax === prixMax;
    this.changer.emit(
      avecFiltre(criteres, dejaChoisie ? { prixMin: null, prixMax: null } : { prixMin, prixMax }),
    );
  }

  protected saisirPrix(evenement: SubmitEvent): void {
    evenement.preventDefault();
    const donnees = new FormData(evenement.target as HTMLFormElement);
    const lire = (nom: string) => {
      const chiffres = String(donnees.get(nom) ?? '').replace(/\D/g, '');
      return chiffres ? Math.min(Number(chiffres), 999_999_999_999) : null;
    };
    let prixMin = lire('prixMin');
    let prixMax = lire('prixMax');
    if (prixMin !== null && prixMax !== null && prixMin > prixMax) {
      [prixMin, prixMax] = [prixMax, prixMin];
    }
    this.changer.emit(avecFiltre(this.criteres(), { prixMin, prixMax }));
  }

  protected basculerDisponible(): void {
    this.changer.emit(avecFiltre(this.criteres(), { disponible: !this.criteres().disponible }));
  }
}
