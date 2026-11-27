import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SuiviCommande } from '../../../noyau/api/modeles';
import { SeoService } from '../../../noyau/seo/seo.service';
import { ParametresSiteService } from '../../../noyau/donnees/parametres-site.service';
import { DetailCommande } from './detail-commande';
import { ListeCommandes } from './liste-commandes';

const attendre = () => new Promise((resolve) => setTimeout(resolve));

const SUIVI: SuiviCommande = {
  numero: 'CMD-2026-000123',
  statut: 'EN_ATTENTE_PAIEMENT',
  dateCreation: '2026-11-20T10:00:00Z',
  expireLe: '2026-11-27T23:00:00Z',
  prenomClient: 'Awa',
  telephoneMasque: '+221 77 ••• •• 67',
  modeLivraison: 'DOMICILE',
  zoneLivraison: 'Dakar',
  adresseMasquee: 'Mermoz, Dakar',
  modePaiement: 'MOBILE_MONEY',
  lignes: [{ sku: 'MBA-13', nomProduit: 'MacBook Air 13', libelleVariante: '8 Go', imageUrl: null, prixUnitaire: 900000, quantite: 1, totalLigne: 900000 }],
  sousTotal: 900000,
  fraisLivraison: 2000,
  remise: 0,
  total: 902000,
  codePromo: null,
  etapes: [{ statut: 'EN_ATTENTE_PAIEMENT', date: '2026-11-20T10:00:00Z' }],
  peutReprendrePaiement: true,
  achatASuivre: false,
  remboursementEnCours: false,
  numeroFacture: 'FAC-2026-000007',
  numerosAvoirs: [],
};

function configurer() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: SeoService, useValue: { definirPage: () => undefined } },
      { provide: ParametresSiteService, useValue: { parametres: signal({ whatsapp: '+221770000000' }) } },
    ],
  });
  return TestBed.inject(HttpTestingController);
}

describe('Mes commandes', () => {
  it('liste les commandes avec leur statut en français', async () => {
    const http = configurer();
    const fixture = TestBed.createComponent(ListeCommandes);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/compte/commandes').flush({
      content: [{ numero: 'CMD-2026-000123', statut: 'LIVREE', dateCreation: '2026-11-20T10:00:00Z', total: 902000, nombreArticles: 2, premierArticle: 'MacBook Air 13', imageUrl: null }],
      totalElements: 1, totalPages: 1, number: 0,
    });
    await attendre();
    fixture.detectChanges();
    const texte = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texte).toContain('CMD-2026-000123');
    expect(texte).toContain('MacBook Air 13 et 1 autre');
    expect(texte).toContain('Livrée');
  });

  it('propose de reprendre le paiement d’une commande en attente et le lien WhatsApp avec le numéro', async () => {
    const http = configurer();
    const fixture = TestBed.createComponent(DetailCommande);
    fixture.componentRef.setInput('numero', 'CMD-2026-000123');
    fixture.detectChanges();
    http.expectOne('/api/compte/commandes/CMD-2026-000123').flush(SUIVI);
    await attendre();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Reprendre le paiement');
    expect(el.textContent).toContain('Facture FAC-2026-000007');
    const whatsapp = el.querySelector<HTMLAnchorElement>('a[href^="https://wa.me/"]')!;
    expect(decodeURIComponent(whatsapp.href)).toContain('CMD-2026-000123');
  });

  it('télécharge la facture avec le jeton d’accès (requête authentifiée, pas un lien direct)', async () => {
    const http = configurer();
    URL.createObjectURL = () => 'blob:test';
    URL.revokeObjectURL = () => undefined;
    const fixture = TestBed.createComponent(DetailCommande);
    fixture.componentRef.setInput('numero', 'CMD-2026-000123');
    fixture.detectChanges();
    http.expectOne('/api/compte/commandes/CMD-2026-000123').flush(SUIVI);
    await attendre();
    fixture.detectChanges();
    const bouton = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) => b.textContent?.includes('Facture'))!;
    bouton.click();
    const requete = http.expectOne('/api/compte/commandes/CMD-2026-000123/facture');
    expect(requete.request.responseType).toBe('blob');
    requete.flush(new Blob(['%PDF']));
  });

  it('annonce une commande introuvable (404) sans rien révéler', async () => {
    const http = configurer();
    const fixture = TestBed.createComponent(DetailCommande);
    fixture.componentRef.setInput('numero', 'CMD-2026-999999');
    fixture.detectChanges();
    http.expectOne('/api/compte/commandes/CMD-2026-999999').flush({ detail: 'x' }, { status: 404, statusText: 'Not Found' });
    await attendre();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Commande introuvable');
  });
});
