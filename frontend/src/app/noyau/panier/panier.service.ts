import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import { Panier } from '../api/modeles';
import { SessionService } from '../auth/session.service';
import { PlateformeService } from '../plateforme.service';
import { SuiviService } from '../suivi/suivi.service';

/** Drapeau local (non sensible) : un panier existe, le charger au démarrage pour afficher le compteur. */
const CLE_PANIER_ACTIF = 'panier_actif';
/** Zone de livraison choisie pour l'estimation, gardée d'une visite à l'autre. */
const CLE_ZONE = 'zone_livraison';

const ERREUR_GENERIQUE = 'Impossible de mettre à jour le panier. Vérifiez votre connexion et réessayez.';

/**
 * Panier (`/api/panier`) : invité (cookie HttpOnly posé par le serveur) ou connecté.
 *
 * Le serveur est la seule source des prix, des quantités autorisées et des totaux : chaque appel renvoie le
 * panier complet recalculé, qui remplace l'état local. Le navigateur n'envoie qu'une variante et une quantité.
 *
 * Rien n'est appelé pendant le rendu serveur (le SSR ne transmet pas les cookies du visiteur). Au démarrage,
 * le panier n'est chargé que si ce navigateur en a déjà un (drapeau local), pour ne pas faire un appel inutile
 * à chaque visite.
 */
@Injectable({ providedIn: 'root' })
export class PanierService {
  private readonly http = inject(HttpClient);
  private readonly suivi = inject(SuiviService);
  private readonly session = inject(SessionService);
  private readonly navigateur = inject(PlateformeService).estNavigateur;

  /** Dernier panier reçu du serveur ; null tant qu'il n'est pas chargé. */
  readonly panier = signal<Panier | null>(null);
  /** Premier chargement en cours (squelettes). */
  readonly chargement = signal(false);
  /** Une modification est en cours : boutons désactivés (pas de double envoi). */
  readonly enCours = signal(false);
  /** Message d'erreur de la dernière action (stock insuffisant, code refusé…). */
  readonly erreur = signal<string | null>(null);
  readonly tiroirOuvert = signal(false);
  readonly zoneId = signal<number | null>(null);

  /** Nombre d'articles affiché sur l'icône du panier. */
  readonly nombreArticles = computed(() => this.panier()?.nombreArticles ?? 0);
  readonly vide = computed(() => (this.panier()?.lignes.length ?? 0) === 0);

  private initialise = false;

  /** Au démarrage dans le navigateur : charge le panier s'il en existe un. */
  initialiser(): void {
    if (!this.navigateur || this.initialise) return;
    this.initialise = true;
    const zone = Number(localStorage.getItem(CLE_ZONE));
    if (Number.isInteger(zone) && zone > 0) this.zoneId.set(zone);
    if (localStorage.getItem(CLE_PANIER_ACTIF) === '1') {
      void this.charger();
    } else {
      // Un client connecté a peut-être un panier de compte, même sans panier de visiteur sur cet appareil
      void this.session.restaurer().then(() => {
        if (this.session.connecte()) void this.charger();
      });
    }
  }

  /** Recharge le panier (page panier, ouverture du tiroir). */
  async charger(): Promise<void> {
    if (!this.navigateur) return;
    this.initialise = true;
    this.chargement.set(this.panier() === null);
    try {
      // Le jeton d'accès n'existe qu'une fois la session restaurée : sans cette attente, un client connecté qui
      // recharge la page lirait le panier d'un visiteur (vide) au lieu de celui de son compte
      if (!this.session.prete()) await this.session.restaurer();
      this.recevoir(await firstValueFrom(this.http.get<Panier>('/api/panier', { params: this.parametres() })));
    } catch {
      this.erreur.set('Impossible de charger le panier. Vérifiez votre connexion et réessayez.');
    } finally {
      this.chargement.set(false);
    }
  }

  /**
   * Ajoute une variante (fiche produit) puis ouvre le tiroir. Le suivi reçoit le prix et la quantité
   * réellement retenus par le serveur (quantité éventuellement ramenée au stock).
   */
  async ajouter(varianteId: number, quantite = 1): Promise<boolean> {
    const avant = this.quantite(varianteId);
    const panier = await this.envoyer(
      this.http.post<Panier>('/api/panier/lignes', { varianteId, quantite }, { params: this.parametres() }),
    );
    if (!panier) return false;
    const ligne = panier.lignes.find((l) => l.varianteId === varianteId);
    if (ligne && ligne.quantite > avant) {
      this.suivi.ajouterAuPanier(ligne.sku, ligne.prixUnitaire, ligne.quantite - avant);
    }
    this.ouvrirTiroir();
    return true;
  }

  async modifierQuantite(ligneId: number, quantite: number): Promise<void> {
    await this.envoyer(
      this.http.patch<Panier>(`/api/panier/lignes/${ligneId}`, { quantite }, { params: this.parametres() }),
    );
  }

  async supprimer(ligneId: number): Promise<void> {
    await this.envoyer(this.http.delete<Panier>(`/api/panier/lignes/${ligneId}`, { params: this.parametres() }));
  }

  /** @return true si le code est enregistré (même s'il ne s'applique pas encore : montant minimum…) */
  async appliquerCode(code: string): Promise<boolean> {
    const panier = await this.envoyer(
      this.http.put<Panier>('/api/panier/code-promo', { code }, { params: this.parametres() }),
    );
    return panier !== null;
  }

  async retirerCode(): Promise<void> {
    await this.envoyer(this.http.delete<Panier>('/api/panier/code-promo', { params: this.parametres() }));
  }

  /** Le client a lu « Le prix de cet article est passé de X à Y » et accepte les nouveaux prix. */
  async accepterPrix(): Promise<void> {
    await this.envoyer(this.http.post<Panier>('/api/panier/accepter-prix', null, { params: this.parametres() }));
  }

  /** Zone de livraison pour l'estimation des frais (null : aucune). */
  async choisirZone(zoneId: number | null): Promise<void> {
    this.zoneId.set(zoneId);
    if (this.navigateur) {
      if (zoneId === null) localStorage.removeItem(CLE_ZONE);
      else localStorage.setItem(CLE_ZONE, String(zoneId));
    }
    if (this.panier()) await this.charger();
  }

  /** @param relire relire le panier (ouverture depuis l'en-tête : il a pu changer dans un autre onglet) */
  ouvrirTiroir(relire = false): void {
    this.tiroirOuvert.set(true);
    if (relire && this.panier() !== null) void this.charger();
  }

  fermerTiroir(): void {
    this.tiroirOuvert.set(false);
  }

  /** Efface le message d'erreur et les avertissements déjà lus. */
  effacerMessages(): void {
    this.erreur.set(null);
    this.panier.update((p) => (p && p.avertissements.length ? { ...p, avertissements: [] } : p));
  }

  private quantite(varianteId: number): number {
    return this.panier()?.lignes.find((l) => l.varianteId === varianteId)?.quantite ?? 0;
  }

  private parametres(): Record<string, string> {
    const zone = this.zoneId();
    return zone === null ? {} : { zone: String(zone) };
  }

  private async envoyer(requete: Observable<Panier>): Promise<Panier | null> {
    if (this.enCours()) return null;
    this.enCours.set(true);
    this.erreur.set(null);
    try {
      if (!this.session.prete()) await this.session.restaurer();
      const panier = await firstValueFrom(requete);
      this.recevoir(panier);
      return panier;
    } catch (e) {
      this.erreur.set(messageErreur(e));
      // Stock ou ligne changés entre-temps : l'état affiché doit refléter le serveur
      if (e instanceof HttpErrorResponse && (e.status === 409 || e.status === 404)) {
        void this.charger();
      }
      return null;
    } finally {
      this.enCours.set(false);
    }
  }

  /** Après une déconnexion : le panier affiché n'est plus celui du compte (le panier invité repart de zéro). */
  reinitialiserLocalement(): void {
    this.panier.set(null);
    this.tiroirOuvert.set(false);
    if (this.navigateur) localStorage.removeItem(CLE_PANIER_ACTIF);
  }

  private recevoir(panier: Panier): void {
    this.panier.set(panier);
    if (!this.navigateur) return;
    if (panier.lignes.length) localStorage.setItem(CLE_PANIER_ACTIF, '1');
    else localStorage.removeItem(CLE_PANIER_ACTIF);
  }
}

/** Message lisible d'une erreur de l'API (`ProblemDetail.detail`, en français), sinon un message générique. */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof HttpErrorResponse) {
    if (erreur.status === 429) return 'Trop d’essais en peu de temps. Patientez quelques minutes.';
    const detail = (erreur.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && erreur.status >= 400 && erreur.status < 500) return detail;
  }
  return ERREUR_GENERIQUE;
}
