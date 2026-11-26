import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SuiviService } from '../suivi/suivi.service';
import { ligne, panier } from './panier.donnees-test';
import { PanierService } from './panier.service';

describe('PanierService', () => {
  let http: HttpTestingController;
  let suivi: { ajouterAuPanier: ReturnType<typeof vi.fn> };

  function creer(plateforme: 'browser' | 'server' = 'browser'): PanierService {
    suivi = { ajouterAuPanier: vi.fn() };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PLATFORM_ID, useValue: plateforme },
        { provide: SuiviService, useValue: suivi },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(PanierService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => http.verify());

  it("n'envoie que la variante et la quantité ; le suivi reçoit le prix et la quantité retenus par le serveur", async () => {
    const service = creer();
    const ajout = service.ajouter(10, 3);
    const requete = http.expectOne('/api/panier/lignes');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ varianteId: 10, quantite: 3 });
    // Stock : 2 seulement ; prix actuel 445 000
    requete.flush(panier([ligne({ quantite: 2, prixUnitaire: 445_000, totalLigne: 890_000 })], {
      avertissements: ['Quantité de « TUF Gaming A15 » ramenée à 2 (stock disponible).'],
    }));

    expect(await ajout).toBe(true);
    expect(service.nombreArticles()).toBe(2);
    expect(service.tiroirOuvert()).toBe(true);
    expect(suivi.ajouterAuPanier).toHaveBeenCalledWith('ASUS-TUF-A15-16-512', 445_000, 2);
    expect(localStorage.getItem('panier_actif')).toBe('1');
  });

  it("n'envoie pas d'événement de suivi quand rien n'a été ajouté", async () => {
    const service = creer();
    service.panier.set(panier([ligne({ quantite: 5, quantiteMax: 5 })]));
    const ajout = service.ajouter(10);
    http.expectOne('/api/panier/lignes').flush(
      { status: 422, code: 'QUANTITE_MAXIMALE', detail: '« TUF Gaming A15 » est limité à 5 par commande.' },
      { status: 422, statusText: 'Unprocessable Content' },
    );

    expect(await ajout).toBe(false);
    expect(service.erreur()).toBe('« TUF Gaming A15 » est limité à 5 par commande.');
    expect(suivi.ajouterAuPanier).not.toHaveBeenCalled();
    expect(service.tiroirOuvert()).toBe(false);
  });

  it('stock insuffisant (409) : affiche le message et relit le panier', async () => {
    const service = creer();
    const modification = service.modifierQuantite(1, 4);
    http.expectOne('/api/panier/lignes/1').flush(
      { status: 409, code: 'STOCK_INSUFFISANT', detail: 'Quantité indisponible : vous pouvez en commander 2 au plus.' },
      { status: 409, statusText: 'Conflict' },
    );
    await modification;

    expect(service.erreur()).toContain('2 au plus');
    http.expectOne('/api/panier').flush(panier([ligne({ quantite: 2 })]));
  });

  it("une erreur serveur ou réseau donne un message générique, jamais le détail technique", async () => {
    const service = creer();
    const retrait = service.supprimer(1);
    http.expectOne('/api/panier/lignes/1').flush({ detail: 'NullPointerException' }, { status: 500, statusText: 'Error' });
    await retrait;
    expect(service.erreur()).toBe('Impossible de mettre à jour le panier. Vérifiez votre connexion et réessayez.');
  });

  it('transmet la zone choisie et la garde pour la prochaine visite', async () => {
    const service = creer();
    service.panier.set(panier([ligne()]));
    const choix = service.choisirZone(7);
    http.expectOne('/api/panier?zone=7').flush(panier([ligne()], { fraisLivraison: 2_000, total: 452_000 }));
    await choix;

    expect(service.panier()?.total).toBe(452_000);
    expect(localStorage.getItem('zone_livraison')).toBe('7');

    const autre = creer();
    localStorage.setItem('panier_actif', '1');
    autre.initialiser();
    http.expectOne('/api/panier?zone=7').flush(panier([ligne()]));
  });

  it("au démarrage, ne charge le panier que si ce navigateur en a un", async () => {
    creer().initialiser();
    http.expectNone('/api/panier');

    localStorage.setItem('panier_actif', '1');
    const service = creer();
    service.initialiser();
    http.expectOne('/api/panier').flush(panier([]));
    await vi.waitFor(() => expect(service.panier()).not.toBeNull());
    // Panier vidé (dans un autre onglet, ou purgé) : plus de chargement aux prochaines visites
    expect(localStorage.getItem('panier_actif')).toBeNull();
  });

  it("n'appelle jamais l'API pendant le rendu serveur", async () => {
    localStorage.setItem('panier_actif', '1');
    const service = creer('server');
    service.initialiser();
    await service.charger();
    http.expectNone(() => true);
  });

  it('ignore un second envoi tant que le premier est en cours', async () => {
    const service = creer();
    const premier = service.modifierQuantite(1, 2);
    const second = service.modifierQuantite(1, 3);
    http.expectOne('/api/panier/lignes/1').flush(panier([ligne({ quantite: 2 })]));
    await Promise.all([premier, second]);
    expect(service.panier()?.lignes[0].quantite).toBe(2);
  });
});
