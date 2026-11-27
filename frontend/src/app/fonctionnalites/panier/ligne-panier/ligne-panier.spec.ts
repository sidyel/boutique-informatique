import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LignePanier as Ligne } from '../../../noyau/api/modeles';
import { sessionDeTest } from '../../../noyau/auth/session.donnees-test';
import { SessionService } from '../../../noyau/auth/session.service';
import { ligne, panier } from '../../../noyau/panier/panier.donnees-test';
import { LignePanier } from './ligne-panier';

describe('LignePanier', () => {
  function afficher(valeurs: Partial<Ligne>) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: sessionDeTest() },
      ],
    });
    const fixture = TestBed.createComponent(LignePanier);
    fixture.componentRef.setInput('ligne', ligne(valeurs));
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const bouton = (libelle: string) =>
      element.querySelector<HTMLButtonElement>(`button[aria-label="${libelle}"]`);
    return { element, bouton, http: TestBed.inject(HttpTestingController) };
  }

  it('annonce clairement un changement de prix', () => {
    const { element } = afficher({ prixUnitaire: 475_000, prixPrecedent: 450_000, totalLigne: 475_000 });
    const texte = element.querySelector('.ligne__alerte')?.textContent?.replace(/\s+/g, ' ').trim();
    expect(texte).toBe('Le prix de cet article est passé de 450 000 FCFA à 475 000 FCFA.');
  });

  it('bornes du sélecteur : pas de « − » à 1, pas de « + » au maximum commandable', () => {
    const { bouton } = afficher({ quantite: 1, quantiteMax: 1 });
    expect(bouton('Diminuer la quantité')?.disabled).toBe(true);
    expect(bouton('Augmenter la quantité')?.disabled).toBe(true);
  });

  it('« + » envoie la nouvelle quantité au serveur', () => {
    const { bouton, http } = afficher({ id: 42, quantite: 2, quantiteMax: 5 });
    bouton('Augmenter la quantité')!.click();
    const requete = http.expectOne('/api/panier/lignes/42');
    expect(requete.request.method).toBe('PATCH');
    expect(requete.request.body).toEqual({ quantite: 3 });
    requete.flush(panier([ligne({ id: 42, quantite: 3 })]));
  });

  it('article indisponible : pas de sélecteur, invitation à le retirer', () => {
    const { element, bouton } = afficher({ probleme: 'INDISPONIBLE', quantiteMax: 0, disponibilite: 'RUPTURE' });
    expect(bouton('Augmenter la quantité')).toBeNull();
    expect(element.textContent).toContain("Cet article n'est plus disponible : retirez-le pour commander.");
    expect(element.querySelector('.ligne__retirer')).not.toBeNull();
  });

  it('quantité devenue trop élevée : propose de passer au maximum', () => {
    const { element, http } = afficher({ id: 7, quantite: 4, quantiteMax: 2, probleme: 'QUANTITE_TROP_ELEVEE' });
    expect(element.textContent).toContain('Vous pouvez en commander 2 au plus.');
    element.querySelector<HTMLButtonElement>('.ligne__lien')!.click();
    const requete = http.expectOne('/api/panier/lignes/7');
    expect(requete.request.body).toEqual({ quantite: 2 });
    requete.flush(panier([ligne({ id: 7, quantite: 2, quantiteMax: 2 })]));
  });
});
