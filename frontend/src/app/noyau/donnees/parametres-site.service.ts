import { httpResource } from '@angular/common/http';
import { Injectable, computed } from '@angular/core';

import { ParametresPublics } from '../api/modeles';

/** Nom affiché tant que les paramètres ne sont pas chargés (ou si l'API ne répond pas). */
export const NOM_SITE_PAR_DEFAUT = 'Boutique';

/**
 * Paramètres publics du site (`/api/parametres`) : identité, contact, promesses commerciales.
 * Chargés une fois par rendu et partagés par l'en-tête, le pied de page, le SEO… Les réponses du SSR
 * sont réutilisées par le navigateur (transfer cache) : aucun second appel à l'hydratation.
 */
@Injectable({ providedIn: 'root' })
export class ParametresSiteService {
  private readonly ressource = httpResource<ParametresPublics>(() => '/api/parametres');

  /** `undefined` pendant le chargement ou en cas d'erreur : chaque usage prévoit l'absence. */
  readonly parametres = computed(() => (this.ressource.hasValue() ? this.ressource.value() : undefined));

  readonly nomSite = computed(() => this.parametres()?.nomSite?.trim() || NOM_SITE_PAR_DEFAUT);

  /** `tel:+221338000000` : un toucher lance l'appel. */
  readonly lienTelephone = computed(() => lienTelephone(this.parametres()?.telephone));

  readonly telephoneAffiche = computed(() => numeroAffiche(this.parametres()?.telephone));

  readonly whatsappAffiche = computed(() => numeroAffiche(this.parametres()?.whatsapp));

  /** Lien WhatsApp sans message ; voir {@link lienWhatsApp} pour un message prérempli. */
  readonly lienWhatsApp = computed(() => lienWhatsApp(this.parametres()?.whatsapp));
}

/** `+221338000000` → `tel:+221338000000`. */
export function lienTelephone(numero: string | null | undefined): string | null {
  return numero ? `tel:${numero}` : null;
}

/** `+221770000000` → `https://wa.me/221770000000?text=…` : ouvre directement l'application. */
export function lienWhatsApp(numero: string | null | undefined, message?: string): string | null {
  if (!numero) {
    return null;
  }
  const lien = `https://wa.me/${numero.replace(/\D/g, '')}`;
  return message ? `${lien}?text=${encodeURIComponent(message)}` : lien;
}

/** `+221770000000` → `77 000 00 00` (format local, espaces insécables). */
export function numeroAffiche(numero: string | null | undefined): string | null {
  if (!numero) {
    return null;
  }
  const local = numero.replace(/^\+221/, '');
  const decoupe = /^(\d{2})(\d{3})(\d{2})(\d{2})$/.exec(local);
  return decoupe ? decoupe.slice(1).join(' ') : numero;
}
