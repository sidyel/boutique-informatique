// LCP et élément LCP d'une page (réseau et processeur bridés comme un Android d'entrée de gamme).
// Usage : node mesure-lcp.mjs /panier [--panier]   (--panier : ajoute d'abord un article au panier du visiteur)
import { chromium } from 'playwright-core';

const BASE = process.env.BASE ?? 'http://localhost';
const CHROME = process.env.CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const chemin = process.argv[2] ?? '/';
const avecPanier = process.argv.includes('--panier');

const navigateur = await chromium.launch({ executablePath: CHROME, headless: true });
const resultats = [];
for (let i = 0; i < 5; i++) {
  const contexte = await navigateur.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true });
  if (avecPanier) {
    const accueil = await (await fetch(`${BASE}/api/accueil`)).json();
    const p = accueil.sections.flatMap((s) => s.produits).find((x) => x.slug);
    const detail = await (await fetch(`${BASE}/api/produits/${p.slug}`)).json();
    const r = await contexte.request.post(`${BASE}/api/panier/lignes`, {
      data: { varianteId: detail.variantes[0].id, quantite: 1 }, headers: { Origin: BASE },
    });
    if (!r.ok()) throw new Error('ajout au panier impossible : ' + r.status());
  }
  const page = await contexte.newPage();
  const cdp = await contexte.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 });
  await page.addInitScript(() => {
    window.__lcp = null;
    window.__cls = 0;
    new PerformanceObserver((l) => { for (const e of l.getEntries()) if (!e.hadRecentInput) window.__cls += e.value; }).observe({ type: 'layout-shift', buffered: true });
    new PerformanceObserver((l) => {
      const e = l.getEntries().at(-1);
      window.__lcp = { t: Math.round(e.startTime), el: e.element ? e.element.tagName + '.' + (e.element.className || '') + ' « ' + (e.element.textContent || '').trim().slice(0, 40) + ' »' : '?' };
    }).observe({ type: 'largest-contentful-paint', buffered: true });
  });
  await page.goto(BASE + chemin, { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  const mesure = await page.evaluate(() => ({ ...window.__lcp, cls: window.__cls }));
  if (i === 0 && process.env.CAPTURE) await page.screenshot({ path: process.env.CAPTURE, fullPage: true });
  resultats.push(mesure);
  await contexte.close();
}
await navigateur.close();
resultats.sort((a, b) => a.t - b.t);
console.log(`LCP ${chemin} : médiane ${resultats[2].t} ms — élément : ${resultats[2].el}`);
console.log('tous :', resultats.map((r) => r.t).join(', '), '| CLS max', Math.max(...resultats.map((r) => r.cls)).toFixed(4));
