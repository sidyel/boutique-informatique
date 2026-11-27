import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SuiviCommande } from '../../../noyau/api/modeles';
import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { CompteService } from '../../../noyau/compte/compte.service';
import { ParametresSiteService, lienWhatsApp } from '../../../noyau/donnees/parametres-site.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Squelette } from '../../../partage/composants/squelette/squelette';
import { PrixFcfaPipe } from '../../../partage/pipes/prix-fcfa.pipe';
import { EtapeSuivi, LIBELLES_STATUT, parcours } from '../../commande/page-suivi/suivi-statuts';

/**
 * Détail d'une commande du compte : historique des statuts, lignes, totaux, facture et avoirs (téléchargés avec le jeton
 * d'accès, jamais en cache), reprise du paiement si la commande attend son règlement, WhatsApp avec le numéro prérempli.
 */
@Component({
  selector: 'bi-detail-commande',
  imports: [RouterLink, Bouton, Squelette, PrixFcfaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../commande/page-suivi/page-suivi.scss', '../espace.scss'],
  template: `
    <a class="espace__retour" routerLink="/compte/commandes">← Mes commandes</a>
    @if (introuvable()) {
      <h1 class="espace__titre">Commande introuvable</h1>
      <p class="auth__intro">Cette commande n’existe pas dans votre compte.</p>
      <a biBouton routerLink="/compte/commandes">Voir mes commandes</a>
    } @else if (commande(); as c) {
      <div class="suivi suivi--compte">
      <header class="suivi__entete">
        <h1 class="suivi__titre">Commande <span class="suivi__numero">{{ c.numero }}</span></h1>
        <p class="suivi__statut suivi__statut--{{ c.statut }}">{{ libelles[c.statut] }}</p>
        <p class="espace__aide">Passée le {{ dateLisible(c.dateCreation) }}</p>
      </header>

      @if (c.remboursementEnCours) {
        <section class="message message--info" role="status">
          <p><strong>Votre paiement va être remboursé.</strong></p>
          <p>Nous ne pouvons pas honorer cette commande (articles épuisés ou paiement en double). Notre équipe vous contacte.</p>
        </section>
      }
      @if (c.statut === 'EN_ATTENTE_PAIEMENT') {
        @if (c.peutReprendrePaiement) {
          <section class="message message--alerte">
            <p><strong>Paiement en attente.</strong></p>
            <p>Votre commande est réservée jusqu’au {{ dateHeure(c.expireLe) }}. Passé ce délai, elle est annulée.</p>
            @if (erreurReprise()) {
              <p class="message__erreur" role="alert">{{ erreurReprise() }}</p>
            }
            <button biBouton variante="principal" taille="lg" type="button" [chargement]="reprise()" (click)="reprendre(c.numero)">
              Reprendre le paiement
            </button>
          </section>
        } @else {
          <section class="message message--alerte">
            <p><strong>Paiement non reçu.</strong></p>
            <p>Le délai de paiement est dépassé. Si vous avez payé, contactez-nous avec votre numéro de commande.</p>
          </section>
        }
      } @else if (c.statut === 'EN_ATTENTE_CONFIRMATION') {
        <section class="message message--alerte">
          <p><strong>Nous allons vous appeler pour confirmer votre commande.</strong></p>
          <p>Nous appelons le {{ c.telephoneMasque }}. Sans confirmation, la commande est annulée.</p>
        </section>
      } @else if (c.statut === 'ANNULEE') {
        <section class="message message--danger"><p><strong>Cette commande est annulée.</strong></p></section>
      }

      @if (etapes().length) {
        <ol class="parcours" aria-label="Avancement de la commande">
          @for (etape of etapes(); track etape.statut) {
            <li class="parcours__etape parcours__etape--{{ etape.etat }}" [attr.aria-current]="etape.etat === 'courante' ? 'step' : null">
              <span class="parcours__point" aria-hidden="true"></span>
              <span class="parcours__libelle">{{ etape.libelle }}</span>
            </li>
          }
        </ol>
      }

      <section class="bloc">
        <h2 class="bloc__titre">Historique</h2>
        <ul class="historique">
          @for (e of c.etapes; track $index) {
            <li><span>{{ libelles[e.statut] }}</span> <time [attr.datetime]="e.date">{{ dateHeure(e.date) }}</time></li>
          }
        </ul>
      </section>

      <section class="bloc">
        <h2 class="bloc__titre">Articles</h2>
        <ul class="articles">
          @for (ligne of c.lignes; track ligne.sku) {
            <li class="article">
              <span>{{ ligne.nomProduit }}
                <span class="article__variante">{{ ligne.libelleVariante }} × {{ ligne.quantite }}</span></span>
              <span class="article__prix">{{ ligne.totalLigne | prixFcfa }}</span>
            </li>
          }
        </ul>
        <dl class="totaux">
          <div><dt>Sous-total</dt><dd>{{ c.sousTotal | prixFcfa }}</dd></div>
          @if (c.remise > 0) {
            <div class="totaux__remise"><dt>Remise {{ c.codePromo }}</dt><dd>−{{ c.remise | prixFcfa }}</dd></div>
          }
          <div><dt>Livraison</dt><dd>{{ c.fraisLivraison === 0 ? 'Offerte' : (c.fraisLivraison | prixFcfa) }}</dd></div>
          <div class="totaux__total"><dt>Total</dt><dd>{{ c.total | prixFcfa }}</dd></div>
        </dl>
      </section>

      @if (c.numeroFacture) {
        <section class="bloc">
          <h2 class="bloc__titre">Documents</h2>
          @if (erreurDocument()) {
            <p class="message__erreur" role="alert">{{ erreurDocument() }}</p>
          }
          <ul class="documents">
            <li>
              <button biBouton variante="contour" type="button" (click)="telecharger(c.numero, undefined, c.numeroFacture)">
                Facture {{ c.numeroFacture }} (PDF)
              </button>
            </li>
            @for (avoir of c.numerosAvoirs; track avoir) {
              <li>
                <button biBouton variante="contour" type="button" (click)="telecharger(c.numero, avoir, avoir)">
                  Avoir {{ avoir }} (PDF)
                </button>
              </li>
            }
          </ul>
        </section>
      }

      <section class="bloc">
        <h2 class="bloc__titre">Livraison et paiement</h2>
        <dl class="infos">
          <div>
            <dt>Mode</dt>
            <dd>{{ c.modeLivraison === 'RETRAIT_MAGASIN' ? 'Retrait en boutique' : 'Livraison à domicile' + (c.zoneLivraison ? ' · ' + c.zoneLivraison : '') }}</dd>
          </div>
          @if (c.adresseMasquee) { <div><dt>Adresse</dt><dd>{{ c.adresseMasquee }}</dd></div> }
          <div><dt>Paiement</dt>
            <dd>{{ c.modePaiement === 'A_LA_LIVRAISON' ? 'À la livraison ou au retrait' : 'Mobile money' }}</dd>
          </div>
        </dl>
      </section>

      @if (whatsapp(); as lien) {
        <p class="aide">
          Une question sur cette commande ?
          <a [href]="lien" target="_blank" rel="noopener noreferrer">Écrivez-nous sur WhatsApp</a>
          : le numéro <strong class="mono">{{ c.numero }}</strong> est déjà dans le message.
        </p>
      }
      </div>
    } @else if (erreur()) {
      <p class="auth__erreur" role="alert">{{ erreur() }}</p>
    } @else {
      <div aria-busy="true">
        <bi-squelette forme="ligne" largeur="60%" hauteur="2rem" />
        <bi-squelette forme="bloc" hauteur="6rem" />
        <bi-squelette forme="bloc" hauteur="12rem" />
      </div>
    }
  `,
})
export class DetailCommande implements OnInit {
  /** Numéro dans l'URL (`/compte/commandes/:numero`). */
  readonly numero = input.required<string>();

  private readonly compte = inject(CompteService);
  private readonly site = inject(ParametresSiteService);
  private readonly document = inject(DOCUMENT);
  private readonly seo = inject(SeoService);

  protected readonly libelles = LIBELLES_STATUT;
  protected readonly commande = signal<SuiviCommande | null>(null);
  protected readonly introuvable = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly reprise = signal(false);
  protected readonly erreurReprise = signal<string | null>(null);
  protected readonly erreurDocument = signal<string | null>(null);

  protected readonly etapes = computed<EtapeSuivi[]>(() => {
    const c = this.commande();
    return c ? parcours(c.statut, c.modePaiement, c.modeLivraison) : [];
  });
  /** wa.me avec le numéro de commande déjà dans le message : un toucher ouvre WhatsApp. */
  protected readonly whatsapp = computed(() => {
    const c = this.commande();
    return lienWhatsApp(this.site.parametres()?.whatsapp, c ? `Bonjour, à propos de ma commande ${c.numero}.` : undefined);
  });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Ma commande', indexable: false });
    void this.charger();
  }

  protected dateLisible(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Dakar' });
  }

  protected dateHeure(iso: string | null): string {
    return iso
      ? new Date(iso).toLocaleString('fr-FR', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Dakar' })
      : '';
  }

  private async charger(): Promise<void> {
    try {
      this.commande.set(await this.compte.commande(this.numero()));
    } catch (e) {
      const lue = lireErreur(e);
      if (lue.statut === 404) this.introuvable.set(true);
      else this.erreur.set(lue.message);
    }
  }

  protected async reprendre(numero: string): Promise<void> {
    if (this.reprise()) return;
    this.reprise.set(true);
    this.erreurReprise.set(null);
    try {
      this.document.location.assign(await this.compte.reprendrePaiement(numero));
    } catch (e) {
      this.erreurReprise.set(lireErreur(e).message);
      this.reprise.set(false);
    }
  }

  protected async telecharger(numeroCommande: string, avoir: string | undefined, nomFichier: string): Promise<void> {
    this.erreurDocument.set(null);
    try {
      const pdf = await this.compte.documentPdf(numeroCommande, avoir);
      const url = URL.createObjectURL(pdf);
      const lien = this.document.createElement('a');
      lien.href = url;
      lien.download = `${nomFichier}.pdf`;
      lien.click();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (e) {
      this.erreurDocument.set(lireErreur(e).message);
    }
  }
}
