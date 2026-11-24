-- =====================================================================
-- V8 — Avis réservés aux clients ayant reçu le produit (ADR 0004 §5).
-- Activé par défaut ; modifiable dans l'admin sans redéploiement.
-- =====================================================================

ALTER TABLE parametres_site
    ADD COLUMN avis_reserves_aux_acheteurs boolean NOT NULL DEFAULT true;
