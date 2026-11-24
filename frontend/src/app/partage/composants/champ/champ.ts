import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type TypeChamp = 'text' | 'email' | 'tel' | 'password' | 'search' | 'number' | 'url';

let compteur = 0;

/**
 * Champ de saisie avec libellé, aide, message d'erreur, préfixe et suffixe.
 * Compatible avec les formulaires Angular (ControlValueAccessor) :
 *
 *   <bi-champ libelle="Téléphone" type="tel" prefixe="+221" autocomplete="tel"
 *             [erreur]="erreurTelephone()" formControlName="telephone" />
 */
@Component({
  selector: 'bi-champ',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Champ), multi: true }],
  host: {
    class: 'champ',
    '[class.champ--erreur]': '!!erreur()',
    '[class.champ--desactive]': 'desactive()',
  },
  template: `
    <label class="champ__libelle" [attr.for]="identifiant">
      {{ libelle() }}
      @if (requis()) {
        <span class="champ__requis" aria-hidden="true">*</span>
      }
    </label>

    <div class="champ__zone">
      @if (prefixe()) {
        <span class="champ__affixe" aria-hidden="true">{{ prefixe() }}</span>
      }
      <input
        class="champ__saisie"
        [id]="identifiant"
        [type]="type()"
        [value]="valeur()"
        [disabled]="desactive()"
        [required]="requis()"
        [attr.name]="nom() || null"
        [attr.placeholder]="indication() || null"
        [attr.autocomplete]="autocomplete() || null"
        [attr.inputmode]="modeSaisie() || null"
        [attr.aria-invalid]="!!erreur() || null"
        [attr.aria-describedby]="descriptions()"
        (input)="saisir($event)"
        (blur)="quitter()"
      />
      @if (suffixe()) {
        <span class="champ__affixe" aria-hidden="true">{{ suffixe() }}</span>
      }
    </div>

    @if (erreur()) {
      <p class="champ__erreur" [id]="identifiant + '-erreur'">{{ erreur() }}</p>
    } @else if (aide()) {
      <p class="champ__aide" [id]="identifiant + '-aide'">{{ aide() }}</p>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--espace-1);
    }

    .champ__libelle {
      font-size: var(--taille-sm);
      font-weight: var(--graisse-semi-gras);
      color: var(--couleur-texte);
    }

    .champ__requis {
      margin-inline-start: var(--espace-1);
      color: var(--couleur-promo-fonce);
    }

    .champ__zone {
      display: flex;
      align-items: stretch;
      min-height: var(--hauteur-controle-md);
      background: var(--couleur-surface);
      border: var(--epaisseur-bordure) solid var(--couleur-bordure-champ);
      border-radius: var(--rayon-2);
      transition: border-color var(--duree-rapide) var(--courbe-standard);

      &:hover {
        border-color: var(--couleur-texte);
      }

      &:focus-within {
        border-color: var(--couleur-focus);
        box-shadow: 0 0 0 1px var(--couleur-focus);
      }
    }

    .champ__saisie {
      flex: 1;
      min-width: 0;
      padding-inline: var(--espace-3);
      font-size: var(--taille-base); /* 16 px : évite le zoom automatique sur iOS */
      background: transparent;
      border: 0;
      outline: none;

      &::placeholder {
        color: var(--couleur-texte-secondaire);
      }
    }

    .champ__affixe {
      display: inline-flex;
      align-items: center;
      padding-inline: var(--espace-3);
      font-family: var(--police-mono);
      font-size: var(--taille-sm);
      color: var(--couleur-texte-secondaire);
      background: var(--couleur-surface-alt);

      &:first-child {
        border-inline-end: var(--epaisseur-bordure) solid var(--couleur-bordure);
        border-start-start-radius: var(--rayon-2);
        border-end-start-radius: var(--rayon-2);
      }

      &:last-child {
        border-inline-start: var(--epaisseur-bordure) solid var(--couleur-bordure);
        border-start-end-radius: var(--rayon-2);
        border-end-end-radius: var(--rayon-2);
      }
    }

    .champ__aide,
    .champ__erreur {
      font-size: var(--taille-sm);
    }

    .champ__aide {
      color: var(--couleur-texte-secondaire);
    }

    .champ__erreur {
      color: var(--couleur-promo-fonce);
      font-weight: var(--graisse-moyenne);
    }

    :host(.champ--erreur) .champ__zone {
      border-color: var(--couleur-danger);
    }

    :host(.champ--desactive) .champ__zone {
      background: var(--couleur-surface-alt);
      border-color: var(--couleur-bordure);
      cursor: not-allowed;
    }

    :host(.champ--desactive) .champ__saisie {
      color: var(--couleur-texte-desactive);
      cursor: not-allowed;
    }
  `,
})
export class Champ implements ControlValueAccessor {
  readonly libelle = input.required<string>();
  readonly type = input<TypeChamp>('text');
  readonly nom = input<string>();
  readonly indication = input<string>();
  readonly aide = input<string>();
  readonly erreur = input<string | null>();
  readonly prefixe = input<string>();
  readonly suffixe = input<string>();
  readonly autocomplete = input<string>();
  readonly modeSaisie = input<string>();
  readonly requis = input(false, { transform: booleanAttribute });

  protected readonly identifiant = `champ-${++compteur}`;
  protected readonly valeur = signal('');
  protected readonly desactive = signal(false);

  protected readonly descriptions = computed(() => {
    if (this.erreur()) {
      return `${this.identifiant}-erreur`;
    }
    return this.aide() ? `${this.identifiant}-aide` : null;
  });

  private notifierChangement: (valeur: string) => void = () => {};
  private notifierToucher: () => void = () => {};

  protected saisir(evenement: Event): void {
    const valeur = (evenement.target as HTMLInputElement).value;
    this.valeur.set(valeur);
    this.notifierChangement(valeur);
  }

  protected quitter(): void {
    this.notifierToucher();
  }

  writeValue(valeur: string | number | null): void {
    this.valeur.set(valeur == null ? '' : String(valeur));
  }

  registerOnChange(fonction: (valeur: string) => void): void {
    this.notifierChangement = fonction;
  }

  registerOnTouched(fonction: () => void): void {
    this.notifierToucher = fonction;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }
}
