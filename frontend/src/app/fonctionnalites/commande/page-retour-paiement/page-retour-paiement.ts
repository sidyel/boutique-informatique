import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, afterNextRender, inject, input, signal } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { RetourPaiement } from '../../../noyau/api/modeles';
import { CommandeService } from '../../../noyau/commande/commande.service';
import { lireSession } from '../../../noyau/donnees/stockage-session';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Icone } from '../../../partage/composants/icone/icone';

/** Le paiement est relu toutes les 3 s, pendant 90 s au plus, tant que le prestataire ne l'a pas confirmé. */
const INTERVALLE_VERIFICATION = 3_000;
const VERIFICATIONS_MAX = 30;

/**
 * Page où le prestataire de paiement renvoie le client (`/commande/retour/:jeton`). L'adresse ne contient pas le
 * jeton de suivi, seulement un jeton de retour qui n'ouvre que le statut du paiement. noindex, `no-referrer`.
 *
 * **Le navigateur n'est jamais cru** : l'adresse peut dire « payé », seul le serveur, qui interroge le prestataire,
 * décide. `?annule=1` signale seulement que le client a quitté la page de paiement.
 */
@Component({
  selector: 'bi-page-retour-paiement',
  imports: [Bouton, DatePipe, Icone, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-retour-paiement.html',
  styleUrl: './page-retour-paiement.scss',
})
export class PageRetourPaiement {
  private readonly commandes = inject(CommandeService);
  private readonly meta = inject(Meta);
  private readonly destruction = inject(DestroyRef);

  readonly jeton = input.required<string>();
  readonly annule = input<string>();

  protected readonly retour = signal<RetourPaiement | null>(null);
  protected readonly introuvable = signal(false);
  protected readonly erreurReseau = signal(false);
  protected readonly verification = signal(true);
  /** Lien de suivi gardé par cet onglet avant le départ vers le prestataire (absent si autre appareil). */
  protected readonly lienSuivi = signal<string | null>(null);

  private arrete = false;

  constructor() {
    inject(SeoService).definirPage({ titre: 'Retour de paiement', indexable: false });
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    this.destruction.onDestroy(() => {
      this.arrete = true;
      this.meta.removeTag("name='referrer'");
    });
    afterNextRender(() => void this.demarrer());
  }

  private async demarrer(): Promise<void> {
    for (let i = 0; i < VERIFICATIONS_MAX && !this.arrete; i++) {
      if (i > 0) await new Promise((resolve) => setTimeout(resolve, INTERVALLE_VERIFICATION));
      if (!(await this.lire())) return;
      this.memoriserLienSuivi();
      // Un résultat définitif s'affiche tout de suite ; tant que rien n'est confirmé on réessaie
      if (this.retour()?.statut !== 'EN_ATTENTE' || this.annule() === '1') break;
    }
    this.verification.set(false);
  }

  private async lire(): Promise<boolean> {
    try {
      this.retour.set(await this.commandes.retour(this.jeton()));
      this.erreurReseau.set(false);
      return true;
    } catch (e) {
      if ((e as { status?: number }).status === 404) this.introuvable.set(true);
      else this.erreurReseau.set(true);
      this.verification.set(false);
      return false;
    }
  }

  private memoriserLienSuivi(): void {
    const brut = lireSession('suivi_dernier');
    const numero = this.retour()?.numero;
    if (!brut || !numero) return;
    try {
      const memo = JSON.parse(brut) as { numero?: string; jeton?: string };
      if (memo.numero === numero && memo.jeton) this.lienSuivi.set(`/suivi/${memo.jeton}`);
    } catch {
      // contenu illisible : le lien reçu par e-mail ou message reste valable
    }
  }
}
