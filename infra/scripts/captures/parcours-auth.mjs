// Parcours de bout en bout de l'authentification (partie 5a) contre la pile locale, avec captures à 360, 390 et 1440 px.
//
// Prérequis : la pile de développement tourne (http://localhost, Mailpit sur http://localhost:8025) et
//   cd infra/scripts/captures && npm install --no-save playwright-core
// Variables : BASE (http://localhost), MAILPIT (http://localhost:8025), CHROME (chemin de Chrome/Chromium),
//             SORTIE (dossier des captures, ./sorties par défaut, ignoré par Git).
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost';
const MAILPIT = process.env.MAILPIT ?? 'http://localhost:8025';
const SORTIE = process.env.SORTIE ?? './sorties';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
mkdirSync(SORTIE, { recursive: true });

const suffixe = Date.now().toString(36);
const EMAIL = `client-${suffixe}@exemple.sn`;
const MDP1 = 'Un-mot-de-passe-solide-1';
const MDP2 = 'Un-autre-mot-de-passe-2';
let echecs = 0;
const ok = (condition, nom) => {
  console.log(`${condition ? 'OK ' : 'KO '} ${nom}`);
  if (!condition) echecs++;
};

async function dernierLien(destinataire, chemin) {
  for (let i = 0; i < 30; i++) {
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

const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });
const contexte = await navigateur.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
const page = await contexte.newPage();
const texte = () => page.locator('main, body').first().innerText();

// --- Inscription -----------------------------------------------------------------------------
await page.goto(`${BASE}/inscription`, { waitUntil: 'networkidle' });
await page.getByLabel(/^\s*Prénom/).fill('Awa');
await page.getByLabel(/^\s*Nom/).fill('Diop');
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByLabel(/^\s*Mot de passe/).fill(MDP1);
await page.getByRole('button', { name: /créer mon compte/i }).click();
await page.getByText(/e-mail de confirmation/i).waitFor();
ok(true, 'inscription : message de confirmation');

// --- Vérification de l'adresse par le lien reçu --------------------------------------------------
const lienVerif = await dernierLien(EMAIL, '/compte/verifier-email');
const reponseLien = page.waitForResponse((r) => r.url().includes('/compte/verifier-email') && r.request().resourceType() === 'document');
await page.goto(lienVerif);
const enTetes = (await reponseLien).headers();
ok(enTetes['referrer-policy'] === 'no-referrer', `lien de vérification : Referrer-Policy = ${enTetes['referrer-policy']}`);
ok(/no-store/.test(enTetes['cache-control'] ?? ''), `lien de vérification : Cache-Control = ${enTetes['cache-control']}`);
await page.getByText(/adresse e-mail est vérifiée/i).waitFor();
ok(!page.url().includes('jeton='), 'le jeton est retiré de la barre d’adresse');
ok((await page.locator('meta[name=referrer]').getAttribute('content')) === 'no-referrer', 'balise meta referrer posée');
await page.screenshot({ path: `${SORTIE}/verification-360.png`, fullPage: true });
await page.goto(lienVerif);
await page.getByText(/n’est plus valide/i).waitFor();
ok(true, 'le lien déjà utilisé est refusé');

// --- Panier invité puis connexion : le panier est fusionné ---------------------------------------
await page.goto(`${BASE}/produit/apple-macbook-air-13-m2`, { waitUntil: 'networkidle' });
await page.getByRole('button', { name: /ajouter au panier/i }).first().click();
await page.waitForTimeout(1200);
await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
ok(page.url().includes('/connexion?retour=%2Fcompte'), `zone privée sans session : redirigé vers ${new URL(page.url()).pathname}${new URL(page.url()).search}`);
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByLabel(/^\s*Mot de passe/).fill('mauvais-mot-de-passe');
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('alert').waitFor();
ok(/incorrect/i.test(await page.getByRole('alert').innerText()), 'mauvais mot de passe : message générique');
await page.getByLabel(/^\s*Mot de passe/).fill(MDP1);
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
ok(new URL(page.url()).pathname === '/compte', 'connexion : retour à la page demandée');
// Le serveur fusionne le panier invité à la première lecture du client connecté (le cookie invité est alors effacé)
await page.goto(`${BASE}/panier`, { waitUntil: 'networkidle' });
const vuPanier = await texte(); if (!/MacBook Air/i.test(vuPanier)) console.log(vuPanier.slice(0, 400));
ok(/MacBook Air/i.test(vuPanier), 'panier invité retrouvé dans le panier du compte après la connexion');
await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
const stockage = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie);
ok(!/eyJ/.test(stockage), 'aucun jeton JWT dans localStorage, sessionStorage ni cookie lisible');

// --- Rechargement et deux onglets simultanés : un seul rafraîchissement -------------------------
await page.reload({ waitUntil: 'networkidle' });
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
ok(true, 'rechargement : session retrouvée par le cookie');
const second = await contexte.newPage();
await Promise.all([page.reload({ waitUntil: 'networkidle' }), second.goto(`${BASE}/compte`, { waitUntil: 'networkidle' })]);
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
await second.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
await Promise.all([page.reload({ waitUntil: 'networkidle' }), second.reload({ waitUntil: 'networkidle' })]);
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
await second.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
ok(true, 'deux onglets rechargés en même temps : les deux restent connectés');
await second.close();

// --- Déconnexion ------------------------------------------------------------------------------------
await page.getByRole('button', { name: /se déconnecter/i }).click();
await page.waitForURL(`${BASE}/`);
await page.goto(`${BASE}/compte`, { waitUntil: 'networkidle' });
ok(new URL(page.url()).pathname === '/connexion', 'après déconnexion : /compte renvoie vers la connexion');

// --- Mot de passe oublié -----------------------------------------------------------------------------
await page.goto(`${BASE}/mot-de-passe-oublie`, { waitUntil: 'networkidle' });
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByRole('button', { name: /envoyer le lien/i }).click();
await page.getByText(/Si un compte existe/i).waitFor();
const lienMdp = await dernierLien(EMAIL, '/compte/nouveau-mot-de-passe');
await page.goto(lienMdp, { waitUntil: 'networkidle' });
ok(!page.url().includes('jeton='), 'réinitialisation : jeton retiré de la barre d’adresse');
ok((await page.locator('input[name=motDePasse]').getAttribute('autocomplete')) === 'new-password', 'nouveau mot de passe : autocomplete = new-password');
ok(/au moins 10 caractères/i.test(await texte()), 'nouveau mot de passe : règles affichées');
await page.getByLabel(/^\s*Nouveau mot de passe/).fill(MDP2);
await page.screenshot({ path: `${SORTIE}/nouveau-mot-de-passe-360.png`, fullPage: true });
await page.getByRole('button', { name: /enregistrer/i }).click();
await page.getByText(/mot de passe est modifié/i).waitFor();
await page.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByLabel(/^\s*Mot de passe/).fill(MDP2);
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
ok(true, 'connexion avec le nouveau mot de passe');
await page.getByRole('button', { name: /se déconnecter/i }).click();
await page.waitForURL(`${BASE}/`);


// --- Confort des formulaires sur mobile (360 px) ---------------------------------------------------------------
const actif = () => page.evaluate(() => document.activeElement?.getAttribute('name') ?? document.activeElement?.tagName);
const attribut = (sel, nom) => page.locator(sel).first().getAttribute(nom);
await page.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
ok((await attribut('input[name=email]', 'autocomplete')) === 'username', 'connexion : autocomplete e-mail = username (gestionnaires de mots de passe)');
ok((await attribut('input[name=motDePasse]', 'autocomplete')) === 'current-password', 'connexion : autocomplete mot de passe = current-password');
ok((await attribut('input[name=email]', 'type')) === 'email' && (await attribut('input[name=email]', 'inputmode')) === 'email', 'connexion : clavier e-mail');
await page.getByRole('button', { name: /se connecter/i }).click();
await page.waitForTimeout(300);
ok((await actif()) === 'email', `connexion vide : focus sur le premier champ en erreur (${await actif()})`);
await page.screenshot({ path: `${SORTIE}/connexion-erreurs-360.png`, fullPage: true });
await page.getByLabel(/^\s*Adresse e-mail/).fill('inconnu@exemple.sn');
await page.getByLabel(/^\s*Mot de passe/).fill('un-mot-de-passe-faux');
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('alert').first().waitFor();
await page.waitForTimeout(300);
ok((await actif()) === 'motDePasse', `mauvais identifiants : focus sur le mot de passe (${await actif()})`);
await page.screenshot({ path: `${SORTIE}/connexion-refusee-360.png`, fullPage: true });

await page.goto(`${BASE}/inscription`, { waitUntil: 'networkidle' });
ok((await attribut('input[name=email]', 'autocomplete')) === 'email', 'inscription : autocomplete e-mail = email');
ok((await attribut('input[name=motDePasse]', 'autocomplete')) === 'new-password', 'inscription : autocomplete mot de passe = new-password');
ok((await attribut('input[name=prenom]', 'autocomplete')) === 'given-name' && (await attribut('input[name=nom]', 'autocomplete')) === 'family-name', 'inscription : autocomplete prénom et nom');
ok(/au moins 10 caractères/i.test(await texte()) && /72 octets/i.test(await texte()), 'inscription : règles du mot de passe affichées avant toute saisie');
await page.screenshot({ path: `${SORTIE}/inscription-regles-360.png`, fullPage: true });
await page.getByRole('button', { name: /créer mon compte/i }).click();
await page.waitForTimeout(300);
ok((await actif()) === 'prenom', `inscription vide : focus sur le premier champ en erreur (${await actif()})`);
await page.screenshot({ path: `${SORTIE}/inscription-erreurs-360.png`, fullPage: true });
await page.getByLabel(/^\s*Mot de passe/).fill('court');
const champMdp = page.locator('input[name=motDePasse]');
ok((await champMdp.getAttribute('type')) === 'password', 'mot de passe masqué par défaut');
await page.getByRole('button', { name: /^Afficher$/ }).click();
ok((await champMdp.getAttribute('type')) === 'text', 'bouton Afficher : mot de passe visible');
ok((await page.getByRole('button', { name: /^Masquer$/ }).getAttribute('aria-pressed')) === 'true', 'bouton Masquer : état annoncé (aria-pressed)');
const bascule = await page.locator('.champ__bascule').first().boundingBox();
ok(bascule.height >= 43.5 && bascule.width >= 43.5, `bouton Afficher/Masquer : zone tactile ${Math.round(bascule.width)}×${Math.round(bascule.height)} px`);
await page.getByRole('button', { name: /^Masquer$/ }).click();
ok((await champMdp.getAttribute('type')) === 'password', 'bouton Masquer : mot de passe de nouveau masqué');
await page.getByLabel(/^\s*Prénom/).fill('Awa');
await page.getByLabel(/^\s*Nom/).fill('Diop');
await page.getByLabel(/^\s*Adresse e-mail/).fill('pas-un-email');
await page.getByRole('button', { name: /créer mon compte/i }).click();
await page.waitForTimeout(300);
ok((await actif()) === 'email', `e-mail invalide : focus sur le champ e-mail, pas sur le premier (${await actif()})`);
ok(/trop court/i.test(await texte()), 'mot de passe trop court : message clair en français');
await page.screenshot({ path: `${SORTIE}/inscription-erreurs-2-360.png`, fullPage: true });

await page.goto(`${BASE}/mot-de-passe-oublie`, { waitUntil: 'networkidle' });
await page.getByRole('button', { name: /envoyer le lien/i }).click();
await page.waitForTimeout(300);
ok((await actif()) === 'email', `mot de passe oublié vide : focus sur le champ e-mail (${await actif()})`);
await page.screenshot({ path: `${SORTIE}/mot-de-passe-oublie-erreur-360.png`, fullPage: true });

// --- Captures à 360, 390 et 1440 px, défilement horizontal compris -----------------------------------
for (const largeur of [360, 390, 1440]) {
  const mobile = largeur < 1000;
  const ctx = await navigateur.newContext({ viewport: { width: largeur, height: mobile ? 760 : 900 }, isMobile: mobile, hasTouch: mobile });
  const p = await ctx.newPage();
  for (const [nom, chemin] of [['connexion', '/connexion'], ['inscription', '/inscription'], ['mot-de-passe-oublie', '/mot-de-passe-oublie']]) {
    await p.goto(BASE + chemin, { waitUntil: 'networkidle' });
    await p.screenshot({ path: `${SORTIE}/${nom}-${largeur}.png`, fullPage: true });
    const debordement = await p.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1);
    ok(!debordement, `${nom} à ${largeur} px : pas de défilement horizontal`);
    const petits = await p.evaluate(() =>
      [...document.querySelectorAll('main a, main button, main input')].filter((e) => {
        // Un champ se touche sur toute sa zone (bordure comprise), pas seulement sur l'élément <input>
        const b = (e.closest('.champ__zone') ?? e).getBoundingClientRect();
        return b.width > 0 && e.type !== 'checkbox' && (b.height < 43.5 || (e.tagName === 'INPUT' && parseFloat(getComputedStyle(e).fontSize) < 16));
      }).map((e) => `${e.tagName} ${(e.textContent || e.name || '').trim().slice(0, 25)}`),
    );
    ok(petits.length === 0, `${nom} à ${largeur} px : zones tactiles ≥ 44 px et champs ≥ 16 px${petits.length ? ' → ' + petits.join(', ') : ''}`);
  }
  await ctx.close();
}

await navigateur.close();
console.log(echecs ? `\n${echecs} vérification(s) en échec` : '\nToutes les vérifications passent');
process.exit(echecs ? 1 : 0);
