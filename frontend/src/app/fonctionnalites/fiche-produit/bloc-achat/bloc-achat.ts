import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import { FicheProduit, VarianteFiche } from '../../../noyau/api/modeles';
import { ParametresSiteService, lienWhatsApp } from '../../../noyau/donnees/parametres-site.service';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SuiviService } from '../../../noyau/suivi/suivi.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Icone } from '../../../partage/composants/icone/icone';
import { ICONES_CONFIANCE } from '../../../partage/composants/icone/icones-confiance';
import { Prix } from '../../../partage/composants/prix/prix';
import { formaterPrixFcfa } from '../../../partage/pipes/prix-fcfa.pipe';
import { messageWhatsAppProduit, reassurance } from '../fiche';
import { ZonesLivraison } from '../zones-livraison/zones-livraison';

/**
 * Bloc d'achat de la fiche : prix TTC, disponibilité, délai de livraison, boutons, téléphone, réassurance.
 * - Ordre des boutons réglé dans les paramètres (`boutonPrincipalFiche`) : panier en principal par défaut,
 *   WhatsApp juste en dessous, bien visible (ou l'inverse).
 * - Variante en rupture : prix affiché, bouton panier désactivé, pas de délai de livraison ; WhatsApp
 *   reste proposé pour demander le retour en stock.
 * - Sur mobile, une barre d'achat collante (bas de l'écran, à portée du pouce) apparaît quand les boutons
 *   sortent de l'écran.
 */
@Component({
  selector: 'bi-bloc-achat',
  imports: [Bouton, Icone, Prix, ZonesLivraison],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './bloc-achat.html',
  styleUrl: './bloc-achat.scss',
})
export class BlocAchat {
  private readonly site = inject(ParametresSiteService);
  private readonly panier = inject(PanierService);
  private readonly suivi = inject(SuiviService);
  private readonly document = inject(DOCUMENT);

  readonly fiche = input.required<FicheProduit>();
  readonly variante = input.required<VarianteFiche>();
  /** Nom complet affiché (marque + nom). */
  readonly nom = input.required<string>();

  private readonly actions = viewChild.required<ElementRef<HTMLElement>>('actions');

  protected readonly rupture = computed(() => this.variante().disponibilite === 'RUPTURE');
  protected readonly whatsappPrincipal = computed(() => this.site.parametres()?.boutonPrincipalFiche === 'WHATSAPP');
  protected readonly delai = computed(() => (this.rupture() ? null : (this.site.parametres()?.delaiLivraison ?? null)));
  protected readonly elementsConfiance = computed(() =>
    reassurance(this.site.parametres()?.elementsConfiance ?? []).map((e) => ({
      ...e,
      nomIcone: e.icone ? ICONES_CONFIANCE[e.icone] : null,
    })),
  );
  protected readonly telephone = computed(() => ({
    lien: this.site.lienTelephone(),
    affiche: this.site.telephoneAffiche(),
  }));

  protected readonly lienWhatsApp = computed(() => {
    const variante = this.variante();
    const fiche = this.fiche();
    const origine = this.document.location?.origin ?? '';
    const url = `${origine}/produit/${fiche.slug}?variante=${variante.id}`;
    const message = messageWhatsAppProduit(
      this.nom(),
      variante,
      formaterPrixFcfa(variante.prix),
      url,
      fiche.variantes.length > 1,
    );
    return lienWhatsApp(this.site.parametres()?.whatsapp, message);
  });

  /** Réponse au clic sur « Ajouter au panier » tant que la commande en ligne n'est pas ouverte (phase 4). */
  protected readonly annonce = signal<string | null>(null);
  /** Boutons principaux visibles à l'écran (sinon, barre collante sur mobile). */
  protected readonly actionsVisibles = signal(true);
  protected readonly demandesZones = signal(0);

  constructor() {
    const destruction = inject(DestroyRef);
    afterNextRender(() => {
      if (typeof IntersectionObserver === 'undefined') return;
      const observateur = new IntersectionObserver(([entree]) => this.actionsVisibles.set(entree.isIntersecting));
      observateur.observe(this.actions().nativeElement);
      destruction.onDestroy(() => observateur.disconnect());
    });
  }

  protected ajouterAuPanier(): void {
    const variante = this.variante();
    if (this.rupture()) return;
    this.panier.ajouter(variante.sku, variante.prix);
    this.annonce.set(
      'La commande en ligne ouvre très bientôt : en attendant, commandez sur WhatsApp ou par téléphone.',
    );
  }

  protected contacterWhatsApp(): void {
    this.suivi.contactWhatsApp('fiche-produit', this.variante().sku);
  }

  protected voirZones(): void {
    this.demandesZones.update((n) => n + 1);
  }
}
