// Proxy du serveur de développement (ng serve) : les appels /api partent vers le backend local.
// Port du backend configurable : API_URL_SERVEUR=http://localhost:8085 npm start
const cible = process.env.API_URL_SERVEUR ?? 'http://localhost:8080';

export default {
  '/api': { target: cible, secure: false, changeOrigin: true },
  '/sitemap.xml': { target: cible, secure: false, changeOrigin: true },
  '/robots.txt': { target: cible, secure: false, changeOrigin: true },
  '/flux': { target: cible, secure: false, changeOrigin: true },
};
