import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CommandeCreee, CreationCommande } from '../api/modeles';
import { CommandeService, ErreurCommande, MESSAGE_RESEAU, traduireErreur } from './commande.service';

const DEMANDE: CreationCommande = {
  nomComplet: 'Awa Diop',
  telephone: '771234567',
  email: null,
  modeLivraison: 'DOMICILE',
  zoneId: 1,
  ville: 'Dakar',
  quartier: 'Médina',
  adresse: 'Rue 10',
  pointDeRepere: null,
  telephoneLivraison: null,
  modePaiement: 'A_LA_LIVRAISON',
  totalAttendu: 12_000,
  attribution: null,
};

const CREEE: CommandeCreee = {
  numero: 'CMD-2026-000001',
  jetonSuivi: 'a'.repeat(43),
  statut: 'EN_ATTENTE_CONFIRMATION',
  modePaiement: 'A_LA_LIVRAISON',
  total: 12_000,
  expireLe: null,
  urlPaiement: null,
  paiementAReprendre: false,
  rejouee: false,
};

describe('CommandeService', () => {
  let http: HttpTestingController;

  function creer(plateforme: 'browser' | 'server' = 'browser'): CommandeService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: PLATFORM_ID, useValue: plateforme }],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(CommandeService);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it('garde la même clé d’idempotence d’un essai à l’autre, jusqu’au succès', async () => {
    const service = creer();
    const premiere = service.commander(DEMANDE);
    const requete1 = http.expectOne('/api/commandes');
    const cle = requete1.request.headers.get('Idempotency-Key');
    expect(cle).toMatch(/^[A-Za-z0-9_-]{16,64}$/);
    requete1.error(new ProgressEvent('error'), { status: 0 });
    await expect(premiere).rejects.toMatchObject({ code: 'RESEAU', reessayable: true, message: MESSAGE_RESEAU });

    // Nouvel appui : même clé, même commande côté serveur
    const seconde = service.commander(DEMANDE);
    const requete2 = http.expectOne('/api/commandes');
    expect(requete2.request.headers.get('Idempotency-Key')).toBe(cle);
    requete2.flush({ ...CREEE, rejouee: true });
    expect((await seconde).rejouee).toBe(true);

    // Après le succès, la commande suivante aura sa propre clé
    const suivante = service.commander(DEMANDE);
    const requete3 = http.expectOne('/api/commandes');
    expect(requete3.request.headers.get('Idempotency-Key')).not.toBe(cle);
    requete3.flush(CREEE);
    await suivante;
  });

  it('survit à un rechargement de la page : la clé est dans sessionStorage', () => {
    const cle = creer().cleIdempotence();
    expect(creer().cleIdempotence()).toBe(cle);
  });

  it('ignore une clé invalide trouvée dans le stockage', () => {
    sessionStorage.setItem('cle_commande', 'x');
    expect(creer().cleIdempotence()).not.toBe('x');
  });

  it('oublie la clé quand le serveur dit qu’elle a servi pour une autre demande', async () => {
    const service = creer();
    const ancienne = service.cleIdempotence();
    const envoi = service.commander(DEMANDE);
    http.expectOne('/api/commandes').flush(
      { code: 'CLE_IDEMPOTENCE_REUTILISEE', detail: 'Cette demande a déjà servi.' },
      { status: 409, statusText: 'Conflict' },
    );
    await expect(envoi).rejects.toMatchObject({ code: 'CLE_IDEMPOTENCE_REUTILISEE' });
    expect(service.cleIdempotence()).not.toBe(ancienne);
  });

  it('une commande refusée garde sa clé : rien n’a été enregistré, le réessai est sans risque', async () => {
    const service = creer();
    const cle = service.cleIdempotence();
    const envoi = service.commander(DEMANDE);
    http.expectOne('/api/commandes').flush(
      { code: 'TOTAL_MODIFIE', detail: 'Le total a changé : 13 000 FCFA.' },
      { status: 409, statusText: 'Conflict' },
    );
    await expect(envoi).rejects.toMatchObject({ code: 'TOTAL_MODIFIE', panierARevoir: true, reessayable: false });
    expect(service.cleIdempotence()).toBe(cle);
  });

  it('ne touche pas au stockage pendant le rendu serveur', () => {
    const service = creer('server');
    service.cleIdempotence();
    expect(sessionStorage.getItem('cle_commande')).toBeNull();
  });
});

describe('traduireErreur', () => {
  const erreur = (status: number, corps: unknown): ErreurCommande =>
    traduireErreur(new HttpErrorResponse({ status, error: corps }));

  it('affiche le message du serveur pour une erreur du client, jamais un détail technique', () => {
    expect(erreur(422, { code: 'PAIEMENT_LIVRAISON_MONTANT_MAX', detail: 'Limité à 500 000 FCFA.' })).toMatchObject({
      message: 'Limité à 500 000 FCFA.',
      reessayable: false,
      panierARevoir: false,
    });
    expect(erreur(500, { code: 'ERREUR_INTERNE', detail: 'NullPointerException' }).message).not.toContain('Null');
  });

  it('coupure, délai dépassé et passerelle indisponible : message de réseau, essai possible', () => {
    for (const statut of [0, 502, 504]) {
      expect(erreur(statut, null)).toMatchObject({ code: 'RESEAU', message: MESSAGE_RESEAU, reessayable: true });
    }
  });

  it('prestataire de paiement indisponible : message du serveur, essai possible', () => {
    expect(erreur(503, { code: 'PAIEMENT_INDISPONIBLE', detail: 'Paiement momentanément indisponible.' }))
      .toMatchObject({ code: 'PAIEMENT_INDISPONIBLE', message: 'Paiement momentanément indisponible.', reessayable: true });
  });

  it('trop de requêtes : message clair', () => {
    expect(erreur(429, { code: 'TROP_DE_REQUETES' }).message).toContain('Patientez');
  });

  it('les codes du panier invitent à le revoir', () => {
    for (const code of ['TOTAL_MODIFIE', 'PRIX_MODIFIES', 'STOCK_INSUFFISANT', 'CODE_PROMO_EXPIRE', 'PANIER_VIDE']) {
      expect(erreur(409, { code, detail: 'x' }).panierARevoir).toBe(true);
    }
  });
});
