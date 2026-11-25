"""Contrôle de sécurité (CI) : le conteneur frontend SSR ne reçoit aucun secret.

Résout la configuration docker compose (base + dev) et vérifie que l'environnement du service
« frontend » ne contient que des variables autorisées, et aucune variable sensible (clé JWT,
Cloudinary, mots de passe, jetons). N'affiche jamais les valeurs, seulement les noms.

Usage, depuis la racine du dépôt (un fichier .env doit exister, la CI copie .env.exemple) :
    python infra/scripts/verifier_env_frontend.py
"""
import json
import re
import subprocess
import sys

FICHIERS = ["infra/docker-compose.yml", "infra/docker-compose.dev.yml"]
AUTORISEES = {"PORT", "API_URL_SERVEUR", "NG_TRUST_PROXY_HEADERS", "NG_ALLOWED_HOSTS"}
SENSIBLES = re.compile(r"JWT|CLOUDINARY|SECRET|MOT_DE_PASSE|PASSWORD|JETON|CLE|TOKEN|PAYDUNYA|META_CAPI", re.I)


def environnement(config, service):
    env = config["services"][service].get("environment") or {}
    if isinstance(env, list):
        env = dict(ligne.split("=", 1) if "=" in ligne else (ligne, "") for ligne in env)
    return env


def main():
    commande = ["docker", "compose"]
    for fichier in FICHIERS:
        commande += ["-f", fichier]
    commande += ["config", "--format", "json"]
    resultat = subprocess.run(commande, capture_output=True, text=True)
    if resultat.returncode != 0:
        # stderr de compose ne contient pas les valeurs des variables
        print("docker compose config a échoué :", resultat.stderr.strip(), file=sys.stderr)
        return 2
    config = json.loads(resultat.stdout)

    frontend = environnement(config, "frontend")
    erreurs = []
    sensibles = sorted(nom for nom in frontend if SENSIBLES.search(nom))
    if sensibles:
        erreurs.append("variables sensibles transmises au frontend : " + ", ".join(sensibles))
    inattendues = sorted(set(frontend) - AUTORISEES)
    if inattendues:
        erreurs.append("variables non autorisées transmises au frontend : " + ", ".join(inattendues))
    if config["services"]["frontend"].get("env_file"):
        erreurs.append("le service frontend ne doit pas avoir d'env_file")

    # Témoin : le contrôle n'a de sens que si le backend, lui, reçoit bien la clé JWT
    if "JWT_SECRET" not in environnement(config, "backend"):
        erreurs.append("témoin absent : JWT_SECRET devrait être transmis au backend (contrôle inopérant ?)")

    if erreurs:
        for erreur in erreurs:
            print("ÉCHEC :", erreur, file=sys.stderr)
        return 1
    print("OK : le frontend ne reçoit que " + ", ".join(sorted(frontend)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
