import { IMAGE_LOADER, ImageLoaderConfig } from '@angular/common';
import { Provider } from '@angular/core';

/**
 * URL Cloudinary enregistrée : `…/(image|video)/upload/<transformation>/v<version>/<idPublic>.<ext>`.
 * La transformation de diffusion est `f_auto,q_auto` (images), `so_auto,f_auto,q_auto` (miniature de
 * vidéo) ou `fl_sanitize` (SVG).
 */
const URL_CLOUDINARY = /^(https:\/\/res\.cloudinary\.com\/[^/]+\/(?:image|video)\/upload\/)(?:([^/]+)\/)?(v\d+\/.+)$/;

/**
 * Chargeur de `NgOptimizedImage` (ADR 0005, « Pour la phase 3 ») : **complète** la transformation
 * enregistrée avec la largeur demandée, sans la dupliquer.
 * - `…/upload/f_auto,q_auto/v12/x.jpg`, 640 → `…/upload/f_auto,q_auto,w_640/v12/x.jpg` ;
 * - une largeur déjà présente est remplacée (jamais deux `w_`) ;
 * - SVG (`fl_sanitize`) : vectoriel, renvoyé tel quel ;
 * - image de démonstration servie par le site, ou toute autre URL : renvoyée telle quelle.
 */
export function chargeurCloudinary(config: ImageLoaderConfig): string {
  const correspondance = URL_CLOUDINARY.exec(config.src);
  if (!correspondance || !config.width) {
    return config.src;
  }
  const [, debut, transformation, suite] = correspondance;
  if (transformation?.includes('fl_sanitize')) {
    return config.src;
  }
  const largeur = `w_${Math.round(config.width)}`;
  let nouvelle: string;
  if (!transformation) {
    nouvelle = `f_auto,q_auto,${largeur}`;
  } else if (/(^|,)w_\d+/.test(transformation)) {
    nouvelle = transformation.replace(/(^|,)w_\d+/, `$1${largeur}`);
  } else {
    nouvelle = `${transformation},${largeur}`;
  }
  return `${debut}${nouvelle}/${suite}`;
}

export function fournirChargeurImages(): Provider {
  return { provide: IMAGE_LOADER, useValue: chargeurCloudinary };
}
