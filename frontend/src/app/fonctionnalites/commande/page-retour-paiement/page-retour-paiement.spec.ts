import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PageRetourPaiement } from './page-retour-paiement';

const JETON = 'r'.repeat(43);
const URL = `/api/commandes/retour/${JETON}`;

describe('PageRetourPaiement', () => {
  let http: HttpTestingController;

  function monter(annule?: string) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PageRetourPaiement);
    fixture.componentRef.setInput('jeton', JETON);
    if (annule) fixture.componentRef.setInput('annule', annule);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    // Le service SEO demande les paramètres du site : sans objet ici
    http.match('/api/parametres');
    http.verify();
  });

  const tick = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

  async function repondre(fixture: ReturnType<typeof monter>, corps: object | null, statut = 200) {
    // La requête part après le premier rendu (afterNextRender) : on laisse passer les micro-tâches
    await tick();
    const requete = http.expectOne(URL);
    if (corps === null) requete.flush({}, { status: statut, statusText: 'Erreur' });
    else requete.flush(corps);
    await tick();
    fixture.detectChanges();
  }

  it('affiche le paiement reçu seulement quand le serveur le confirme', async () => {
    const fixture = monter();
    await repondre(fixture, { numero: 'CMD-2026-000001', statut: 'PAYE', expireLe: null, verifie: true });

    expect(fixture.nativeElement.textContent).toContain('Paiement reçu, merci');
  });

  it('un paiement annulé ou échoué est présenté sans attente', async () => {
    const fixture = monter('1');
    await repondre(fixture, { numero: 'CMD-2026-000001', statut: 'ECHEC', expireLe: null, verifie: true });

    expect(fixture.nativeElement.textContent).toContain('Le paiement n’a pas abouti');
  });

  it('annonce un remboursement quand la commande ne peut pas être honorée', async () => {
    const fixture = monter();
    await repondre(fixture, { numero: 'CMD-2026-000001', statut: 'A_REMBOURSER', expireLe: null, verifie: true });

    expect(fixture.nativeElement.textContent).toContain('va être remboursé');
  });

  it('propose le lien de suivi gardé par cet onglet seulement pour la même commande', async () => {
    sessionStorage.setItem('suivi_dernier', JSON.stringify({ numero: 'CMD-2026-000001', jeton: 'j'.repeat(43) }));
    const fixture = monter();
    await repondre(fixture, { numero: 'CMD-2026-000001', statut: 'PAYE', expireLe: null, verifie: true });

    expect(fixture.nativeElement.querySelector('a[href^="/suivi/"]')).not.toBeNull();
  });

  it("n'affiche pas le lien de suivi d'une autre commande", async () => {
    sessionStorage.setItem('suivi_dernier', JSON.stringify({ numero: 'CMD-2026-000099', jeton: 'j'.repeat(43) }));
    const fixture = monter();
    await repondre(fixture, { numero: 'CMD-2026-000001', statut: 'PAYE', expireLe: null, verifie: true });

    expect(fixture.nativeElement.querySelector('a[href^="/suivi/"]')).toBeNull();
  });

  it('un jeton inconnu affiche « lien introuvable »', async () => {
    const fixture = monter();
    await repondre(fixture, null, 404);

    expect(fixture.nativeElement.textContent).toContain('Lien de retour introuvable');
  });
});
