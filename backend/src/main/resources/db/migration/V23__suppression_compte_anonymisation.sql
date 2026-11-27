-- =====================================================================
-- V23 — Suppression de compte et anonymisation (phase 5d, ADR 0004 §3)
--  1. Commande : marque de l'anonymisation ; téléphone et adresse de livraison peuvent disparaître d'une commande
--     anonymisée (jamais payée, donc sans facture à conserver).
--  2. Historique de consentement et de changements d'e-mail : conservés sans identité (preuve de consentement,
--     journal de sécurité) quand le compte disparaît, au lieu d'être supprimés en cascade.
-- =====================================================================

-- 1. Commande -----------------------------------------------------------
ALTER TABLE commande ADD COLUMN anonymisee_le timestamptz;

ALTER TABLE commande ALTER COLUMN telephone_client DROP NOT NULL;
ALTER TABLE commande ADD CONSTRAINT ck_commande_telephone_present
    CHECK (telephone_client IS NOT NULL OR anonymisee_le IS NOT NULL);

-- Livraison à domicile : adresse complète obligatoire, sauf commande anonymisée
ALTER TABLE commande DROP CONSTRAINT ck_commande_adresse_domicile;
ALTER TABLE commande ADD CONSTRAINT ck_commande_adresse_domicile CHECK (
    mode_livraison <> 'DOMICILE'
    OR anonymisee_le IS NOT NULL
    OR (livraison_nom_complet IS NOT NULL AND livraison_telephone IS NOT NULL
        AND livraison_ville IS NOT NULL AND livraison_quartier IS NOT NULL
        AND zone_livraison_id IS NOT NULL));

-- 2. Historiques sans identité ---------------------------------------------
ALTER TABLE historique_consentement DROP CONSTRAINT historique_consentement_utilisateur_id_fkey;
ALTER TABLE historique_consentement ALTER COLUMN utilisateur_id DROP NOT NULL;
ALTER TABLE historique_consentement ADD CONSTRAINT historique_consentement_utilisateur_id_fkey
    FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE SET NULL;

ALTER TABLE changement_email DROP CONSTRAINT changement_email_utilisateur_id_fkey;
ALTER TABLE changement_email ALTER COLUMN utilisateur_id DROP NOT NULL;
ALTER TABLE changement_email ADD CONSTRAINT changement_email_utilisateur_id_fkey
    FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE SET NULL;
