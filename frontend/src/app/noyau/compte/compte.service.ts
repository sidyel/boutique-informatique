import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { StatutCommande, SuiviCommande } from '../api/modeles';
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

/** Ligne de « Mes commandes » (miroir de `CommandeResumeReponse`). */
export interface CommandeResume {
  numero: string;
  statut: StatutCommande;
  dateCreation: string;
  total: number;
  nombreArticles: number;
  premierArticle: string | null;
  imageUrl: string | null;
}

export interface PageCommandes {
  content: CommandeResume[];
  totalElements: number;
  totalPages: number;
  number: number;
}

/** Un avis du client (« Mes avis »). */
export interface MonAvis {
  id: number;
  produit: string;
  slugProduit: string;
  note: number;
  titre: string | null;
  commentaire: string | null;
  statut: 'EN_ATTENTE' | 'APPROUVE' | 'REJETE';
  achatVerifie: boolean;
  date: string;
  reponseBoutique: string | null;
}

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

  avis(): Promise<MonAvis[]> {
    return firstValueFrom(this.http.get<MonAvis[]>('/api/compte/avis'));
  }

  /** Droit d'accès : toutes les données du client (mot de passe exigé). Le fichier est fabriqué par l'appelant. */
  mesDonnees(motDePasse: string): Promise<unknown> {
    return firstValueFrom(this.http.post<unknown>('/api/compte/mes-donnees', { motDePasse }));
  }

  /** Suppression définitive du compte (mot de passe et mot SUPPRIMER). 422 COMMANDE_EN_COURS si une commande n'est pas terminée. */
  async supprimerCompte(motDePasse: string, confirmation: string): Promise<void> {
    await firstValueFrom(this.http.post<Message>('/api/compte/suppression', { motDePasse, confirmation }));
  }

  commandes(page = 0, taille = 10): Promise<PageCommandes> {
    return firstValueFrom(this.http.get<PageCommandes>('/api/compte/commandes', { params: { page, taille } }));
  }

  commande(numero: string): Promise<SuiviCommande> {
    return firstValueFrom(this.http.get<SuiviCommande>(`/api/compte/commandes/${encodeURIComponent(numero)}`));
  }

  async reprendrePaiement(numero: string): Promise<string> {
    const r = await firstValueFrom(
      this.http.post<{ urlPaiement: string }>(`/api/compte/commandes/${encodeURIComponent(numero)}/reprise-paiement`, {}),
    );
    return r.urlPaiement;
  }

  /** PDF d'une facture ou d'un avoir : le jeton d'accès part dans l'en-tête (pas de lien direct), la réponse n'est jamais mise en cache. */
  documentPdf(numeroCommande: string, avoir?: string): Promise<Blob> {
    const base = `/api/compte/commandes/${encodeURIComponent(numeroCommande)}`;
    const chemin = avoir ? `${base}/avoirs/${encodeURIComponent(avoir)}` : `${base}/facture`;
    return firstValueFrom(this.http.get(chemin, { responseType: 'blob' }));
  }
}
