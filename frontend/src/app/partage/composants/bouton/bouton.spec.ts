import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Bouton } from './bouton';

@Component({
  imports: [Bouton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button biBouton variante="contour" taille="lg" [chargement]="chargement()" (click)="clics = clics + 1">
      Ajouter au panier
    </button>
  `,
})
class HoteEssai {
  readonly chargement = signal(false);
  clics = 0;
}

describe('Bouton', () => {
  function creer() {
    const fixture = TestBed.createComponent(HoteEssai);
    fixture.detectChanges();
    const bouton: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    return { fixture, bouton };
  }

  it('applique la variante et la taille sur le vrai élément <button>', () => {
    const { bouton } = creer();

    expect(bouton.classList).toContain('bouton');
    expect(bouton.classList).toContain('bouton--contour');
    expect(bouton.classList).toContain('bouton--lg');
    expect(bouton.textContent?.trim()).toBe('Ajouter au panier');
  });

  it('transmet les clics hors chargement', () => {
    const { fixture, bouton } = creer();

    bouton.click();

    expect(fixture.componentInstance.clics).toBe(1);
  });

  it('bloque les clics et signale le chargement (pas de double commande)', () => {
    const { fixture, bouton } = creer();
    fixture.componentInstance.chargement.set(true);
    fixture.detectChanges();

    bouton.click();

    expect(fixture.componentInstance.clics).toBe(0);
    expect(bouton.getAttribute('aria-busy')).toBe('true');
    expect(bouton.querySelector('.bouton__indicateur')).not.toBeNull();
  });
});
