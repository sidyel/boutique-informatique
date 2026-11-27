import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { lireErreur } from '../../../noyau/auth/erreurs-api';
import { focaliserPremiereErreur } from '../../../noyau/auth/focus-erreur';
import { SessionService } from '../../../noyau/auth/session.service';
import { Adresse, AdresseSaisie, CompteService } from '../../../noyau/compte/compte.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { Bouton } from '../../../partage/composants/bouton/bouton';
import { Champ } from '../../../partage/composants/champ/champ';

type ChampAdresse = 'libelle' | 'nomComplet' | 'telephone' | 'ville' | 'quartier' | 'adresseLigne' | 'pointDeRepere';

const OBLIGATOIRES: Partial<Record<ChampAdresse, string>> = {
  libelle: 'Donnez un nom à cette adresse, par exemple « Maison ».',
  nomComplet: 'Saisissez le nom du destinataire.',
  telephone: 'Saisissez un numéro joignable pour le livreur.',
  ville: 'Saisissez la ville.',
  quartier: 'Saisissez le quartier : il nous sert à calculer la livraison.',
};

/** Carnet d'adresses : le quartier et le point de repère comptent plus que le nom de rue. */
@Component({
  selector: 'bi-adresses',
  imports: [ReactiveFormsModule, Bouton, Champ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../espace.scss',
  template: `
    <h1 class="espace__titre">Adresses</h1>
    <p class="auth__intro">Jusqu’à 10 adresses. L’adresse par défaut est proposée en premier à la commande.</p>

    @if (erreurListe()) {
      <p class="auth__erreur" role="alert">{{ erreurListe() }}</p>
    }
    @for (a of adresses(); track a.id) {
      <article class="carte-adresse" [class.carte-adresse--defaut]="a.parDefaut">
        @if (a.parDefaut) {
          <span class="mention">Par défaut</span>
        }
        <h3>{{ a.libelle }}</h3>
        <p>{{ a.nomComplet }} · {{ a.telephone }}</p>
        <p>{{ a.quartier }}, {{ a.ville }}@if (a.adresseLigne) { · {{ a.adresseLigne }} }</p>
        @if (a.pointDeRepere) {
          <p>Repère : {{ a.pointDeRepere }}</p>
        }
        <div class="actions">
          <button biBouton variante="contour" taille="sm" type="button" (click)="modifier(a)">Modifier</button>
          @if (!a.parDefaut) {
            <button biBouton variante="contour" taille="sm" type="button" (click)="definirParDefaut(a)">
              Définir par défaut
            </button>
          }
          @if (aSupprimer() === a.id) {
            <button biBouton variante="danger" taille="sm" type="button" (click)="supprimer(a)">Confirmer la suppression</button>
            <button biBouton variante="fantome" taille="sm" type="button" (click)="aSupprimer.set(null)">Annuler</button>
          } @else {
            <button biBouton variante="fantome" taille="sm" type="button" (click)="aSupprimer.set(a.id)">Supprimer</button>
          }
        </div>
      </article>
    } @empty {
      @if (chargee()) {
        <p class="espace__aide">Aucune adresse enregistrée.</p>
      }
    }

    @if (formulaireOuvert()) {
      <section class="espace__section" aria-labelledby="titre-formulaire">
        <h2 id="titre-formulaire">{{ enEdition() ? 'Modifier l’adresse' : 'Nouvelle adresse' }}</h2>
        <form class="auth__formulaire" [formGroup]="formulaire" (ngSubmit)="enregistrer()" novalidate>
          <bi-champ libelle="Nom de l’adresse" nom="libelle" indication="Maison, Bureau…" autocomplete="off" requis
                    formControlName="libelle" [erreur]="erreurChamp('libelle')" />
          <bi-champ libelle="Nom du destinataire" nom="nomComplet" autocomplete="name" requis
                    formControlName="nomComplet" [erreur]="erreurChamp('nomComplet')" />
          <bi-champ libelle="Téléphone" type="tel" nom="telephone" autocomplete="tel" modeSaisie="tel" requis
                    formControlName="telephone" [erreur]="erreurChamp('telephone')" />
          <bi-champ libelle="Ville" nom="ville" autocomplete="address-level2" requis formControlName="ville"
                    [erreur]="erreurChamp('ville')" />
          <bi-champ libelle="Quartier" nom="quartier" autocomplete="address-level3" requis formControlName="quartier"
                    [erreur]="erreurChamp('quartier')" />
          <bi-champ libelle="Rue ou adresse (facultatif)" nom="adresseLigne" autocomplete="street-address"
                    formControlName="adresseLigne" [erreur]="erreurChamp('adresseLigne')" />
          <bi-champ libelle="Point de repère (facultatif)" nom="pointDeRepere" autocomplete="off"
                    indication="En face de la pharmacie, près de la mosquée…" formControlName="pointDeRepere"
                    [erreur]="erreurChamp('pointDeRepere')" />
          <label class="auth__case">
            <input type="checkbox" formControlName="parDefaut" />
            <span>Utiliser comme adresse par défaut</span>
          </label>
          @if (erreur()) {
            <p class="auth__erreur" role="alert">{{ erreur() }}</p>
          }
          <div class="espace__rangee">
            <button biBouton type="submit" [chargement]="envoi()">Enregistrer l’adresse</button>
            <button biBouton variante="fantome" type="button" (click)="fermer()">Annuler</button>
          </div>
        </form>
      </section>
    } @else if (chargee()) {
      <button biBouton type="button" (click)="ajouter()">Ajouter une adresse</button>
    }
  `,
})
export class Adresses implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly compte = inject(CompteService);
  private readonly seo = inject(SeoService);
  private readonly racine = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  private readonly session = inject(SessionService);

  protected readonly adresses = signal<Adresse[]>([]);
  protected readonly chargee = signal(false);
  protected readonly formulaireOuvert = signal(false);
  protected readonly enEdition = signal<number | null>(null);
  protected readonly aSupprimer = signal<number | null>(null);
  protected readonly envoi = signal(false);
  protected readonly soumis = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurListe = signal<string | null>(null);
  protected readonly erreursServeur = signal<Record<string, string>>({});

  protected readonly formulaire = this.fb.nonNullable.group({
    libelle: ['', [Validators.required, Validators.maxLength(40)]],
    nomComplet: ['', [Validators.required, Validators.maxLength(120)]],
    telephone: ['', Validators.required],
    ville: ['Dakar', [Validators.required, Validators.maxLength(80)]],
    quartier: ['', [Validators.required, Validators.maxLength(80)]],
    adresseLigne: [''],
    pointDeRepere: [''],
    parDefaut: [false],
  });

  ngOnInit(): void {
    this.seo.definirPage({ titre: 'Mes adresses', indexable: false });
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.adresses.set(await this.compte.adresses());
    } catch (e) {
      this.erreurListe.set(lireErreur(e).message);
    } finally {
      this.chargee.set(true);
    }
  }

  protected erreurChamp(nom: ChampAdresse): string | null {
    const serveur = this.erreursServeur()[nom];
    if (serveur) return serveur;
    const obligatoire = OBLIGATOIRES[nom];
    return this.soumis() && obligatoire && this.formulaire.controls[nom].invalid ? obligatoire : null;
  }

  protected ajouter(): void {
    const u = this.session.utilisateur();
    this.formulaire.reset({
      libelle: '', nomComplet: u ? `${u.prenom} ${u.nom}` : '', telephone: u?.telephone ?? '', ville: 'Dakar',
      quartier: '', adresseLigne: '', pointDeRepere: '', parDefaut: this.adresses().length === 0,
    });
    this.enEdition.set(null);
    this.ouvrir();
  }

  protected modifier(a: Adresse): void {
    this.formulaire.reset({
      libelle: a.libelle, nomComplet: a.nomComplet, telephone: a.telephone, ville: a.ville, quartier: a.quartier,
      adresseLigne: a.adresseLigne ?? '', pointDeRepere: a.pointDeRepere ?? '', parDefaut: a.parDefaut,
    });
    this.enEdition.set(a.id);
    this.ouvrir();
  }

  private ouvrir(): void {
    this.soumis.set(false);
    this.erreur.set(null);
    this.erreursServeur.set({});
    this.formulaireOuvert.set(true);
  }

  protected fermer(): void {
    this.formulaireOuvert.set(false);
    this.enEdition.set(null);
  }

  protected async enregistrer(): Promise<void> {
    this.soumis.set(true);
    this.erreur.set(null);
    this.erreursServeur.set({});
    if (this.formulaire.invalid) {
      focaliserPremiereErreur(this.racine, this.injector);
      return;
    }
    if (this.envoi()) return;
    this.envoi.set(true);
    try {
      const v = this.formulaire.getRawValue();
      const saisie: AdresseSaisie = {
        ...v,
        adresseLigne: v.adresseLigne.trim() || null,
        pointDeRepere: v.pointDeRepere.trim() || null,
      };
      const id = this.enEdition();
      if (id === null) await this.compte.creerAdresse(saisie);
      else await this.compte.modifierAdresse(id, saisie);
      this.fermer();
      await this.charger();
    } catch (e) {
      const lue = lireErreur(e);
      if (Object.keys(lue.champs).length > 0) {
        this.erreursServeur.set(lue.champs);
        focaliserPremiereErreur(this.racine, this.injector);
      } else {
        this.erreur.set(lue.message);
      }
    } finally {
      this.envoi.set(false);
    }
  }

  protected async definirParDefaut(a: Adresse): Promise<void> {
    await this.agir(() => this.compte.adresseParDefaut(a.id));
  }

  protected async supprimer(a: Adresse): Promise<void> {
    this.aSupprimer.set(null);
    await this.agir(() => this.compte.supprimerAdresse(a.id));
  }

  private async agir(action: () => Promise<unknown>): Promise<void> {
    this.erreurListe.set(null);
    try {
      await action();
      await this.charger();
    } catch (e) {
      this.erreurListe.set(lireErreur(e).message);
    }
  }
}
