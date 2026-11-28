import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ZoneLivraison } from '../../../noyau/api/modeles';
import { ParametresSiteService, phraseInformationPanier } from '../../../noyau/donnees/parametres-site.service';
import { articlesSuivis, valeurSuivie } from '../../../noyau/panier/articles-suivis';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Icone } from '../../../partage/composants/icone/icone';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';
import { LignePanier } from '../ligne-panier/ligne-panier';
import { ResumePanier } from '../resume-panier/resume-panier';

/**
 * Page panier (`/panier`, noindex) : lignes, code promo, estimation de la livraison par zone, montants.
 * Sur mobile, le total et « Commander » restent dans une barre en bas de l'écran.
 *
 * Rendue côté serveur avec des squelettes : le contenu (personnel) n'est chargé que dans le navigateur.
 */
@Component({
  selector: 'bi-page-panier',
  imports: [Bouton, Icone, LignePanier, PrixFcfaPipe, ResumePanier, RouterLink, Squelette],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './page-panier.html',
  styleUrl: './page-panier.scss',
})
export class PagePanier {
  protected readonly panier = inject(PanierService);
  private readonly http = inject(HttpClient);
  private readonly suivi = inject(SuiviService);
  private readonly site = inject(ParametresSiteService);

  /** Délai de livraison et moyens de paiement d'après les paramètres du site ; rien tant qu'ils ne sont pas connus. */
  protected readonly information = computed(() => {
    const parametres = this.site.parametres();
    return parametres ? phraseInformationPanier(parametres.delaiLivraison, this.site.modesPaiement()) : null;
  });

  protected readonly zones = signal<ZoneLivraison[]>([]);
  protected readonly saisieCode = signal('');
  protected readonly messageCode = signal<string | null>(null);

  constructor() {
    inject(SeoService).definirPage({ titre: 'Mon panier', indexable: false });
    afterNextRender(async () => {
      this.http.get<ZoneLivraison[]>('/api/livraison/zones').subscribe({
        next: (zones) => this.zones.set(zones),
        error: () => this.zones.set([]),
      });
      await this.panier.charger();
      const p = this.panier.panier();
      if (p?.lignes.length) this.suivi.voirPanier(articlesSuivis(p), valeurSuivie(p));
    });
  }

  protected async appliquerCode(): Promise<void> {
    const code = this.saisieCode().trim();
    if (!code) return;
    this.messageCode.set(null);
    if (await this.panier.appliquerCode(code)) {
      this.saisieCode.set('');
    } else {
      this.messageCode.set(this.panier.erreur());
      this.panier.erreur.set(null);
    }
  }

  protected choisirZone(valeur: string): void {
    void this.panier.choisirZone(valeur ? Number(valeur) : null);
  }
}
