import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SessionService } from '../../../noyau/auth/session.service';
import { sessionDeTest } from '../../../noyau/auth/session.donnees-test';
import { Securite } from './securite';

Element.prototype.scrollIntoView ??= () => undefined;

describe('Securite', () => {
  function monter() {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: sessionDeTest(true) },
      ],
    });
    const fixture = TestBed.createComponent(Securite);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), el: fixture.nativeElement as HTMLElement };
  }

  function saisir(el: HTMLElement, nom: string, valeur: string): void {
    const champ = el.querySelector<HTMLInputElement>(`input[name="${nom}"]`)!;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
  }

  it('utilise les bons attributs autocomplete pour les gestionnaires de mots de passe', () => {
    const { el } = monter();
    expect(el.querySelector('input[name="motDePasseActuel"]')?.getAttribute('autocomplete')).toBe('current-password');
    expect(el.querySelector('input[name="nouveauMotDePasse"]')?.getAttribute('autocomplete')).toBe('new-password');
    expect(el.querySelector('input[name="nouvelEmail"]')?.getAttribute('autocomplete')).toBe('email');
  });

  it('n’envoie rien et signale les champs vides', () => {
    const { fixture, http, el } = monter();
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    http.expectNone('/api/compte/mot-de-passe');
    expect(el.textContent).toContain('Saisissez votre mot de passe actuel.');
  });

  it('affiche l’erreur sur le mot de passe actuel quand le serveur le refuse', async () => {
    const { fixture, http, el } = monter();
    saisir(el, 'motDePasseActuel', 'faux-faux-faux-1');
    saisir(el, 'nouveauMotDePasse', 'Mafe-Tiga-Diga-2026');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const requete = http.expectOne('/api/compte/mot-de-passe');
    expect(requete.request.body).toEqual({ motDePasseActuel: 'faux-faux-faux-1', nouveauMotDePasse: 'Mafe-Tiga-Diga-2026' });
    requete.flush(
      { code: 'MOT_DE_PASSE_ACTUEL_INCORRECT', detail: 'Le mot de passe actuel est incorrect.' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(el.textContent).toContain('Le mot de passe actuel est incorrect.');
  });

  it('confirme le changement d’e-mail sans le faire avant le clic sur le lien', async () => {
    const { fixture, http, el } = monter();
    saisir(el, 'nouvelEmail', 'nouvelle@exemple.sn');
    saisir(el, 'motDePasse', 'Thieboudienne-2026');
    el.querySelectorAll<HTMLFormElement>('form')[1].dispatchEvent(new Event('submit'));
    const requete = http.expectOne('/api/compte/email');
    expect(requete.request.body).toEqual({ motDePasse: 'Thieboudienne-2026', nouvelEmail: 'nouvelle@exemple.sn' });
    requete.flush({ message: 'Si cette adresse peut être utilisée, un e-mail de confirmation vient de lui être envoyé.' });
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(el.textContent).toContain('un e-mail de confirmation vient de lui être envoyé');
  });
});
