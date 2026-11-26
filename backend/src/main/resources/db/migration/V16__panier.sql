-- =====================================================================
-- V16 — Panier (phase 4a).
-- - Quantité maximale par ligne : réglage du site (5 par défaut) et exception facultative par variante.
-- - Prix vu par le client sur chaque ligne : s'il change avant la commande, le client en est informé
--   (« Le prix de cet article est passé de X à Y ») et doit l'accepter avant de payer.
-- - Code promo saisi sur le panier (revalidé à chaque lecture et à la commande).
-- - panier.jeton_invite contient désormais l'empreinte SHA-256 (hex) du jeton du cookie, jamais le
--   jeton lui-même : une fuite de la base ne donne accès à aucun panier.
-- =====================================================================

ALTER TABLE parametres_site
    ADD COLUMN quantite_max_par_ligne integer NOT NULL DEFAULT 5,
    ADD CONSTRAINT ck_parametres_site_quantite_max CHECK (quantite_max_par_ligne BETWEEN 1 AND 99);

ALTER TABLE variante_produit
    ADD COLUMN quantite_max integer,
    ADD CONSTRAINT ck_variante_quantite_max CHECK (quantite_max IS NULL OR quantite_max BETWEEN 1 AND 99);

ALTER TABLE ligne_panier ADD COLUMN prix_vu bigint;
UPDATE ligne_panier l SET prix_vu = v.prix FROM variante_produit v WHERE v.id = l.variante_id;
ALTER TABLE ligne_panier
    ALTER COLUMN prix_vu SET NOT NULL,
    ADD CONSTRAINT ck_ligne_panier_prix_vu CHECK (prix_vu >= 0);

ALTER TABLE panier
    ADD COLUMN code_promo varchar(40),
    ADD CONSTRAINT ck_panier_code_promo CHECK (code_promo IS NULL OR code_promo ~ '^[A-Z0-9_-]{3,40}$'),
    ADD CONSTRAINT ck_panier_jeton_invite CHECK (jeton_invite IS NULL OR jeton_invite ~ '^[0-9a-f]{64}$');
