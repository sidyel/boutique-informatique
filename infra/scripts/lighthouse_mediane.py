#!/usr/bin/env python3
"""Lighthouse mobile, plusieurs passages par page, médiane de chaque métrique.

Lighthouse varie d'un passage à l'autre (charge de la machine, réseau simulé) : un seul passage ne prouve
rien. Ce script lance N passages par page, garde la médiane de chaque métrique et le rapport HTML du
passage médian (score de performance), puis applique les seuils du projet (CLAUDE.md, « Mobile d'abord ») :

  - ÉCHEC si LCP > 2,5 s, CLS > 0,1 ou poids du premier chargement > 1 Mo ;
  - simple AVERTISSEMENT si le score de performance est < 90.

Usage :
  python3 infra/scripts/lighthouse_mediane.py --base https://localhost --page accueil=/ \\
      [--page categorie=/categorie/pc-portables] [--passages 7] [--sortie rapports-lighthouse] \\
      [--certificat-autosigne] [--variante-demo apple-macbook-air-13-m2 --page-panier panier=/panier]

--page-panier nom=/chemin : page personnelle mesurée AVEC un panier de démonstration (panier, commande). Le script
crée un panier invité (1 exemplaire du produit --variante-demo) et le présente à Lighthouse par l'en-tête Cookie.

--certificat-autosigne : le site est servi en HTTPS avec un certificat autosigné (workflow Lighthouse, voir
ADR 0006) ; Chrome et la requête de chauffe acceptent alors ce certificat. Jamais pour un vrai site.

La commande Lighthouse se règle par la variable LIGHTHOUSE_CMD (défaut : « lighthouse »).
Le tableau récapitulatif est ajouté au résumé du job GitHub Actions (GITHUB_STEP_SUMMARY) s'il existe.
"""

from __future__ import annotations

import argparse
import ssl
import json
import os
import shlex
import shutil
import statistics
import subprocess
import sys
import urllib.request
from pathlib import Path

SEUIL_LCP_MS = 2500
SEUIL_CLS = 0.1
SEUIL_POIDS_OCTETS = 1_000_000  # 1 Mo
SEUIL_PERFORMANCE = 90

CATEGORIES = "performance,accessibility,best-practices,seo"


def lancer_lighthouse(url: str, destination: Path, certificat_autosigne: bool, entetes: dict | None = None) -> dict:
    commande = shlex.split(os.environ.get("LIGHTHOUSE_CMD", "lighthouse"))
    commande += [
        url,
        "--quiet",
        "--output=json",
        "--output=html",
        f"--output-path={destination}",
        f"--only-categories={CATEGORIES}",
        # Profil mobile par défaut de Lighthouse : téléphone moyen, 4G lente et processeur ralenti simulés
        "--chrome-flags=--headless=new --no-sandbox"
        + (" --ignore-certificate-errors" if certificat_autosigne else ""),
    ]
    if entetes:
        # Envoyés avec toutes les requêtes de la page, y compris les appels à l'API faits par Angular
        commande.append("--extra-headers=" + json.dumps(entetes))
    subprocess.run(commande, check=True)
    with open(f"{destination}.report.json", encoding="utf-8") as fichier:
        return json.load(fichier)


def metriques(rapport: dict) -> dict:
    audits = rapport["audits"]
    categories = rapport["categories"]
    return {
        "performance": round(categories["performance"]["score"] * 100),
        "accessibilite": round(categories["accessibility"]["score"] * 100),
        "bonnes_pratiques": round(categories["best-practices"]["score"] * 100),
        "seo": round(categories["seo"]["score"] * 100),
        "fcp_ms": audits["first-contentful-paint"]["numericValue"],
        "lcp_ms": audits["largest-contentful-paint"]["numericValue"],
        "cls": audits["cumulative-layout-shift"]["numericValue"],
        "tbt_ms": audits["total-blocking-time"]["numericValue"],
        "poids_octets": audits["total-byte-weight"]["numericValue"],
    }


def creer_panier_demo(base: str, produit: str, certificat_autosigne: bool) -> dict:
    """Panier invité d'un exemplaire de `produit` ; renvoie l'en-tête Cookie à présenter à Lighthouse."""
    contexte = ssl._create_unverified_context() if certificat_autosigne else None
    racine = base.rstrip("/")
    with urllib.request.urlopen(f"{racine}/api/produits/{produit}", timeout=60, context=contexte) as reponse:
        variante = json.load(reponse)["variantes"][0]["id"]
    requete = urllib.request.Request(
        f"{racine}/api/panier/lignes",
        data=json.dumps({"varianteId": variante, "quantite": 1}).encode(),
        headers={"Content-Type": "application/json", "Origin": racine},
        method="POST",
    )
    with urllib.request.urlopen(requete, timeout=60, context=contexte) as reponse:
        cookie = reponse.headers.get("Set-Cookie", "")
    jeton = cookie.split(";", 1)[0]
    if not jeton.startswith("panier_invite="):
        raise SystemExit(f"Panier de démonstration non créé (en-tête Set-Cookie : « {cookie[:40]} »)")
    return {"Cookie": jeton}


def ouvrir_session(base: str, email: str, mot_de_passe: str, certificat_autosigne: bool) -> str:
    """Connecte le compte de test et renvoie le cookie de rafraîchissement (`jeton_rafraichissement=…`).

    Le cookie tourne à chaque utilisation (rotation + détection de réutilisation) : une session neuve par passage,
    sinon le 2e passage présenterait un ancien cookie et passerait pour un vol (session révoquée)."""
    contexte = ssl._create_unverified_context() if certificat_autosigne else None
    racine = base.rstrip("/")
    requete = urllib.request.Request(
        f"{racine}/api/auth/connexion",
        data=json.dumps({"email": email, "motDePasse": mot_de_passe}).encode(),
        headers={"Content-Type": "application/json", "Origin": racine},
        method="POST",
    )
    with urllib.request.urlopen(requete, timeout=60, context=contexte) as reponse:
        cookie = reponse.headers.get("Set-Cookie", "")
    jeton = cookie.split(";", 1)[0]
    if not jeton.startswith("jeton_rafraichissement="):
        raise SystemExit("Connexion du compte de test impossible (pas de cookie de rafraîchissement)")
    return jeton


def mesurer_page(base: str, nom: str, chemin: str, passages: int, sortie: Path, certificat_autosigne: bool,
                 entetes: dict | None = None, compte: tuple[str, str] | None = None) -> dict:
    url = base.rstrip("/") + chemin
    # Chauffe : le premier rendu SSR (compilation à la volée de Node) ne doit pas fausser le 1er passage
    contexte = ssl._create_unverified_context() if certificat_autosigne else None
    urllib.request.urlopen(url, timeout=60, context=contexte).read()

    resultats = []
    for numero in range(1, passages + 1):
        destination = sortie / f"{nom}-{numero}"
        print(f"[{nom}] passage {numero}/{passages} : {url}", flush=True)
        entetes_passage = entetes
        if compte:
            # Session neuve à chaque passage ; le cookie du panier (s'il y en a un) est conservé
            session = ouvrir_session(base, compte[0], compte[1], certificat_autosigne)
            entetes_passage = {"Cookie": "; ".join(filter(None, [(entetes or {}).get("Cookie"), session]))}
        resultats.append(metriques(lancer_lighthouse(url, destination, certificat_autosigne, entetes_passage)))

    mediane = {cle: statistics.median(r[cle] for r in resultats) for cle in resultats[0]}
    # Rapport HTML conservé : celui du passage dont le score de performance est le plus proche de la médiane
    indice = min(range(passages), key=lambda i: abs(resultats[i]["performance"] - mediane["performance"]))
    shutil.copyfile(sortie / f"{nom}-{indice + 1}.report.html", sortie / f"{nom}-mediane.report.html")
    return {"nom": nom, "url": url, "mediane": mediane, "passages": resultats}


def verdicts(page: dict) -> tuple[list[str], list[str]]:
    m = page["mediane"]
    echecs, avertissements = [], []
    if m["lcp_ms"] > SEUIL_LCP_MS:
        echecs.append(f"{page['nom']} : LCP {m['lcp_ms'] / 1000:.2f} s > {SEUIL_LCP_MS / 1000} s")
    if m["cls"] > SEUIL_CLS:
        echecs.append(f"{page['nom']} : CLS {m['cls']:.3f} > {SEUIL_CLS}")
    if m["poids_octets"] > SEUIL_POIDS_OCTETS:
        echecs.append(f"{page['nom']} : poids {m['poids_octets'] / 1000:.0f} Ko > 1 Mo")
    if m["performance"] < SEUIL_PERFORMANCE:
        avertissements.append(f"{page['nom']} : performance {m['performance']:.0f} < {SEUIL_PERFORMANCE}")
    return echecs, avertissements


def tableau(pages: list[dict], passages: int) -> str:
    lignes = [
        f"### Lighthouse mobile : médiane de {passages} passages",
        "",
        "| Page | Perf. | LCP | CLS | TBT | Poids | Access. | Bonnes prat. | SEO | Perf. des passages |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for page in pages:
        m = page["mediane"]
        detail = ", ".join(str(r["performance"]) for r in page["passages"])
        lignes.append(
            f"| {page['nom']} (`{page['url']}`) | {m['performance']:.0f} | {m['lcp_ms'] / 1000:.2f} s "
            f"| {m['cls']:.3f} | {m['tbt_ms']:.0f} ms | {m['poids_octets'] / 1000:.0f} Ko "
            f"| {m['accessibilite']:.0f} | {m['bonnes_pratiques']:.0f} | {m['seo']:.0f} | {detail} |"
        )
    lignes += [
        "",
        f"Seuils : échec si LCP > {SEUIL_LCP_MS / 1000} s, CLS > {SEUIL_CLS} ou poids > 1 Mo ; "
        f"avertissement si performance < {SEUIL_PERFORMANCE}.",
    ]
    return "\n".join(lignes)


def main() -> int:
    analyseur = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    analyseur.add_argument("--base", default="https://localhost")
    analyseur.add_argument("--page", action="append", required=True, help="nom=/chemin (répétable)")
    analyseur.add_argument("--page-panier", action="append", default=[],
                           help="nom=/chemin mesuré avec un panier de démonstration (répétable)")
    analyseur.add_argument("--page-connectee", action="append", default=[],
                           help="nom=/chemin mesuré connecté (compte de test, session neuve à chaque passage) ; "
                                "mot de passe dans la variable LIGHTHOUSE_COMPTE_MOT_DE_PASSE (répétable)")
    analyseur.add_argument("--page-connectee-panier", action="append", default=[],
                           help="comme --page-connectee, avec en plus le panier de démonstration")
    analyseur.add_argument("--compte-email", default="client@exemple.sn", help="e-mail du compte de test")
    analyseur.add_argument("--variante-demo", help="slug du produit mis dans le panier de démonstration")
    analyseur.add_argument("--passages", type=int, default=7)
    analyseur.add_argument("--sortie", default="rapports-lighthouse")
    analyseur.add_argument(
        "--certificat-autosigne",
        action="store_true",
        help="accepter le certificat autosigné du site (workflow Lighthouse uniquement)",
    )
    args = analyseur.parse_args()

    sortie = Path(args.sortie)
    sortie.mkdir(parents=True, exist_ok=True)

    pages = []
    for definition in args.page:
        nom, _, chemin = definition.partition("=")
        if not nom or not chemin.startswith("/"):
            analyseur.error(f"page invalide : « {definition} » (attendu : nom=/chemin)")
        pages.append(mesurer_page(args.base, nom, chemin, args.passages, sortie, args.certificat_autosigne))
    if args.page_panier:
        if not args.variante_demo:
            analyseur.error("--page-panier exige --variante-demo")
        entetes = creer_panier_demo(args.base, args.variante_demo, args.certificat_autosigne)
        for definition in args.page_panier:
            nom, _, chemin = definition.partition("=")
            if not nom or not chemin.startswith("/"):
                analyseur.error(f"page invalide : « {definition} » (attendu : nom=/chemin)")
            pages.append(mesurer_page(args.base, nom, chemin, args.passages, sortie, args.certificat_autosigne, entetes))

    if args.page_connectee or args.page_connectee_panier:
        mot_de_passe = os.environ.get("LIGHTHOUSE_COMPTE_MOT_DE_PASSE", "")
        if not mot_de_passe:
            analyseur.error("les pages connectées exigent LIGHTHOUSE_COMPTE_MOT_DE_PASSE")
        compte = (args.compte_email, mot_de_passe)
        avec_panier = [(d, True) for d in args.page_connectee_panier]
        sans_panier = [(d, False) for d in args.page_connectee]
        if avec_panier and not args.variante_demo:
            analyseur.error("--page-connectee-panier exige --variante-demo")
        for definition, panier in sans_panier + avec_panier:
            nom, _, chemin = definition.partition("=")
            if not nom or not chemin.startswith("/"):
                analyseur.error(f"page invalide : « {definition} » (attendu : nom=/chemin)")
            entetes = creer_panier_demo(args.base, args.variante_demo, args.certificat_autosigne) if panier else None
            pages.append(mesurer_page(args.base, nom, chemin, args.passages, sortie, args.certificat_autosigne,
                                      entetes, compte))

    resume = tableau(pages, args.passages)
    print(resume)
    (sortie / "resume.md").write_text(resume + "\n", encoding="utf-8")
    (sortie / "medianes.json").write_text(json.dumps(pages, indent=2, ensure_ascii=False), encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as fichier:
            fichier.write(resume + "\n")

    tous_echecs = []
    for page in pages:
        m = page["mediane"]
        # Annotation lisible sans téléchargement des artefacts (API publique des « check runs »)
        print(
            f"::notice title=Lighthouse {page['nom']} (médiane)::performance {m['performance']:.0f}, "
            f"LCP {m['lcp_ms'] / 1000:.2f} s, CLS {m['cls']:.3f}, TBT {m['tbt_ms']:.0f} ms, "
            f"poids {m['poids_octets'] / 1000:.0f} Ko ; passages : "
            + ", ".join(str(r["performance"]) for r in page["passages"])
        )
        echecs, avertissements = verdicts(page)
        for message in avertissements:
            print(f"::warning title=Lighthouse::{message}")
        for message in echecs:
            print(f"::error title=Lighthouse::{message}")
        tous_echecs += echecs
    return 1 if tous_echecs else 0


if __name__ == "__main__":
    sys.exit(main())
