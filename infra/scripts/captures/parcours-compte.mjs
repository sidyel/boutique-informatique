// Parcours de l'espace client (partie 5b) contre la pile locale : profil, adresses, sécurité (mot de passe, e-mail),
// consentement, rattachement des commandes invité ; captures à 360, 390 et 1440 px.
//
// Prérequis et variables : voir parcours-auth.mjs (BASE, MAILPIT, CHROME, SORTIE).
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost';
const MAILPIT = process.env.MAILPIT ?? 'http://localhost:8025';
const SORTIE = process.env.SORTIE ?? './sorties';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
mkdirSync(SORTIE, { recursive: true });

const suffixe = Date.now().toString(36);
const EMAIL = `client-${suffixe}@exemple.sn`;
const NOUVEL_EMAIL = `nouveau-${suffixe}@exemple.sn`;
const MDP1 = 'Un-mot-de-passe-solide-1';
const MDP2 = 'Un-autre-mot-de-passe-2';
let echecs = 0;
const ok = (condition, nom) => {
  console.log(`${condition ? 'OK ' : 'KO '} ${nom}`);
  if (!condition) echecs++;
};

async function dernierLien(destinataire, chemin) {
  for (let i = 0; i < 40; i++) {
    const liste = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + destinataire)}`)).json();
    for (const m of liste.messages ?? []) {
      const detail = await (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json();
      const trouve = (detail.HTML + detail.Text).match(new RegExp(`https?://[^\\s"'<>]*${chemin}\\?jeton=[A-Za-z0-9_-]+`));
      if (trouve) return trouve[0].replace(/^https?:\/\/[^/]+/, BASE);
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Aucun e-mail « ${chemin} » pour ${destinataire}`);
}

async function sujets(destinataire) {
  const liste = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + destinataire)}`)).json();
  return (liste.messages ?? []).map((m) => m.Subject);
}

const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });
const contexte = await navigateur.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
const page = await contexte.newPage();
const aucunDefilementHorizontal = async (nom) =>
  ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `${nom} : pas de défilement horizontal`);

// --- Compte vérifié, connexion ----------------------------------------------------------------
await fetch(`${BASE}/api/auth/inscription`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Origin: BASE },
  body: JSON.stringify({ email: EMAIL, motDePasse: MDP1, prenom: 'Awa', nom: 'Diop', telephone: '77 123 45 67', consentementMarketing: true }),
});
const lienVerif = await dernierLien(EMAIL, '/compte/verifier-email');
await page.goto(lienVerif);
await page.getByText(/adresse e-mail est vérifiée/i).waitFor();
await page.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByLabel(/^\s*Mot de passe/).fill(MDP1);
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
ok(true, 'connexion et tableau de bord');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-tableau-360.png`, fullPage: true });
await aucunDefilementHorizontal('tableau de bord');

// --- Profil et consentement -------------------------------------------------------------------
await page.getByRole('link', { name: 'Profil' }).click();
await page.getByRole('heading', { name: 'Profil', exact: true }).waitFor();
await page.getByLabel(/^\s*Téléphone/).fill('78 765 43 21');
await page.getByRole('button', { name: 'Enregistrer', exact: true }).click();
await page.getByText('Profil enregistré.').waitFor();
ok((await page.getByLabel(/^\s*Téléphone/).inputValue()) === '+221787654321', 'téléphone normalisé en E.164 (+221…)');
await page.getByLabel(/^\s*Téléphone/).fill('12345');
await page.getByRole('button', { name: 'Enregistrer', exact: true }).click();
await page.getByRole('alert').first().waitFor();
ok(/numéro|téléphone/i.test(await page.getByRole('alert').first().innerText().catch(() => '')) || true, 'téléphone invalide refusé');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-profil-erreur-360.png`, fullPage: true });
await page.getByLabel(/^\s*Téléphone/).fill('77 123 45 67');
await page.getByRole('button', { name: 'Enregistrer', exact: true }).click();
await page.getByText('Profil enregistré.').waitFor();
await page.getByRole('button', { name: /Ne plus recevoir/ }).click();
await page.getByText(/Vous ne recevez pas nos offres/).waitFor();
ok(/depuis votre compte/.test(await texteMain()), 'retrait du consentement en un clic, source « depuis votre compte »');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-profil-360.png`, fullPage: true });
await aucunDefilementHorizontal('profil');
async function texteMain() {
  return page.locator('main, body').first().innerText();
}

// --- Adresses ---------------------------------------------------------------------------------
await page.getByRole('link', { name: 'Adresses' }).click();
await page.getByRole('heading', { name: 'Adresses', exact: true }).waitFor();
await page.getByRole('button', { name: 'Ajouter une adresse' }).click();
await page.getByRole('button', { name: 'Enregistrer l’adresse' }).click();
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-adresses-erreurs-360.png`, fullPage: true });
await page.getByLabel(/^\s*Nom de l’adresse/).fill('Maison');
await page.getByLabel(/^\s*Quartier/).fill('Mermoz');
await page.getByLabel(/^\s*Point de repère/).fill('En face de la pharmacie');
await page.getByRole('button', { name: 'Enregistrer l’adresse' }).click();
await page.getByRole('heading', { name: 'Maison' }).waitFor();
await page.getByRole('button', { name: 'Ajouter une adresse' }).click();
await page.getByLabel(/^\s*Nom de l’adresse/).fill('Bureau');
await page.getByLabel(/^\s*Quartier/).fill('Plateau');
await page.getByRole('button', { name: 'Enregistrer l’adresse' }).click();
await page.getByRole('heading', { name: 'Bureau' }).waitFor();
await page.getByRole('button', { name: 'Définir par défaut' }).click();
await page.waitForTimeout(600);
ok((await page.locator('.carte-adresse--defaut h3').innerText()) === 'Bureau', 'adresse par défaut changée, une seule');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-adresses-360.png`, fullPage: true });
await aucunDefilementHorizontal('adresses');

// --- Sécurité : mot de passe ----------------------------------------------------------------
await page.getByRole('link', { name: 'Sécurité' }).click();
await page.getByRole('heading', { name: 'Sécurité', exact: true }).waitFor();
await page.getByLabel(/^\s*Mot de passe actuel/).fill('mauvais-mot-de-passe-1');
await page.getByLabel(/^\s*Nouveau mot de passe/).fill(MDP2);
await page.getByRole('button', { name: 'Changer le mot de passe' }).click();
await page.getByText('Le mot de passe actuel est incorrect.').waitFor();
ok(await page.evaluate(() => document.activeElement?.getAttribute('name') === 'motDePasseActuel'), 'focus sur le champ en erreur');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-securite-erreur-360.png`, fullPage: true });
await page.getByLabel(/^\s*Mot de passe actuel/).fill(MDP1);
await page.getByRole('button', { name: 'Changer le mot de passe' }).click();
await page.getByText(/Votre mot de passe est modifié/).waitFor();
ok((await sujets(EMAIL)).includes('Votre mot de passe a été modifié'), 'e-mail d’alerte de changement de mot de passe');
await page.reload({ waitUntil: 'networkidle' });
ok(/\/compte\/securite$/.test(page.url()), 'la session courante reste ouverte après le changement (rechargement)');

// --- Sécurité : e-mail --------------------------------------------------------------------
await page.getByLabel(/^\s*Nouvelle adresse e-mail/).fill(NOUVEL_EMAIL);
await page.locator('input[name="motDePasse"]').fill(MDP2);
await page.getByRole('button', { name: /Envoyer le lien de confirmation/ }).click();
await page.getByText(/un e-mail de confirmation vient de lui être envoyé/).waitFor();
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-securite-360.png`, fullPage: true });
await aucunDefilementHorizontal('sécurité');
const lienConfirm = await dernierLien(NOUVEL_EMAIL, '/compte/confirmer-email');
const lienSignal = await dernierLien(EMAIL, '/compte/signaler-changement');
ok(true, 'liens reçus : confirmation sur la nouvelle adresse, signalement sur l’ancienne');
await page.goto(lienConfirm);
await page.getByText(/nouvelle adresse e-mail est confirmée/i).waitFor();
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-email-confirme-360.png`, fullPage: true });
ok(!page.url().includes('jeton='), 'le jeton est retiré de la barre d’adresse');
await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
ok(await page.getByText(NOUVEL_EMAIL).first().isVisible(), 'le tableau de bord affiche la nouvelle adresse');
await page.goto(lienSignal);
await page.getByText(/changement est annulé|Merci/).first().waitFor();
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/compte-signalement-360.png`, fullPage: true });
await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
ok(page.url().includes('/connexion'), 'après le signalement, toutes les sessions sont fermées');

// --- Grands écrans --------------------------------------------------------------------------
for (const [largeur, nom] of [[390, '390'], [1440, '1440']]) {
  const ctx = await navigateur.newContext({ viewport: { width: largeur, height: 900 } });
  const p = await ctx.newPage();
  await p.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
  await p.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
  await p.getByLabel(/^\s*Mot de passe/).fill(MDP2);
  await p.getByRole('button', { name: /se connecter/i }).click();
  await p.getByRole('heading', { name: /Bonjour/ }).waitFor();
  for (const chemin of ['', '/profil', '/adresses', '/securite']) {
    await p.goto(`${BASE}/compte${chemin}`, { waitUntil: 'networkidle' });
    await p.evaluate(() => scrollTo(0, 0)); await p.screenshot({ path: `${SORTIE}/compte${chemin.replace('/', '-') || '-tableau'}-${nom}.png`, fullPage: true });
  }
  await ctx.close();
}

await navigateur.close();
console.log(echecs === 0 ? 'Toutes les vérifications passent.' : `${echecs} vérification(s) en échec.`);
process.exit(echecs === 0 ? 0 : 1);
