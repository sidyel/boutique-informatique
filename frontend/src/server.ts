import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

/**
 * Serveur de rendu (SSR).
 * Variables d'environnement :
 *  - PORT              : port d'écoute (4000 par défaut) ;
 *  - API_URL_SERVEUR   : URL du backend vue depuis ce serveur (lue par app.config.server.ts) ;
 *  - NG_ALLOWED_HOSTS  : noms d'hôte acceptés, séparés par des virgules (protection SSRF),
 *                        en plus de ceux d'angular.json — y mettre le domaine de production.
 */
const dossierNavigateur = join(import.meta.dirname, '../browser');

const app = express();
app.disable('x-powered-by');

const moteurAngular = new AngularNodeAppEngine();

/**
 * Fichiers statiques.
 * Les fichiers JS/CSS ont une empreinte dans leur nom et les polices ne changent pas de nom
 * sans changer de contenu : cache d'un an, immuable.
 */
app.use(
  express.static(dossierNavigateur, {
    maxAge: '1y',
    immutable: true,
    index: false,
    redirect: false,
  }),
);

/**
 * L'API n'est jamais servie ici (nginx l'envoie au backend). Une requête /api arrivée jusqu'au SSR reçoit
 * une 404 sèche : la rendre avec Angular relancerait les appels de l'application vers ce même serveur.
 */
app.use('/api', (_requete, reponse) => {
  reponse.status(404).type('text/plain').send('API indisponible sur le serveur de rendu');
});

/**
 * Toutes les autres requêtes sont rendues par Angular. Les statuts (404…) sont fixés
 * par les composants via RESPONSE_INIT.
 */
app.use((requete, reponse, suivant) => {
  moteurAngular
    .handle(requete)
    .then((resultat) =>
      resultat ? writeResponseToNodeResponse(resultat, reponse) : suivant(),
    )
    .catch(suivant);
});

if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (erreur) => {
    if (erreur) {
      throw erreur;
    }
    console.log(`Serveur SSR à l'écoute sur http://localhost:${port}`);
  });
}

/**
 * Gestionnaire utilisé par la CLI Angular (ng serve et build).
 */
export const reqHandler = createNodeRequestHandler(app);
