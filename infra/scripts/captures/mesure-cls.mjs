// Mesure les décalages de mise en page (CLS) d'une page connectée et nomme les éléments qui bougent.
// Usage : node mesure-cls.mjs /compte   (BASE, CHROME, DEMO_CLIENT_EMAIL, DEMO_CLIENT_MOT_DE_PASSE comme ailleurs)
import { chromium } from 'playwright-core';

const BASE = process.env.BASE ?? 'http://localhost';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const EMAIL = process.env.DEMO_CLIENT_EMAIL ?? 'client@exemple.sn';
const MDP = process.env.DEMO_CLIENT_MOT_DE_PASSE ?? 'Client-Demo-Local-2026';
const chemin = process.argv[2] ?? '/compte';

const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });
const contexte = await navigateur.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
const page = await contexte.newPage();
await page.goto(`${BASE}/connexion`, { waitUntil: 'networkidle' });
await page.getByLabel(/^\s*Adresse e-mail/).fill(EMAIL);
await page.getByLabel(/^\s*Mot de passe/).fill(MDP);
await page.getByRole('button', { name: /se connecter/i }).click();
await page.getByRole('heading', { name: /Bonjour/ }).waitFor();

await page.addInitScript(() => {
  window.__cls = [];
  new PerformanceObserver((liste) => {
    for (const e of liste.getEntries()) {
      if (e.hadRecentInput) continue;
      window.__cls.push({
        valeur: e.value,
        t: Math.round(e.startTime),
        sources: e.sources.map((s) => `${s.node?.nodeName}.${(s.node?.className ?? '').toString().slice(0, 40)} ${s.previousRect.y | 0}->${s.currentRect.y | 0}`),
      });
    }
  }).observe({ type: 'layout-shift', buffered: true });
});
await page.goto(`${BASE}${chemin}`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
const decalages = await page.evaluate(() => window.__cls);
const total = decalages.reduce((s, d) => s + d.valeur, 0);
console.log(`CLS ${chemin} : ${total.toFixed(4)}`);
for (const d of decalages) console.log(` ${d.valeur.toFixed(4)} à ${d.t} ms : ${d.sources.join(' | ')}`);
await navigateur.close();
