import { RESPONSE_INIT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { PageIntrouvable } from './page-introuvable';

describe('PageIntrouvable', () => {
  it('renvoie un vrai statut HTTP 404 pendant le rendu serveur', () => {
    const reponse: ResponseInit = { status: 200 };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: RESPONSE_INIT, useValue: reponse }],
    });

    TestBed.createComponent(PageIntrouvable);

    expect(reponse.status).toBe(404);
  });

  it("n'est pas indexable et a un titre explicite", () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });

    const fixture = TestBed.createComponent(PageIntrouvable);
    fixture.detectChanges();

    expect(TestBed.inject(Title).getTitle()).toBe('Page introuvable | Boutique informatique');
    expect(TestBed.inject(Meta).getTag('name="robots"')?.content).toBe('noindex,follow');
    expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toBe('/');
  });
});
