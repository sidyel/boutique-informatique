import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Prix } from './prix';

const lisible = (texte: string | null | undefined) =>
  (texte ?? '').replace(/[  ]/g, ' ').replace(/\s+/g, ' ').trim();

function creer(montant: number, montantBarre?: number | null): ComponentFixture<Prix> {
  const fixture = TestBed.createComponent(Prix);
  fixture.componentRef.setInput('montant', montant);
  if (montantBarre !== undefined) {
    fixture.componentRef.setInput('montantBarre', montantBarre);
  }
  fixture.detectChanges();
  return fixture;
}

describe('Prix', () => {
  it('affiche le prix au format « 450 000 FCFA »', () => {
    const element: HTMLElement = creer(450000).nativeElement;

    expect(lisible(element.querySelector('.prix__actuel')?.textContent)).toBe('450 000 FCFA');
    expect(element.querySelector('.prix__barre')).toBeNull();
    expect(element.querySelector('.prix__reduction')).toBeNull();
  });

  it('affiche le prix barré et la réduction arrondie à l’entier inférieur', () => {
    const fixture = creer(450000, 520000);
    const element: HTMLElement = fixture.nativeElement;

    expect(lisible(element.querySelector('.prix__barre')?.textContent)).toBe(
      'Prix habituel : 520 000 FCFA',
    );
    // (520 000 − 450 000) / 520 000 = 13,46 % → 13 %
    expect(lisible(element.querySelector('.prix__reduction')?.textContent)).toBe('−13 %');
    expect(element.classList).toContain('prix--promo');
  });

  it("n'affiche pas de prix barré s'il n'est pas supérieur au prix actuel", () => {
    const element: HTMLElement = creer(450000, 450000).nativeElement;

    expect(element.querySelector('.prix__barre')).toBeNull();
    expect(element.classList).not.toContain('prix--promo');
  });

  it('annonce le prix promotionnel aux lecteurs d’écran', () => {
    const element: HTMLElement = creer(99000, 120000).nativeElement;

    expect(lisible(element.querySelector('.prix__actuel')?.textContent)).toBe(
      'Prix promotionnel : 99 000 FCFA',
    );
  });
});
