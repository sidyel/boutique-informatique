import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  it('affiche un lien d’évitement vers le contenu principal (accessibilité clavier)', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('a.lien-evitement')?.getAttribute('href')).toBe('#contenu');
    expect(element.querySelector('main#contenu router-outlet')).not.toBeNull();
  });
});
