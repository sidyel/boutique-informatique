// Parcours de l'administration (phase 6a) contre la pile locale : connexion en deux temps, équipe, journal, mon compte.
// Captures à 360, 768 et 1440 px, et contrôle qu'aucun défilement horizontal parasite n'apparaît.
//
// Prérequis : pile de développement lancée avec DEMO_ADMIN_MOT_DE_PASSE et DEMO_ADMIN_TOTP_SECRET définis, puis
//   cd infra/scripts/captures && npm install --no-save playwright-core
// Variables : BASE (http://localhost), CHROME, SORTIE (./sorties), ADMIN_EMAIL (admin@exemple.sn),
//             ADMIN_MOT_DE_PASSE et ADMIN_TOTP_SECRET (mêmes valeurs que DEMO_ADMIN_*).
import { chromium } from 'playwright-core';
import { createHmac } from 'node:crypto';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE ?? 'http://localhost';
const SORTIE = process.env.SORTIE ?? './sorties';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const EMAIL = process.env.ADMIN_EMAIL ?? 'admin@exemple.sn';
const MDP = process.env.ADMIN_MOT_DE_PASSE ?? '';
const SECRET = process.env.ADMIN_TOTP_SECRET ?? '';
if (!MDP || !SECRET) throw new Error('ADMIN_MOT_DE_PASSE et ADMIN_TOTP_SECRET sont requis');
mkdirSync(SORTIE, { recursive: true });

let echecs = 0;
const ok = (condition, nom) => {
  console.log(`${condition ? 'OK ' : 'KO '} ${nom}`);
  if (!condition) echecs++;
};

function base32(texte) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of texte.replace(/=+$/, '').toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, '0');
  const octets = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) octets.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(octets);
}

let dernierPas = 0;
async function codeTotp() {
  let pas = Math.floor(Date.now() / 30000);
  if (pas <= dernierPas) {
    await new Promise((r) => setTimeout(r, (dernierPas + 1) * 30000 - Date.now() + 500));
    pas = Math.floor(Date.now() / 30000);
  }
  dernierPas = pas;
  const compteur = Buffer.alloc(8);
  compteur.writeBigUInt64BE(BigInt(pas));
  const h = createHmac('sha1', base32(SECRET)).update(compteur).digest();
  const o = h[h.length - 1] & 0x0f;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });
const LARGEURS = [
  { nom: '360', largeur: 360, hauteur: 740, mobile: true },
  { nom: '768', largeur: 768, hauteur: 1024, mobile: false },
  { nom: '1440', largeur: 1440, hauteur: 900, mobile: false },
];

for (const v of LARGEURS) {
  const contexte = await navigateur.newContext({
    viewport: { width: v.largeur, height: v.hauteur },
    isMobile: v.mobile,
    hasTouch: v.mobile,
  });
  const page = await contexte.newPage();
  const verifierDebordement = async (nom) => {
    const deborde = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    ok(!deborde, `${v.nom} px — ${nom} : aucun défilement horizontal`);
  };
  const capturer = (nom) => page.screenshot({ path: `${SORTIE}/admin-${nom}-${v.nom}.png`, fullPage: true });

  // Sans session : /admin renvoie vers la connexion de l'équipe
  await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
  ok(page.url().includes('/admin/connexion'), `${v.nom} px — /admin sans session renvoie vers /admin/connexion`);
  await capturer('connexion');
  await verifierDebordement('connexion');

  // Mot de passe, puis code TOTP
  await page.getByLabel(/Adresse e-mail/).fill(EMAIL);
  await page.getByLabel(/Mot de passe/).fill(MDP);
  await page.getByRole('button', { name: /continuer/i }).click();
  await page.getByLabel(/Code de votre application/).waitFor();
  await capturer('code');
  await page.getByLabel(/Code de votre application/).fill(await codeTotp());
  await page.getByRole('button', { name: /se connecter/i }).click();
  await page.waitForURL(`${BASE}/admin`);
  ok(true, `${v.nom} px — connexion en deux temps réussie`);
  await capturer('tableau-de-bord');
  await verifierDebordement('tableau de bord');

  for (const [chemin, nom] of [['/admin/utilisateurs', 'equipe'], ['/admin/journal', 'journal'], ['/admin/mon-compte', 'mon-compte']]) {
    await page.goto(`${BASE}${chemin}`, { waitUntil: 'networkidle' });
    await capturer(nom);
    await verifierDebordement(nom);
  }

  // En-têtes de la page d'administration : jamais en cache, jamais indexée
  const reponse = await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
  ok((reponse.headers()['cache-control'] ?? '').includes('no-store'), `${v.nom} px — Cache-Control: no-store`);
  ok((reponse.headers()['x-robots-tag'] ?? '').includes('noindex'), `${v.nom} px — X-Robots-Tag: noindex`);
  await contexte.close();
}

await navigateur.close();
console.log(echecs === 0 ? 'Tous les contrôles passent.' : `${echecs} contrôle(s) en échec.`);
process.exit(echecs === 0 ? 0 : 1);
