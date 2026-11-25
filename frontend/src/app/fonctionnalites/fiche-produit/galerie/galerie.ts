import { NgOptimizedImage } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { MediaFiche } from '../../../noyau/api/modeles';
import { Icone } from '../../../partage/composants/icone/icone';
import { apercu } from '../fiche';

/** Facteur du zoom (survol sur ordinateur, toucher en plein écran). */
const ZOOM = 2;

/**
 * Galerie de la fiche produit.
 * - Une piste à défilement natif avec accrochage : balayage au doigt sur mobile, sans bibliothèque.
 * - Miniatures et flèches sur ordinateur ; compteur « 2 / 5 » sur mobile.
 * - Première image prioritaire (élément LCP), les suivantes en chargement différé.
 * - Zoom au survol sur ordinateur ; équivalent au toucher : plein écran (`<dialog>`) où un toucher
 *   agrandit l'image à l'endroit touché, déplaçable au doigt (défilement natif).
 * - Vidéo YouTube : façade légère (aperçu + bouton lecture), lecteur youtube-nocookie chargé au clic.
 *   Vidéo hébergée : `preload="none"` avec image d'aperçu.
 */
@Component({
  selector: 'bi-galerie',
  imports: [Icone, NgOptimizedImage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './galerie.html',
  styleUrl: './galerie.scss',
})
export class Galerie {
  private readonly assainisseur = inject(DomSanitizer);

  readonly medias = input.required<MediaFiche[]>();
  /** Nom du produit : texte alternatif par défaut. */
  readonly nom = input.required<string>();

  private readonly piste = viewChild<ElementRef<HTMLElement>>('piste');
  private readonly fenetre = viewChild<ElementRef<HTMLDialogElement>>('fenetre');
  private readonly pistePleinEcran = viewChild<ElementRef<HTMLElement>>('pistePleinEcran');

  protected readonly actif = signal(0);
  protected readonly pleinEcran = signal(false);
  /** Diapositive agrandie en plein écran (-1 : aucune). */
  protected readonly agrandie = signal(-1);
  /** Vidéos YouTube dont le lecteur a été demandé (id du média). */
  protected readonly lecteurs = signal<ReadonlySet<number>>(new Set());
  /** Zoom au survol : origine en % (ordinateur, souris uniquement). */
  protected readonly loupe = signal<{ index: number; x: number; y: number } | null>(null);

  protected readonly apercu = apercu;
  protected readonly nombre = computed(() => this.medias().length);

  constructor() {
    // Nouvelle variante (photos propres) : retour à la première image (navigateur uniquement)
    afterRenderEffect(() => {
      this.medias();
      untracked(() => {
        this.actif.set(0);
        this.piste()?.nativeElement.scrollTo({ left: 0 });
      });
    });
  }

  protected alt(media: MediaFiche, index: number): string {
    const texte = media.texteAlternatif?.trim();
    if (texte) return texte;
    return index === 0 ? this.nom() : `${this.nom()}, vue ${index + 1}`;
  }

  protected lienYoutube(media: MediaFiche): SafeResourceUrl {
    // Identifiant validé par le backend (oEmbed) ; domaine sans cookie, lecture immédiate après le clic
    const id = encodeURIComponent(media.idYoutube ?? '');
    return this.assainisseur.bypassSecurityTrustResourceUrl(
      `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0`,
    );
  }

  protected lire(media: MediaFiche): void {
    this.lecteurs.update((ids) => new Set(ids).add(media.id));
  }

  /** Défilement de la piste (balayage) : met à jour le compteur et la miniature active. */
  protected suivreDefilement(element: HTMLElement): void {
    const largeur = element.clientWidth;
    if (largeur > 0) this.actif.set(Math.round(element.scrollLeft / largeur));
  }

  protected aller(index: number): void {
    const borne = Math.min(Math.max(index, 0), this.nombre() - 1);
    const piste = this.piste()?.nativeElement;
    this.actif.set(borne);
    piste?.scrollTo({ left: borne * piste.clientWidth, behavior: 'smooth' });
  }

  // --- Zoom au survol (ordinateur) ------------------------------------------------------------

  protected survoler(evenement: PointerEvent, index: number): void {
    if (evenement.pointerType !== 'mouse') return;
    const zone = (evenement.currentTarget as HTMLElement).getBoundingClientRect();
    this.loupe.set({
      index,
      x: ((evenement.clientX - zone.left) / zone.width) * 100,
      y: ((evenement.clientY - zone.top) / zone.height) * 100,
    });
  }

  protected quitterSurvol(): void {
    this.loupe.set(null);
  }

  // --- Plein écran ------------------------------------------------------------------------------

  protected ouvrirPleinEcran(index: number): void {
    this.loupe.set(null);
    this.agrandie.set(-1);
    this.pleinEcran.set(true);
    // Le contenu est créé par le rendu suivant : ouverture et positionnement juste après
    queueMicrotask(() =>
      requestAnimationFrame(() => {
        this.fenetre()?.nativeElement.showModal();
        const piste = this.pistePleinEcran()?.nativeElement;
        piste?.scrollTo({ left: index * piste.clientWidth });
      }),
    );
  }

  protected fermerPleinEcran(): void {
    this.fenetre()?.nativeElement.close();
  }

  /** Fermeture (bouton, Échap, geste retour d'Android) : on revient sur l'image regardée. */
  protected apresFermeture(): void {
    const piste = this.pistePleinEcran()?.nativeElement;
    const index = piste && piste.clientWidth > 0 ? Math.round(piste.scrollLeft / piste.clientWidth) : this.actif();
    this.pleinEcran.set(false);
    this.agrandie.set(-1);
    this.aller(index);
  }

  /** Toucher ou clic en plein écran : agrandit à l'endroit touché, ou revient à la taille normale. */
  protected basculerZoom(evenement: MouseEvent, index: number): void {
    const cadre = evenement.currentTarget as HTMLElement;
    if (this.agrandie() === index) {
      this.agrandie.set(-1);
      return;
    }
    const zone = cadre.getBoundingClientRect();
    const x = (evenement.clientX - zone.left) / zone.width;
    const y = (evenement.clientY - zone.top) / zone.height;
    this.agrandie.set(index);
    // Après agrandissement, le point touché reste sous le doigt
    requestAnimationFrame(() => {
      cadre.scrollTo({
        left: x * cadre.scrollWidth - zone.width / 2,
        top: y * cadre.scrollHeight - zone.height / 2,
      });
    });
  }

  protected readonly zoom = ZOOM;
}
