import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SessionService } from '../../../noyau/auth/session.service';
import { sessionDeTest } from '../../../noyau/auth/session.donnees-test';
import { PanierService } from '../../../noyau/panier/panier.service';
import { SeoService } from '../../../noyau/seo/seo.service';
import { ListeAvis } from '../avis/liste-avis';
import { MesDonnees } from './mes-donnees';
import { SuppressionCompte } from './suppression-compte';

const attendre = () => new Promise((resolve) => setTimeout(resolve));

function configurer() {
  const session = { ...sessionDeTest(true), oublierLocalement: vi.fn() };
  const panier = { reinitialiserLocalement: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: SessionService, useValue: session },
      { provide: PanierService, useValue: panier },
      { provide: SeoService, useValue: { definirPage: () => undefined } },
    ],
  });
  return { http: TestBed.inject(HttpTestingController), session, panier };
}

function saisir(el: HTMLElement, nom: string, valeur: string): void {
  const champ = el.querySelector<HTMLInputElement>(`input[name="${nom}"]`)!;
  champ.value = valeur;
  champ.dispatchEvent(new Event('input'));
}

describe('Télécharger mes données', () => {
  it('demande le mot de passe puis télécharge un fichier JSON', async () => {
    const { http } = configurer();
    URL.createObjectURL = vi.fn(() => 'blob:test');
    URL.revokeObjectURL = vi.fn();
    const fixture = TestBed.createComponent(MesDonnees);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    // Sans mot de passe : rien n'est envoyé
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    http.expectNone('/api/compte/mes-donnees');
    expect(el.textContent).toContain('Saisissez votre mot de passe.');

    saisir(el, 'motDePasseDonnees', 'Thieboudienne-2026');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    const requete = http.expectOne('/api/compte/mes-donnees');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ motDePasse: 'Thieboudienne-2026' });
    requete.flush({ profil: { email: 'awa@exemple.sn' } });
    await attendre();
    fixture.detectChanges();
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(el.textContent).toContain('mes-donnees-boutique.json');
  });

  it('signale un mot de passe incorrect sur le champ', async () => {
    const { http } = configurer();
    const fixture = TestBed.createComponent(MesDonnees);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    saisir(el, 'motDePasseDonnees', 'faux-faux-faux-1');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/compte/mes-donnees').flush(
      { code: 'MOT_DE_PASSE_ACTUEL_INCORRECT', detail: 'x' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await attendre();
    fixture.detectChanges();
    expect(el.textContent).toContain('Le mot de passe est incorrect.');
  });
});

describe('Supprimer mon compte', () => {
  it('exige le mot SUPPRIMER avant d’envoyer quoi que ce soit', () => {
    const { http } = configurer();
    const fixture = TestBed.createComponent(SuppressionCompte);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    saisir(el, 'motDePasseSuppression', 'Thieboudienne-2026');
    saisir(el, 'confirmationSuppression', 'oui');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    http.expectNone('/api/compte/suppression');
    expect(el.textContent).toContain('Saisissez le mot SUPPRIMER pour confirmer.');
  });

  it('supprime le compte, oublie la session et le panier puis affiche la page de confirmation', async () => {
    const { http, session, panier } = configurer();
    const aller = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SuppressionCompte);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    saisir(el, 'motDePasseSuppression', 'Thieboudienne-2026');
    saisir(el, 'confirmationSuppression', ' supprimer ');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    const requete = http.expectOne('/api/compte/suppression');
    expect(requete.request.body).toEqual({ motDePasse: 'Thieboudienne-2026', confirmation: 'supprimer' });
    requete.flush({ message: 'ok' });
    await attendre();
    expect(session.oublierLocalement).toHaveBeenCalled();
    expect(panier.reinitialiserLocalement).toHaveBeenCalled();
    expect(aller).toHaveBeenCalledWith('/compte-supprime');
  });

  it('explique le refus quand une commande est en cours, avec un lien vers les commandes, et garde la session', async () => {
    const { http, session } = configurer();
    const fixture = TestBed.createComponent(SuppressionCompte);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    saisir(el, 'motDePasseSuppression', 'Thieboudienne-2026');
    saisir(el, 'confirmationSuppression', 'SUPPRIMER');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/compte/suppression').flush(
      { code: 'COMMANDE_EN_COURS', detail: 'Votre compte ne peut pas être supprimé tant que ce n’est pas terminé pour : CMD-2026-000001.' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await attendre();
    fixture.detectChanges();
    expect(el.textContent).toContain('CMD-2026-000001');
    expect(el.querySelector('a[href="/compte/commandes"]')).not.toBeNull();
    expect(session.oublierLocalement).not.toHaveBeenCalled();
  });
});

describe('Mes avis', () => {
  it('annonce l’absence d’avis puis liste les avis du client avec leur état', async () => {
    const { http } = configurer();
    let fixture = TestBed.createComponent(ListeAvis);
    fixture.detectChanges();
    http.expectOne('/api/compte/avis').flush([]);
    await attendre();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun avis pour le moment');

    fixture = TestBed.createComponent(ListeAvis);
    fixture.detectChanges();
    http.expectOne('/api/compte/avis').flush([
      { id: 1, produit: 'MacBook Air 13', slugProduit: 'macbook-air-13', note: 5, titre: 'Très bien', commentaire: 'Rapide',
        statut: 'EN_ATTENTE', achatVerifie: true, date: '2026-11-20T10:00:00Z', reponseBoutique: null },
    ]);
    await attendre();
    fixture.detectChanges();
    const texte = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texte).toContain('MacBook Air 13');
    expect(texte).toContain('En cours de modération');
    expect(texte).toContain('Achat vérifié');
  });
});
