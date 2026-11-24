import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { URL_API } from './url-api';

/**
 * Les services appellent toujours « /api/… » ; cet intercepteur ajoute l'hôte du backend
 * quand il est connu (rendu serveur). Dans le navigateur, l'URL reste relative.
 */
export const urlApiIntercepteur: HttpInterceptorFn = (requete, suivant) => {
  const base = inject(URL_API);
  if (!base || !requete.url.startsWith('/api/')) {
    return suivant(requete);
  }
  return suivant(requete.clone({ url: base.replace(/\/$/, '') + requete.url }));
};
