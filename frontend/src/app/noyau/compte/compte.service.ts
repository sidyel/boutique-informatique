import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Profil, SessionService } from '../auth/session.service';

/** Adresse du carnet (miroir de `AdresseReponse`). */
export interface Adresse {
  id: number;
  libelle: string;
  nomComplet: string;
  telephone: string;
  ville: string;
  quartier: string;
  adresseLigne: string | null;
  pointDeRepere: string | null;
  parDefaut: boolean;
}

export type AdresseSaisie = Omit<Adresse, 'id' | 'adresseLigne' | 'pointDeRepere'> & {
  adresseLigne: string | null;
  pointDeRepere: string | null;
};

interface Message {
  message: string;
}

/** Appels de l'espace client. Le jeton d'accès est ajouté par l'intercepteur. */
@Injectable({ providedIn: 'root' })
export class CompteService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);

  async modifierProfil(prenom: string, nom: string, telephone: string): Promise<Profil> {
    const profil = await firstValueFrom(this.http.put<Profil>('/api/compte/profil', { prenom, nom, telephone }));
    this.session.majProfil(profil);
    return profil;
  }

  /** Accepter et retirer sont le même appel : le retrait est aussi simple que l'acceptation. */
  async definirConsentement(consentement: boolean): Promise<Profil> {
    const profil = await firstValueFrom(this.http.put<Profil>('/api/compte/consentement-marketing', { consentement }));
    this.session.majProfil(profil);
    return profil;
  }

  async changerMotDePasse(motDePasseActuel: string, nouveauMotDePasse: string): Promise<string> {
    const r = await firstValueFrom(
      this.http.post<Message>('/api/compte/mot-de-passe', { motDePasseActuel, nouveauMotDePasse }),
    );
    return r.message;
  }

  async changerEmail(motDePasse: string, nouvelEmail: string): Promise<string> {
    const r = await firstValueFrom(this.http.post<Message>('/api/compte/email', { motDePasse, nouvelEmail }));
    return r.message;
  }

  adresses(): Promise<Adresse[]> {
    return firstValueFrom(this.http.get<Adresse[]>('/api/compte/adresses'));
  }

  creerAdresse(a: AdresseSaisie): Promise<Adresse> {
    return firstValueFrom(this.http.post<Adresse>('/api/compte/adresses', a));
  }

  modifierAdresse(id: number, a: AdresseSaisie): Promise<Adresse> {
    return firstValueFrom(this.http.put<Adresse>(`/api/compte/adresses/${id}`, a));
  }

  adresseParDefaut(id: number): Promise<Adresse> {
    return firstValueFrom(this.http.put<Adresse>(`/api/compte/adresses/${id}/par-defaut`, {}));
  }

  async supprimerAdresse(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/compte/adresses/${id}`));
  }
}
