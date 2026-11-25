import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CatalogueService } from '../../noyau/donnees/catalogue.service';
import { ParametresSiteService } from '../../noyau/donnees/parametres-site.service';
import { SuiviService } from '../../noyau/suivi/suivi.service';
import { Icone } from '../../partage/composants/icone/icone';
import { Logo } from '../logo/logo';

/** Pages d'information (contenus de la table `page`, servis par une route dédiée dans une partie suivante). */
const INFORMATIONS = [
  { slug: 'livraison', titre: 'Livraison' },
  { slug: 'retours-garantie', titre: 'Retours et garantie' },
  { slug: 'a-propos', titre: 'À propos' },
  { slug: 'contact', titre: 'Contact' },
  { slug: 'cgv', titre: 'Conditions générales de vente' },
  { slug: 'mentions-legales', titre: 'Mentions légales' },
  { slug: 'confidentialite', titre: 'Politique de confidentialité' },
];

/** Moyens de paiement proposés (cahier des charges §3), affichés en texte : aucun logo de marque copié. */
const PAIEMENTS = ['Wave', 'Orange Money', 'Free Money', 'Carte bancaire', 'Paiement à la livraison'];

const RESEAUX: Record<string, string> = {
  facebook: 'Facebook',
  instagram: 'Instagram',
  tiktok: 'TikTok',
  youtube: 'YouTube',
  linkedin: 'LinkedIn',
  x: 'X',
};

@Component({
  selector: 'bi-pied-de-page',
  imports: [Icone, Logo, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pied-de-page.html',
  styleUrl: './pied-de-page.scss',
})
export class PiedDePage {
  protected readonly site = inject(ParametresSiteService);
  protected readonly catalogue = inject(CatalogueService);
  private readonly suivi = inject(SuiviService);

  protected readonly informations = INFORMATIONS;
  protected readonly paiements = PAIEMENTS;
  protected readonly annee = new Date().getFullYear();

  protected readonly reseaux = computed(() =>
    Object.entries(this.site.parametres()?.liensReseauxSociaux ?? {})
      .filter(([, url]) => !!url)
      .map(([cle, url]) => ({ nom: RESEAUX[cle] ?? cle, url })),
  );

  protected contactWhatsApp(): void {
    this.suivi.contactWhatsApp('pied-de-page');
  }

  protected contactTelephone(): void {
    this.suivi.contactTelephone('pied-de-page');
  }
}
