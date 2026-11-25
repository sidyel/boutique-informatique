-- Phase 2 : tri par popularité et identifiants Cloudinary des images du site.

-- Nombre d'unités vendues, incrémenté à la confirmation du paiement (phase 4).
-- Sert au tri « popularité » sans jointure sur les commandes.
ALTER TABLE produit ADD COLUMN nombre_ventes integer NOT NULL DEFAULT 0;
ALTER TABLE produit ADD CONSTRAINT ck_produit_nombre_ventes CHECK (nombre_ventes >= 0);
CREATE INDEX ix_produit_popularite ON produit (nombre_ventes DESC, nombre_avis DESC, note_moyenne DESC) WHERE actif;
CREATE INDEX ix_produit_nouveaute ON produit (date_creation DESC) WHERE actif;

-- Identifiant Cloudinary de chaque image : nécessaire pour supprimer l'ancien fichier quand l'image est remplacée.
ALTER TABLE parametres_site
    ADD COLUMN logo_id_public          varchar(255),
    ADD COLUMN logo_sombre_id_public   varchar(255),
    ADD COLUMN favicon_id_public       varchar(255),
    ADD COLUMN image_partage_id_public varchar(255);

ALTER TABLE banniere
    ADD COLUMN image_ordinateur_id_public varchar(255),
    ADD COLUMN image_mobile_id_public     varchar(255);

