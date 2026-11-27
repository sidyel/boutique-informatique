import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  CommandeCreee,
  CreationCommande,
  OptionsCommande,
  RetourPaiement,
  SuiviCommande,
} from '../api/modeles';
import { effacerSession, ecrireSession, lireSession } from '../donnees/stockage-session';
import { PlateformeService } from '../plateforme.service';

const CLE_IDEMPOTENCE = 'cle_commande';
const FORMAT_CLE = /^[A-Za-z0-9_-]{16,64}$/;

/** Échec lisible d'une demande de commande ou de suivi. */
export interface ErreurCommande {
  /** Code stable de l'API (STOCK_INSUFFISANT, TOTAL_MODIFIE…), ou RESEAU si le serveur n'a pas répondu. */
  code: string;
  message: string;
  /** Rien n'a été enregistré avec certitude : le même envoi peut être refait sans risque (clé d'idempotence). */
  reessayable: boolean;
  /** Le client doit revoir son panier (prix, total, stock ou code promo ont changé). */
  panierARevoir: boolean;
}

export const MESSAGE_RESEAU =
  'La connexion a été interrompue. Votre commande n’a pas été perdue ni doublée : appuyez à nouveau sur le bouton.';

const CODES_PANIER_A_REVOIR = new Set([
  'TOTAL_MODIFIE',
  'PRIX_MODIFIES',
  'PANIER_NON_COMMANDABLE',
  'STOCK_INSUFFISANT',
  'PANIER_VIDE',
  'CODE_PROMO_INCONNU',
  'CODE_PROMO_EXPIRE',
  'CODE_PROMO_EPUISE',
  'CODE_PROMO_DEJA_UTILISE',
  'CODE_PROMO_MINIMUM',
  'CODE_PROMO_NON_APPLICABLE',
]);

/**
 * Commande (`/api/commandes`) : création idempotente, options, suivi par lien.
 *
 * **Clé d'idempotence** : une clé aléatoire par tentative de commande, gardée dans sessionStorage jusqu'au succès.
 * Si la réponse se perd (coupure réseau, double appui, rechargement de la page), le même envoi renvoie la même
 * commande au lieu d'en créer une seconde.
 */
@Injectable({ providedIn: 'root' })
export class CommandeService {
  private readonly http = inject(HttpClient);
  private readonly navigateur = inject(PlateformeService).estNavigateur;

  options(): Promise<OptionsCommande> {
    return firstValueFrom(this.http.get<OptionsCommande>('/api/commandes/options'));
  }

  /** Clé de la tentative en cours (créée au premier appel, conservée jusqu'à {@link oublierCle}). */
  cleIdempotence(): string {
    const existante = this.navigateur ? lireSession(CLE_IDEMPOTENCE) : null;
    if (existante && FORMAT_CLE.test(existante)) return existante;
    const cle = genererCle();
    if (this.navigateur) ecrireSession(CLE_IDEMPOTENCE, cle);
    return cle;
  }

  /** Après un succès (ou quand le serveur dit que la clé ne convient plus) : la prochaine commande aura sa clé. */
  oublierCle(): void {
    if (this.navigateur) effacerSession(CLE_IDEMPOTENCE);
  }

  async commander(demande: CreationCommande): Promise<CommandeCreee> {
    try {
      const reponse = await firstValueFrom(
        this.http.post<CommandeCreee>('/api/commandes', demande, {
          headers: new HttpHeaders({ 'Idempotency-Key': this.cleIdempotence() }),
        }),
      );
      this.oublierCle();
      return reponse;
    } catch (e) {
      const erreur = traduireErreur(e);
      // La clé ne peut plus servir : la demande qu'elle a enregistrée n'est pas celle-ci
      if (erreur.code === 'CLE_IDEMPOTENCE_REUTILISEE') this.oublierCle();
      throw erreur;
    }
  }

  /** Retour de la page de paiement : le serveur vérifie auprès du prestataire, le navigateur n'est pas cru. */
  retour(jeton: string): Promise<RetourPaiement> {
    return firstValueFrom(this.http.get<RetourPaiement>(`/api/commandes/retour/${encodeURIComponent(jeton)}`));
  }

  suivi(jeton: string): Promise<SuiviCommande> {
    return firstValueFrom(this.http.get<SuiviCommande>(`/api/commandes/suivi/${encodeURIComponent(jeton)}`));
  }

  /** « Reprendre le paiement » : nouvelle page de paiement du prestataire pour la même commande. */
  async reprendrePaiement(jeton: string): Promise<string> {
    try {
      const reponse = await firstValueFrom(
        this.http.post<{ urlPaiement: string }>(
          `/api/commandes/suivi/${encodeURIComponent(jeton)}/reprise-paiement`,
          null,
        ),
      );
      return reponse.urlPaiement;
    } catch (e) {
      throw traduireErreur(e);
    }
  }

  /** Revendique l'émission de l'événement « achat » : vrai pour un seul appelant par commande. */
  async revendiquerAchat(jeton: string): Promise<boolean> {
    try {
      const reponse = await firstValueFrom(
        this.http.post<{ emettre: boolean }>(`/api/commandes/suivi/${encodeURIComponent(jeton)}/achat-suivi`, null),
      );
      return reponse.emettre;
    } catch {
      return false;
    }
  }
}

function genererCle(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Navigateurs sans randomUUID : 32 octets aléatoires en hexadécimal
  const octets = new Uint8Array(16);
  crypto.getRandomValues(octets);
  return Array.from(octets, (o) => o.toString(16).padStart(2, '0')).join('');
}

/** Erreur HTTP ou réseau → message en français ; jamais de détail technique. */
export function traduireErreur(e: unknown): ErreurCommande {
  if (!(e instanceof HttpErrorResponse)) {
    return { code: 'INCONNUE', message: 'Une erreur est survenue. Réessayez.', reessayable: true, panierARevoir: false };
  }
  // 0 : pas de réponse (coupure, délai dépassé) ; 502-504 : proxy ou serveur injoignable
  if (e.status === 0 || e.status === 502 || e.status === 503 || e.status === 504) {
    const code = (e.error as { code?: string } | null)?.code ?? 'RESEAU';
    return {
      code,
      message: code === 'PAIEMENT_INDISPONIBLE' ? lireDetail(e) : MESSAGE_RESEAU,
      reessayable: true,
      panierARevoir: false,
    };
  }
  if (e.status === 429) {
    return {
      code: 'TROP_DE_REQUETES',
      message: 'Trop de tentatives en peu de temps. Patientez quelques minutes avant de réessayer.',
      reessayable: true,
      panierARevoir: false,
    };
  }
  const code = (e.error as { code?: string } | null)?.code ?? 'ERREUR';
  if (e.status >= 500) {
    return { code, message: 'Une erreur est survenue de notre côté. Réessayez dans un instant.', reessayable: true, panierARevoir: false };
  }
  return { code, message: lireDetail(e), reessayable: false, panierARevoir: CODES_PANIER_A_REVOIR.has(code) };
}

function lireDetail(e: HttpErrorResponse): string {
  const detail = (e.error as { detail?: unknown } | null)?.detail;
  return typeof detail === 'string' && detail ? detail : 'La demande n’a pas pu être traitée.';
}
