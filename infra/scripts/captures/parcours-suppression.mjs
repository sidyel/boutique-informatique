// Parcours « données personnelles » (partie 5d) contre la pile locale :
//  1. compte de démonstration (a des commandes en cours) : téléchargement des données, refus de la suppression, avis ;
//  2. compte neuf : suppression réelle, page de confirmation, e-mail, impossible de se reconnecter.
// Captures à 360, 390 et 1440 px. Variables : DEMO_CLIENT_EMAIL, DEMO_CLIENT_MOT_DE_PASSE (voir .env).
import { chromium } from 'playwright-core';
import { mkdirSync, readFileSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost';
const MAILPIT = process.env.MAILPIT ?? 'http://localhost:8025';
const SORTIE = process.env.SORTIE ?? './sorties';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const DEMO_EMAIL = process.env.DEMO_CLIENT_EMAIL ?? 'client@exemple.sn';
const DEMO_MDP = process.env.DEMO_CLIENT_MOT_DE_PASSE ?? 'Client-Demo-Local-2026';
mkdirSync(SORTIE, { recursive: true });

const EMAIL = `suppression-${Date.now().toString(36)}@exemple.sn`;
const MDP = 'Un-mot-de-passe-solide-1';
let echecs = 0;
const ok = (condition, nom) => {
  console.log(`${condition ? 'OK ' : 'KO '} ${nom}`);
  if (!condition) echecs++;
};
const json = { 'Content-Type': 'application/json', Origin: BASE };
const haut = (page) => page.evaluate(() => scrollTo(0, 0));

async function connecter(page, email, mdp) {
  await page.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
  await page.getByLabel(/^\s*Adresse e-mail/).fill(email);
  await page.getByLabel(/^\s*Mot de passe/).fill(mdp);
  await page.getByRole('button', { name: /se connecter/i }).click();
  await page.getByRole('heading', { name: /Bonjour/ }).waitFor();
}

const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });

// --- 1. Compte de démonstration ------------------------------------------------------------------
for (const [largeur, nom] of [[360, '360'], [390, '390'], [1440, '1440']]) {
  const contexte = await navigateur.newContext({
    viewport: { width: largeur, height: largeur === 1440 ? 900 : 740 },
    isMobile: largeur < 768, hasTouch: largeur < 768, acceptDownloads: true,
  });
  const page = await contexte.newPage();
  await connecter(page, DEMO_EMAIL, DEMO_MDP);

  await page.getByRole('link', { name: 'Avis', exact: true }).click();
  await page.getByRole('heading', { name: 'Mes avis' }).waitFor();
  await page.getByText('Aucun avis pour le moment').waitFor();
  await haut(page);
  await page.screenshot({ path: `${SORTIE}/avis-${nom}.png`, fullPage: true });
  ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `${nom} px : avis sans défilement horizontal`);

  await page.getByRole('link', { name: 'Sécurité', exact: true }).click();
  await page.getByRole('heading', { name: 'Supprimer mon compte' }).waitFor();
  await haut(page);
  await page.screenshot({ path: `${SORTIE}/securite-donnees-${nom}.png`, fullPage: true });
  ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `${nom} px : sécurité sans défilement horizontal`);

  if (largeur === 360) {
    // Téléchargement des données
    await page.locator('input[name="motDePasseDonnees"]').fill(DEMO_MDP);
    const [telechargement] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Télécharger mes données' }).click(),
    ]);
    const chemin = `${SORTIE}/mes-donnees-boutique.json`;
    await telechargement.saveAs(chemin);
    const donnees = JSON.parse(readFileSync(chemin, 'utf-8'));
    ok(telechargement.suggestedFilename() === 'mes-donnees-boutique.json', 'fichier nommé mes-donnees-boutique.json');
    ok(donnees.profil.email === DEMO_EMAIL, 'le fichier contient le profil');
    ok(donnees.commandes.length >= 3, `le fichier contient les commandes (${donnees.commandes.length})`);
    ok(donnees.commandes.some((c) => c.numeroFacture), 'une commande porte son numéro de facture');
    ok(!/empreinte|\$2a\$|gclid|motDePasseHash/.test(JSON.stringify(donnees)), 'aucun secret ni suivi publicitaire dans le fichier');
    await page.getByText('a été téléchargé').waitFor();

    // Refus : commandes en cours
    await page.locator('input[name="motDePasseSuppression"]').fill(DEMO_MDP);
    await page.locator('input[name="confirmationSuppression"]').fill('SUPPRIMER');
    await page.getByRole('button', { name: /Supprimer définitivement/ }).click();
    await page.getByText(/ne peut pas être supprimé/).waitFor();
    ok(await page.getByRole('link', { name: 'Voir mes commandes' }).isVisible(), 'refus : lien vers les commandes');
    await page.screenshot({ path: `${SORTIE}/suppression-refusee-360.png`, fullPage: false });
    // La session est intacte
    await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
    ok(await page.getByRole('heading', { name: /Bonjour/ }).isVisible(), 'le compte existe toujours après le refus');
  }
  await contexte.close();
}

// --- 2. Compte neuf : suppression réelle -------------------------------------------------------------
await fetch(`${BASE}/api/auth/inscription`, {
  method: 'POST', headers: json,
  body: JSON.stringify({ email: EMAIL, motDePasse: MDP, prenom: 'Awa', nom: 'Suppression', telephone: '77 765 43 21', consentementMarketing: true }),
});
const contexte = await navigateur.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
const page = await contexte.newPage();
await connecter(page, EMAIL, MDP);
await page.goto(`${BASE}/compte/securite`, { waitUntil: 'networkidle' });
await page.locator('input[name="motDePasseSuppression"]').fill(MDP);
await page.locator('input[name="confirmationSuppression"]').fill('non');
await page.getByRole('button', { name: /Supprimer définitivement/ }).click();
await page.getByText(/Saisissez le mot SUPPRIMER/).first().waitFor();
await haut(page);
await page.screenshot({ path: `${SORTIE}/suppression-confirmation-360.png`, fullPage: false });
await page.locator('input[name="confirmationSuppression"]').fill('SUPPRIMER');
await page.getByRole('button', { name: /Supprimer définitivement/ }).click();
await page.getByRole('heading', { name: 'Votre compte est supprimé' }).waitFor();
ok(page.url().endsWith('/compte-supprime'), 'redirection vers la page de confirmation');
await haut(page);
await page.screenshot({ path: `${SORTIE}/compte-supprime-360.png`, fullPage: true });

let sujet = null;
for (let i = 0; i < 30 && !sujet; i++) {
  const liste = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + EMAIL)}`)).json();
  sujet = (liste.messages ?? []).map((m) => m.Subject).find((s) => /supprimé/i.test(s)) ?? null;
  if (!sujet) await new Promise((r) => setTimeout(r, 500));
}
ok(sujet === 'Votre compte a été supprimé', `e-mail de confirmation reçu (${sujet})`);

const reconnexion = await fetch(`${BASE}/api/auth/connexion`, {
  method: 'POST', headers: json, body: JSON.stringify({ email: EMAIL, motDePasse: MDP }),
});
ok(reconnexion.status === 401, `impossible de se reconnecter (HTTP ${reconnexion.status})`);

// Session fermée côté navigateur : /compte renvoie vers la connexion
await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
ok(page.url().includes('/connexion'), 'plus de session : /compte renvoie vers la connexion');

await navigateur.close();
console.log(echecs === 0 ? 'Toutes les vérifications passent.' : `${echecs} vérification(s) en échec.`);
process.exit(echecs === 0 ? 0 : 1);
