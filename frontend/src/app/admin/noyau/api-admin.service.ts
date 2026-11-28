import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Réponse de `GET /api/admin/moi`. */
export interface Moi {
  id: number;
  email: string;
  prenom: string;
  nom: string;
  role: RoleEquipe;
  secondFacteurActif: boolean;
  codesSecoursRestants: number;
  avertissements: { code: string; message: string }[];
}

export type RoleEquipe = 'SERVICE_CLIENT' | 'GESTIONNAIRE_STOCK' | 'ADMIN' | 'SUPER_ADMIN';

export const LIBELLES_ROLES: Record<RoleEquipe, string> = {
  SERVICE_CLIENT: 'Service client',
  GESTIONNAIRE_STOCK: 'Gestionnaire de stock',
  ADMIN: 'Administrateur',
  SUPER_ADMIN: 'Super-administrateur',
};

/**
 * Appels à l'API d'administration. Une action sensible refusée avec `REAUTHENTIFICATION_REQUISE` ouvre la fenêtre de saisie
 * du code TOTP (ADR 0009 §8), puis la requête est rejouée une seule fois.
 */
@Injectable({ providedIn: 'root' })
export class ApiAdminService {
  private readonly http = inject(HttpClient);

  /** Vrai pendant que la fenêtre de code est ouverte. */
  readonly reauthentificationOuverte = signal(false);
  private attente: { resoudre: (ok: boolean) => void } | null = null;

  async appeler<T>(requete: () => Promise<T>): Promise<T> {
    try {
      return await requete();
    } catch (e) {
      if (!(e instanceof HttpErrorResponse) || e.status !== 403 || (e.error as { code?: string } | null)?.code !== 'REAUTHENTIFICATION_REQUISE') {
        throw e;
      }
      if (!(await this.demanderCode())) throw e;
      return requete();
    }
  }

  get<T>(url: string, params?: Record<string, string>): Promise<T> {
    return firstValueFrom(this.http.get<T>(url, { params }));
  }
  post<T>(url: string, corps: unknown = {}): Promise<T> {
    return this.appeler(() => firstValueFrom(this.http.post<T>(url, corps)));
  }
  patch<T>(url: string, corps: unknown): Promise<T> {
    return this.appeler(() => firstValueFrom(this.http.patch<T>(url, corps)));
  }

  /** Ouvre la fenêtre de réauthentification ; `true` si un code juste a été saisi. */
  demanderCode(): Promise<boolean> {
    this.reauthentificationOuverte.set(true);
    return new Promise<boolean>((resoudre) => (this.attente = { resoudre }));
  }

  async validerCode(code: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/admin/reauthentification', { code }));
    this.fermer(true);
  }

  annulerCode(): void {
    this.fermer(false);
  }

  private fermer(ok: boolean): void {
    this.reauthentificationOuverte.set(false);
    this.attente?.resoudre(ok);
    this.attente = null;
  }
}
