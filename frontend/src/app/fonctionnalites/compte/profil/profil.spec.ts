import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Profil as ProfilClient, SessionService } from '../../../noyau/auth/session.service';
import { Profil } from './profil';

const CLIENT: ProfilClient = {
  id: 1, email: 'awa@exemple.sn', prenom: 'Awa', nom: 'Diop', telephone: '+221771234567', role: 'CLIENT',
  emailVerifie: true, consentementMarketing: true, dateConsentement: '2026-11-01T10:00:00Z', sourceConsentement: 'INSCRIPTION',
};

Element.prototype.scrollIntoView ??= () => undefined;

describe('Profil (espace client)', () => {
  function monter() {
    const utilisateur = signal<ProfilClient | null>(CLIENT);
    const session = { utilisateur, majProfil: (p: ProfilClient) => utilisateur.set(p) };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SessionService, useValue: session }],
    });
    const fixture = TestBed.createComponent(Profil);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), el: fixture.nativeElement as HTMLElement };
  }

  it('retire le consentement en un seul clic, sans mot de passe', async () => {
    const { fixture, http, el } = monter();
    expect(el.textContent).toContain('à l’inscription');
    const bouton = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Ne plus recevoir'))!;
    bouton.click();
    const requete = http.expectOne('/api/compte/consentement-marketing');
    expect(requete.request.body).toEqual({ consentement: false });
    requete.flush({ ...CLIENT, consentementMarketing: false, sourceConsentement: 'COMPTE' });
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(el.textContent).toContain('Vous ne recevez pas nos offres');
    expect(el.textContent).toContain('depuis votre compte');
  });

  it('envoie le téléphone tel que saisi : le serveur le normalise en E.164', async () => {
    const { fixture, http, el } = monter();
    const champ = el.querySelector<HTMLInputElement>('input[name="telephone"]')!;
    champ.value = '77 123 45 67';
    champ.dispatchEvent(new Event('input'));
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const requete = http.expectOne('/api/compte/profil');
    expect(requete.request.body.telephone).toBe('77 123 45 67');
    requete.flush({ ...CLIENT, telephone: '+221771234567' });
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(el.textContent).toContain('Profil enregistré.');
  });
});
