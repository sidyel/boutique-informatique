import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { Champ } from './champ';

@Component({
  imports: [Champ, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <bi-champ
      libelle="Téléphone"
      type="tel"
      prefixe="+221"
      aide="Pour vous prévenir de la livraison"
      requis
      [erreur]="erreur()"
      [formControl]="telephone"
    />
  `,
})
class HoteEssai {
  readonly telephone = new FormControl('77 123 45 67', { nonNullable: true });
  readonly erreur = signal<string | null>(null);
}

describe('Champ', () => {
  function creer() {
    const fixture = TestBed.createComponent(HoteEssai);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const saisie = element.querySelector('input') as HTMLInputElement;
    return { fixture, element, saisie };
  }

  it('relie le libellé au champ et affiche le préfixe', () => {
    const { element, saisie } = creer();
    const libelle = element.querySelector('label');

    expect(libelle?.getAttribute('for')).toBe(saisie.id);
    expect(libelle?.textContent).toContain('Téléphone');
    expect(element.querySelector('.champ__affixe')?.textContent).toBe('+221');
    expect(saisie.type).toBe('tel');
    expect(saisie.required).toBe(true);
  });

  it('reçoit la valeur du formulaire et lui renvoie la saisie', () => {
    const { fixture, saisie } = creer();
    expect(saisie.value).toBe('77 123 45 67');

    saisie.value = '78 000 00 00';
    saisie.dispatchEvent(new Event('input'));

    expect(fixture.componentInstance.telephone.value).toBe('78 000 00 00');
  });

  it('marque le contrôle comme touché quand on quitte le champ', () => {
    const { fixture, saisie } = creer();

    saisie.dispatchEvent(new Event('blur'));

    expect(fixture.componentInstance.telephone.touched).toBe(true);
  });

  it("affiche l'aide puis l'erreur, reliées au champ par aria-describedby", () => {
    const { fixture, element, saisie } = creer();
    const aide = element.querySelector('.champ__aide');
    expect(saisie.getAttribute('aria-describedby')).toBe(aide?.id);
    expect(saisie.hasAttribute('aria-invalid')).toBe(false);

    fixture.componentInstance.erreur.set('Numéro de téléphone invalide');
    fixture.detectChanges();

    const erreur = element.querySelector('.champ__erreur');
    expect(erreur?.textContent).toBe('Numéro de téléphone invalide');
    expect(saisie.getAttribute('aria-describedby')).toBe(erreur?.id);
    expect(saisie.getAttribute('aria-invalid')).toBe('true');
    expect(element.querySelector('.champ__aide')).toBeNull();
  });

  it('se désactive avec le contrôle de formulaire', () => {
    const { fixture, saisie } = creer();

    fixture.componentInstance.telephone.disable();
    fixture.detectChanges();

    expect(saisie.disabled).toBe(true);
  });
});
