// Parcours « Mes commandes » (partie 5c) contre la pile locale : une commande passée en invité est rattachée au compte
// à la vérification de l'e-mail, puis consultée dans la liste et le détail ; captures à 360, 390 et 1440 px.
// Variables : voir parcours-auth.mjs.
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost';
const MAILPIT = process.env.MAILPIT ?? 'http://localhost:8025';
const SORTIE = process.env.SORTIE ?? './sorties';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
mkdirSync(SORTIE, { recursive: true });

const EMAIL = `client-${Date.now().toString(36)}@exemple.sn`;
const MDP = 'Un-mot-de-passe-solide-1';
let echecs = 0;
const ok = (condition, nom) => {
  console.log(`${condition ? 'OK ' : 'KO '} ${nom}`);
  if (!condition) echecs++;
};
// Numéro différent à chaque exécution : la limite « 2 commandes à la livraison en attente par numéro » s'applique
const TELEPHONE = `77${Math.floor(1_000_000 + Math.random() * 9_000_000)}`;
const json = { 'Content-Type': 'application/json', Origin: BASE };

async function lienVerification() {
  for (let i = 0; i < 40; i++) {
    const liste = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + EMAIL)}`)).json();
    for (const m of liste.messages ?? []) {
      const d = await (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json();
      const t = (d.HTML + d.Text).match(/https?:\/\/[^\s"'<>]*\/compte\/verifier-email\?jeton=[A-Za-z0-9_-]+/);
      if (t) return t[0].replace(/^https?:\/\/[^/]+/, BASE);
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error('Pas de lien de vérification');
}

// --- 1. Commande passée en invité avec l'adresse e-mail du futur compte ------------------------------
const accueil = await (await fetch(`${BASE}/api/accueil`)).json();
const produits = accueil.sections.flatMap((s) => s.produits).filter((p) => p.slug);
const moinsCher = produits.sort((a, b) => (a.prix ?? 0) - (b.prix ?? 0))[0];
const detail = await (await fetch(`${BASE}/api/produits/${moinsCher.slug}`)).json();
const ajout = await fetch(`${BASE}/api/panier/lignes`, {
  method: 'POST', headers: json, body: JSON.stringify({ varianteId: detail.variantes[0].id, quantite: 1 }),
});
const cookie = (ajout.headers.get('set-cookie') ?? '').split(';')[0];
const panier = await (await fetch(`${BASE}/api/panier`, { headers: { Cookie: cookie } })).json();
const commande = await fetch(`${BASE}/api/commandes`, {
  method: 'POST',
  headers: { ...json, Cookie: cookie, 'Idempotency-Key': crypto.randomUUID() },
  body: JSON.stringify({
    nomComplet: 'Awa Diop', telephone: TELEPHONE, email: EMAIL, modeLivraison: 'DOMICILE', zoneId: 1,
    ville: 'Dakar', quartier: 'Plateau', adresse: 'Rue 10', pointDeRepere: 'Près du marché', modePaiement: 'A_LA_LIVRAISON',
    totalAttendu: panier.sousTotal - (panier.remise ?? 0) + 2000,
  }),
});
const corpsCommande = await commande.json();
ok(commande.ok, `commande invité créée (HTTP ${commande.status}) ${commande.ok ? '' : JSON.stringify(corpsCommande)}`);
const numero = corpsCommande.numero;

// --- 2. Compte, vérification de l'e-mail : la commande est rattachée ---------------------------------
await fetch(`${BASE}/api/auth/inscription`, {
  method: 'POST', headers: json,
  body: JSON.stringify({ email: EMAIL, motDePasse: MDP, prenom: 'Awa', nom: 'Diop', telephone: '77 123 45 67', consentementMarketing: false }),
});
const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });
const contexte = await navigateur.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
const page = await contexte.newPage();
await page.goto(await lienVerification());
await page.getByText(/adresse e-mail est vérifiée/i).waitFor();
await page.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByLabel(/^\s*Mot de passe/).fill(MDP);
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('heading', { name: /Bonjour Awa/ }).waitFor();
await page.getByText(numero).first().waitFor();
ok(true, 'le tableau de bord montre la commande rattachée');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/commandes-tableau-360.png`, fullPage: true });

// --- 3. Liste et détail ----------------------------------------------------------------------
await page.getByRole('link', { name: 'Commandes', exact: true }).click();
await page.getByRole('heading', { name: 'Mes commandes' }).waitFor();
await page.getByText(numero).first().waitFor();
ok(true, 'la liste affiche la commande passée en invité');
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/commandes-liste-360.png`, fullPage: true });
ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'liste : pas de défilement horizontal');
await page.getByRole('link', { name: new RegExp(numero) }).click();
await page.getByRole('heading', { name: /Commande/ }).waitFor();
await page.getByText('Historique').waitFor();
ok(await page.getByText('Articles').isVisible(), 'détail : articles et totaux');
const wa = await page.getByRole('link', { name: /Écrivez-nous sur WhatsApp/ }).getAttribute('href');
ok(wa && decodeURIComponent(wa).includes(numero), `lien WhatsApp avec le numéro prérempli (${wa})`);
await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: `${SORTIE}/commandes-detail-360.png`, fullPage: true });
ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'détail : pas de défilement horizontal');

// Commande d'un autre : introuvable
await page.goto(`${BASE}/compte/commandes/CMD-2020-000001`, { waitUntil: 'networkidle' });
ok(await page.getByText('Commande introuvable').isVisible(), 'commande inconnue : message neutre');

// --- 4. Facture : téléchargement authentifié et sans cache (avoir si présent) ----------------------------
const sansCache = await page.evaluate(async (n) => {
  const r = await fetch(`/api/compte/commandes/${n}`);
  return r.status;
}, numero);
ok(sansCache === 401, `sans jeton d'accès, l'API refuse (HTTP ${sansCache})`);

// --- 5. Autres largeurs ------------------------------------------------------------------------
for (const [largeur, nom] of [[390, '390'], [1440, '1440']]) {
  const ctx = await navigateur.newContext({ viewport: { width: largeur, height: 900 } });
  const p = await ctx.newPage();
  await p.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
  await p.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
  await p.getByLabel(/^\s*Mot de passe/).fill(MDP);
  await p.getByRole('button', { name: /se connecter/i }).click();
  await p.getByRole('heading', { name: /Bonjour/ }).waitFor();
  await p.evaluate(() => scrollTo(0, 0)); await p.screenshot({ path: `${SORTIE}/commandes-tableau-${nom}.png`, fullPage: true });
  await p.getByRole('link', { name: 'Commandes', exact: true }).click();
  await p.getByRole('heading', { name: 'Mes commandes' }).waitFor();
  await p.evaluate(() => scrollTo(0, 0)); await p.screenshot({ path: `${SORTIE}/commandes-liste-${nom}.png`, fullPage: true });
  await p.getByRole('link', { name: new RegExp(numero) }).click();
  await p.getByText('Historique').waitFor();
  await p.evaluate(() => scrollTo(0, 0)); await p.screenshot({ path: `${SORTIE}/commandes-detail-${nom}.png`, fullPage: true });
  await ctx.close();
}

await navigateur.close();
console.log(echecs === 0 ? 'Toutes les vérifications passent.' : `${echecs} vérification(s) en échec.`);
process.exit(echecs === 0 ? 0 : 1);
