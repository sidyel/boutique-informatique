import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { PlateformeService } from '../plateforme.service';

/** Profil du client connecté (miroir de `ProfilReponse`). */
export interface Profil {
  id: number;
  email: string;
  prenom: string;
  nom: string;
  telephone: string | null;
  role: string;
  emailVerifie: boolean;
  consentementMarketing: boolean;
  /** Date de la dernière décision de consentement (acceptation ou retrait). */
  dateConsentement: string | null;
  sourceConsentement: 'INSCRIPTION' | 'COMPTE' | 'ADMIN' | null;
}

export interface ReponseConnexion {
  jetonAcces: string;
  expireDans: number;
  utilisateur: Profil;
}

/** Étape du second facteur d'un compte de l'équipe (ADR 0009) : le mot de passe est bon, aucune session n'est ouverte. */
export type EtapeEquipe = 'ENROLEMENT_REQUIS' | 'CODE_REQUIS' | 'CODES_SECOURS_A_CONFIRMER';

export interface ReponseEtape {
  etape: EtapeEquipe;
  jetonEtape: string;
}

/** Levée par `connecter` quand le compte est un compte de l'équipe : l'interface continue sur `/admin/connexion`. */
export class EtapeEquipeRequise extends Error {
  constructor(readonly etape: ReponseEtape) {
    super('Second facteur requis');
  }
}

/** Message échangé entre onglets (même origine). Le jeton d'accès y circule en mémoire, il n'est jamais stocké. */
type MessageSession =
  | { type: 'session'; jeton: string; expireA: number; utilisateur: Profil }
  | { type: 'deconnexion' };

/** Indice non secret : « une session a existé ici ». Évite un appel de rafraîchissement inutile pour un simple visiteur. */
const CLE_INDICE = 'session_active';
const NOM_CANAL = 'bi-session';
const NOM_VERROU = 'bi-rafraichissement-session';
/** Un jeton qui expire dans moins de 30 s est renouvelé avant l'appel. */
const MARGE_MS = 30_000;

/**
 * Session du client (ADR 0003).
 * - Jeton d'accès **en mémoire uniquement** (jamais localStorage ni cookie lisible) : un rechargement de page le
 *   retrouve par le cookie de rafraîchissement (HttpOnly, SameSite=Strict, chemin /api/auth).
 * - **Un seul rafraîchissement à la fois entre onglets** : le cookie tourne à chaque rafraîchissement, deux
 *   rafraîchissements simultanés présenteraient le même ancien cookie et passeraient pour un vol (session révoquée).
 *   Verrou Web Locks ; le jeton obtenu est diffusé aux autres onglets (BroadcastChannel), qui le reprennent sans
 *   rappeler le serveur. Sans Web Locks (vieux navigateurs) : un seul rafraîchissement à la fois dans l'onglet.
 * - Rien ne s'exécute pendant le rendu serveur.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly navigateur = inject(PlateformeService).estNavigateur;

  readonly utilisateur = signal<Profil | null>(null);
  readonly connecte = computed(() => this.utilisateur() !== null);
  /** Vrai une fois la restauration de session terminée (les gardes attendent ce moment). */
  readonly prete = signal(false);

  private jeton: string | null = null;
  private expireA = 0;
  private canal: BroadcastChannel | null = null;
  private restauration: Promise<void> | null = null;
  private rafraichissementEnCours: Promise<boolean> | null = null;

  /** Jeton à joindre aux appels de l'API, ou null. */
  jetonAcces(): string | null {
    return this.jeton;
  }

  /** Le jeton est absent ou sur le point d'expirer. */
  expireBientot(): boolean {
    return this.jeton !== null && Date.now() > this.expireA - MARGE_MS;
  }

  /** Au démarrage du navigateur : retrouve la session grâce au cookie de rafraîchissement, une seule fois. */
  restaurer(): Promise<void> {
    if (!this.navigateur) {
      this.prete.set(true);
      return Promise.resolve();
    }
    this.restauration ??= this.demarrer();
    return this.restauration;
  }

  private async demarrer(): Promise<void> {
    this.ouvrirCanal();
    if (localStorage.getItem(CLE_INDICE) === '1') {
      await this.rafraichir();
    }
    this.prete.set(true);
  }

  async connecter(email: string, motDePasse: string): Promise<Profil> {
    const reponse = await this.connecterAvecEtape(email, motDePasse);
    if ('etape' in reponse) throw new EtapeEquipeRequise(reponse);
    return reponse.utilisateur;
  }

  /** Mot de passe : soit une session (client), soit l'étape suivante du second facteur (équipe). */
  async connecterAvecEtape(email: string, motDePasse: string): Promise<ReponseConnexion | ReponseEtape> {
    const reponse = await firstValueFrom(
      this.http.post<ReponseConnexion | ReponseEtape>('/api/auth/connexion', { email, motDePasse }),
    );
    if (!('etape' in reponse)) {
      this.ouvrirCanal();
      this.adopter(reponse);
    }
    return reponse;
  }

  /** Une étape du second facteur a ouvert la session : le jeton d'accès et le profil sont adoptés. */
  ouvrirSessionEquipe(reponse: ReponseConnexion): Profil {
    this.ouvrirCanal();
    this.adopter(reponse);
    return reponse.utilisateur;
  }

  /**
   * Nouveau jeton d'accès (et nouveau cookie). `true` si la session est valide. `forcer` : le serveur vient de refuser
   * le jeton (401), on ne se fie plus à sa date d'expiration. Un seul rafraîchissement à la fois :
   * l'appel concurrent dans le même onglet partage la même promesse ; entre onglets, le verrou Web Locks sérialise.
   */
  rafraichir(forcer = false): Promise<boolean> {
    if (!this.navigateur) return Promise.resolve(false);
    this.rafraichissementEnCours ??= this.rafraichirAvecVerrou(forcer).finally(() => (this.rafraichissementEnCours = null));
    return this.rafraichissementEnCours;
  }

  private async rafraichirAvecVerrou(forcer: boolean): Promise<boolean> {
    const faire = async (): Promise<boolean> => {
      // Un autre onglet a peut-être déjà renouvelé pendant que nous attendions le verrou
      if (!forcer && this.jeton && !this.expireBientot()) return true;
      try {
        const reponse = await firstValueFrom(this.http.post<ReponseConnexion>('/api/auth/rafraichir', {}));
        this.adopter(reponse);
        return true;
      } catch {
        this.oublier(true);
        return false;
      }
    };
    const verrous = (navigator as Navigator & { locks?: LockManager }).locks;
    return verrous ? verrous.request(NOM_VERROU, faire) : faire();
  }

  async deconnecter(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/deconnexion', {}));
    } catch {
      // Hors ligne ou session déjà expirée : on oublie quand même la session ici
    }
    this.oublier(true);
  }

  /** La session a été révoquée côté serveur (mot de passe changé, compte supprimé…) : on l'oublie ici aussi. */
  oublierLocalement(): void {
    this.oublier(true);
  }

  /** Met à jour le profil affiché (après modification du profil). */
  majProfil(profil: Profil): void {
    this.utilisateur.set(profil);
  }

  private adopter(reponse: ReponseConnexion): void {
    this.jeton = reponse.jetonAcces;
    this.expireA = Date.now() + reponse.expireDans * 1000;
    this.utilisateur.set(reponse.utilisateur);
    localStorage.setItem(CLE_INDICE, '1');
    this.diffuser({ type: 'session', jeton: this.jeton, expireA: this.expireA, utilisateur: reponse.utilisateur });
  }

  private oublier(diffuser: boolean): void {
    this.jeton = null;
    this.expireA = 0;
    this.utilisateur.set(null);
    if (this.navigateur) localStorage.removeItem(CLE_INDICE);
    if (diffuser) this.diffuser({ type: 'deconnexion' });
  }

  private ouvrirCanal(): void {
    if (this.canal || typeof BroadcastChannel === 'undefined') return;
    this.canal = new BroadcastChannel(NOM_CANAL);
    this.canal.onmessage = (e: MessageEvent<MessageSession>) => {
      const m = e.data;
      if (m.type === 'session') {
        this.jeton = m.jeton;
        this.expireA = m.expireA;
        this.utilisateur.set(m.utilisateur);
      } else if (m.type === 'deconnexion') {
        this.oublier(false);
      }
    };
  }

  private diffuser(message: MessageSession): void {
    this.canal?.postMessage(message);
  }
}
