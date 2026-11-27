import { ChangeDetectionStrategy, Component, DestroyRef, afterNextRender, inject, signal } from '@angular/core';

import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Icone } from '../../partage/composants/icone/icone';

/** Défilement minimal (px) avant de réagir : un frémissement du doigt ne fait pas clignoter le bouton. */
const SEUIL_DEFILEMENT = 12;
/** En haut de page, le bouton reste visible. */
const HAUT_DE_PAGE = 120;

/** Champs dont le focus ouvre le clavier : le bouton s'efface pour ne pas gêner la saisie. */
function estChampDeSaisie(cible: EventTarget | null): boolean {
  if (!(cible instanceof HTMLElement)) return false;
  if (cible.isContentEditable) return true;
  if (cible instanceof HTMLTextAreaElement || cible instanceof HTMLSelectElement) return true;
  if (cible instanceof HTMLInputElement) {
    return !['button', 'submit', 'reset', 'checkbox', 'radio', 'range', 'file', 'image', 'color'].includes(cible.type);
  }
  return false;
}

/**
 * Bouton WhatsApp flottant, à portée du pouce (bas droit), au-dessus de la barre de navigation des téléphones
 * (`safe-area-inset-bottom`). Lien `wa.me` : un toucher ouvre directement l'application. Masqué tant qu'aucun numéro
 * WhatsApp n'est saisi dans les paramètres.
 * <p>
 * Un bouton fixe recouvre forcément quelque chose pendant le défilement. Règles pour qu'il ne gêne jamais :
 * <ul>
 *   <li>espace réservé sous le pied de page (`--reserve-whatsapp`) : tout lien ou bouton peut être amené au-dessus ;</li>
 *   <li>absent des pages qui ont déjà une barre fixe en bas ou un accès WhatsApp (fiche produit, panier, commande :
 *       règle dans `styles/_utilitaires.scss`) ;</li>
 *   <li>effacé quand un champ de saisie a le focus (clavier ouvert) ;</li>
 *   <li>effacé pendant le défilement vers le bas, de retour dès qu'on remonte (écouteur passif, aucun calcul de mise en
 *       page).</li>
 * </ul>
 */
@Component({
  selector: 'bi-bouton-whatsapp',
  imports: [Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (site.lienWhatsApp(); as lien) {
      <a
        class="whatsapp"
        [class.whatsapp--efface]="efface()"
        [href]="lien"
        target="_blank"
        rel="noopener"
        [attr.aria-label]="'Nous écrire sur WhatsApp au ' + site.whatsappAffiche()"
        [attr.tabindex]="efface() ? -1 : null"
        [attr.aria-hidden]="efface() ? 'true' : null"
        (click)="suivi.contactWhatsApp('flottant')"
      >
        <bi-icone nom="message" taille="1.75rem" />
      </a>
    }
  `,
  styles: `
    .whatsapp {
      position: fixed;
      inset-inline-end: var(--espace-4);
      inset-block-end: calc(var(--espace-4) + env(safe-area-inset-bottom, 0px));
      z-index: var(--z-collant);
      display: grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      color: var(--couleur-sur-whatsapp);
      background: var(--couleur-whatsapp);
      border-radius: var(--rayon-rond);
      box-shadow: var(--ombre-2);
      transition:
        transform var(--duree-normale) ease,
        opacity var(--duree-normale) ease,
        visibility 0s;

      &:hover {
        color: var(--couleur-sur-whatsapp);
        background: var(--couleur-whatsapp-survol);
      }
    }

    // Effacé : sort par le bas, n'est plus focalisable ni cliquable
    .whatsapp--efface {
      transform: translateY(calc(100% + var(--espace-4) + env(safe-area-inset-bottom, 0px)));
      opacity: 0;
      visibility: hidden;
      pointer-events: none;
      transition:
        transform var(--duree-normale) ease,
        opacity var(--duree-normale) ease,
        visibility 0s var(--duree-normale);
    }
  `,
})
export class BoutonWhatsapp {
  protected readonly site = inject(ParametresSiteService);
  protected readonly suivi = inject(SuiviService);

  private readonly champActif = signal(false);
  private readonly defilementVersLeBas = signal(false);
  protected readonly efface = () => this.champActif() || this.defilementVersLeBas();

  constructor() {
    const destruction = inject(DestroyRef);
    // Navigateur seulement : jamais d'écouteur pendant le rendu serveur
    afterNextRender(() => {
      let dernierY = window.scrollY;
      const surDefilement = () => {
        const y = window.scrollY;
        const ecart = y - dernierY;
        if (Math.abs(ecart) < SEUIL_DEFILEMENT) return;
        this.defilementVersLeBas.set(ecart > 0 && y > HAUT_DE_PAGE);
        dernierY = y;
      };
      const surFocus = (e: FocusEvent) => this.champActif.set(estChampDeSaisie(e.target));
      const surPerteDeFocus = () => this.champActif.set(false);

      window.addEventListener('scroll', surDefilement, { passive: true });
      document.addEventListener('focusin', surFocus);
      document.addEventListener('focusout', surPerteDeFocus);
      destruction.onDestroy(() => {
        window.removeEventListener('scroll', surDefilement);
        document.removeEventListener('focusin', surFocus);
        document.removeEventListener('focusout', surPerteDeFocus);
      });
    });
  }
}
