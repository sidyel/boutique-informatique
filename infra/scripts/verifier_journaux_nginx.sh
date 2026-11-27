#!/usr/bin/env bash
# Vérifie que nginx n'écrit JAMAIS un jeton secret dans ses journaux (ADR 0008 §8) : jeton de suivi de commande
# (/suivi/{jeton}, /api/commandes/suivi/{jeton}/…) et jetons des e-mails de compte (?jeton=… : vérification,
# réinitialisation du mot de passe, déverrouillage), y compris dans l'en-tête Referer.
#
# Lance nginx avec les vrais modèles de configuration (mode HTTP) sur un réseau Docker jetable, sans backend ni
# SSR (les requêtes finissent en 502, ce qui suffit : le journal est écrit à la fin de chaque requête), envoie des
# requêtes portant un jeton reconnaissable, puis cherche ce jeton dans tous les journaux du conteneur.
#
# Usage : infra/scripts/verifier_journaux_nginx.sh        (depuis n'importe quel dossier ; Docker requis)
set -euo pipefail

# Git Bash (Windows) transforme les chemins commençant par « / » en arguments de docker : on l'en empêche
export MSYS_NO_PATHCONV=1

racine="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
nginx="$racine/infra/nginx"
nom="nginx-verif-journaux-$$"
reseau="reseau-verif-journaux-$$"
jeton="JETONSECRET0123456789abcdefghijklmnopqrstuvwxyz"

nettoyer() {
  docker rm -f "$nom" >/dev/null 2>&1 || true
  docker network rm "$reseau" >/dev/null 2>&1 || true
}
trap nettoyer EXIT

docker network create "$reseau" >/dev/null
docker run -d --name "$nom" --network "$reseau" -p 127.0.0.1::80 \
  -v "$nginx/default.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  -v "$nginx/commun/emplacements.conf.template:/etc/nginx/templates/commun/emplacements.conf.template:ro" \
  -v "$nginx/modes/http.conf.template:/etc/nginx/templates/mode/serveurs.conf.template:ro" \
  -e NGINX_DEBIT_API=1000r/s -e NGINX_RAFALE_API=1000 -e NGINX_DEBIT_RECHERCHE=1000r/s -e NGINX_RAFALE_RECHERCHE=1000 \
  -e NGINX_DEBIT_PAGES=1000r/s -e NGINX_RAFALE_PAGES=1000 \
  nginx:1.29-alpine >/dev/null

port="$(docker port "$nom" 80/tcp | head -n1 | sed 's/.*://')"
base="http://127.0.0.1:$port"
for _ in $(seq 1 30); do
  curl -s -o /dev/null "$base/robots.txt" && break
  sleep 1
done

requete() { curl -s -o /dev/null --max-time 15 "$@" || true; }
requete "$base/suivi/$jeton"
requete "$base/suivi/$jeton?nouvelle=1&retour=reprise"
requete "$base/api/commandes/suivi/$jeton"
requete "$base/commande/retour/$jeton"
requete "$base/commande/retour/$jeton?annule=1"
requete "$base/api/commandes/retour/$jeton"
requete -X POST "$base/api/commandes/suivi/$jeton/reprise-paiement"
requete -X POST "$base/api/commandes/suivi/$jeton/achat-suivi"
requete "$base/compte/verifier-email?jeton=$jeton"
requete "$base/compte/nouveau-mot-de-passe?jeton=$jeton"
requete "$base/compte/deverrouiller?jeton=$jeton"
requete "$base/une/page?x=1&token=$jeton"
requete "$base/une/page?jeton=$jeton&jeton=$jeton"
requete "$base/polices/inter.woff2" -H "Referer: $base/suivi/$jeton"
requete "$base/polices/inter.woff2" -H "Referer: $base/compte/nouveau-mot-de-passe?jeton=$jeton"
requete "$base/suivi/%0Ainjection$jeton"
requete "$base/categorie/pc-portables?page=2&tri=prix"
sleep 1

journaux="$(docker logs "$nom" 2>&1)"
if grep -q "JETONSECRET" <<<"$journaux"; then
  echo "ECHEC : un jeton apparaît dans les journaux nginx :" >&2
  grep "JETONSECRET" <<<"$journaux" >&2
  exit 1
fi
# Le journal doit rester utile : chemins, paramètres ordinaires et statut conservés
grep -q '"GET /suivi/\[jeton\]"' <<<"$journaux" || { echo "ECHEC : chemin de suivi absent ou mal masqué" >&2; exit 1; }
grep -q '"GET /categorie/pc-portables?page=2&tri=prix"' <<<"$journaux" \
  || { echo "ECHEC : les paramètres ordinaires doivent rester dans le journal" >&2; exit 1; }
echo "OK : aucun jeton dans les journaux nginx ($(grep -c 'GET\|POST' <<<"$journaux") requêtes journalisées)."
