import { httpResource } from '@angular/common/http';
import { Injectable, computed } from '@angular/core';

import { ElementConfiance, ModesPaiementActifs, ParametresPublics } from '../api/modeles';

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

  /** Moyens de paiement réellement actifs ; à défaut d'information, seulement le paiement à la livraison. */
  readonly modesPaiement = computed(() => this.parametres()?.modesPaiement ?? MODES_PAR_DEFAUT);

  /** Moyens de paiement à afficher (pied de page, descriptions) : jamais un moyen qui n'est pas proposé. */
  readonly moyensPaiement = computed(() => moyensPaiement(this.modesPaiement()));

  /** Promesses commerciales, dont l'élément « paiement » est recalculé d'après les moyens réellement actifs. */
  readonly elementsConfiance = computed(() =>
    elementsConfianceAffiches(this.parametres()?.elementsConfiance ?? [], this.modesPaiement()),
  );

  readonly nomSite = computed(() => this.parametres()?.nomSite?.trim() || NOM_SITE_PAR_DEFAUT);

  /** `tel:+221338000000` : un toucher lance l'appel. */
  readonly lienTelephone = computed(() => lienTelephone(this.parametres()?.telephone));

  readonly telephoneAffiche = computed(() => numeroAffiche(this.parametres()?.telephone));

  readonly whatsappAffiche = computed(() => numeroAffiche(this.parametres()?.whatsapp));

  /** Lien WhatsApp sans message ; voir {@link lienWhatsApp} pour un message prérempli. */
  readonly lienWhatsApp = computed(() => lienWhatsApp(this.parametres()?.whatsapp));
}

/** `+221338000000` → `tel:+221338000000`. */
const MODES_PAR_DEFAUT: ModesPaiementActifs = { mobileMoney: false, carte: false, livraison: true };

/** Libellés des moyens de paiement actifs : mobile money (Wave, Orange Money, Free Money), carte, livraison. */
export function moyensPaiement(modes: ModesPaiementActifs): string[] {
  return [
    ...(modes.mobileMoney ? ['Wave', 'Orange Money', 'Free Money'] : []),
    ...(modes.carte ? ['Carte bancaire'] : []),
    ...(modes.livraison ? ['Paiement à la livraison'] : []),
  ];
}

/**
 * Remplace le titre et le texte de l'élément « paiement » par les moyens réellement actifs (le texte saisi par l'admin
 * ne peut pas annoncer un moyen qui n'est pas proposé) ; le masque si aucun moyen n'est actif.
 */
export function elementsConfianceAffiches(
  elements: ElementConfiance[],
  modes: ModesPaiementActifs,
): ElementConfiance[] {
  const enLigne = [...(modes.mobileMoney ? ['Wave', 'Orange Money'] : []), ...(modes.carte ? ['Carte bancaire'] : [])];
  return elements.flatMap((e) => {
    if (e.icone !== 'PAIEMENT') return [e];
    if (enLigne.length) {
      return [{ ...e, titre: enLigne.join(', '), texte: modes.livraison ? 'Ou paiement à la livraison' : null }];
    }
    return modes.livraison ? [{ ...e, titre: 'Paiement à la livraison', texte: 'Réglez à la réception' }] : [];
  });
}

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
