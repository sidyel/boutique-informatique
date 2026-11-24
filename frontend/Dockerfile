# Node 24 LTS (supporté par Angular 21, et prêt pour Angular 22 — voir docs/decisions/0002).

# --- Étape 1 : compilation (navigateur + serveur SSR) ----------------------
FROM node:24-alpine AS compilation
WORKDIR /source

# Dépendances d'abord, pour profiter du cache Docker.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build
# PHASE 9 : retirer les images de démonstration (public/demo) de l'image de production, voir README.

# --- Étape 2 : exécution ----------------------------------------------------
# Le serveur SSR est entièrement empaqueté par Angular (express inclus) :
# aucun node_modules n'est nécessaire à l'exécution.
FROM node:24-alpine
WORKDIR /application

ENV NODE_ENV=production \
    PORT=4000

COPY --from=compilation --chown=node:node /source/dist/frontend ./dist/frontend
USER node

EXPOSE 4000
CMD ["node", "dist/frontend/server/server.mjs"]
