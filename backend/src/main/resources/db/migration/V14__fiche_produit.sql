-- Phase 3c : réglages de la fiche produit, modifiables sans redéploiement.

-- Bouton d'achat mis en avant sur la fiche : « Ajouter au panier » (PANIER, par défaut) ou
-- « Commander sur WhatsApp » (WHATSAPP). L'autre reste affiché juste en dessous, en bouton secondaire.
-- Délai de livraison affiché près du bouton d'achat (« Livré à Dakar en 24 h, en régions en 2 à 4 jours ») :
-- NULL, rien n'est affiché (aucune promesse écrite en dur dans le site) ; jamais affiché pour une variante
-- en rupture de stock.
ALTER TABLE parametres_site
    ADD COLUMN bouton_principal_fiche varchar(10) NOT NULL DEFAULT 'PANIER',
    ADD COLUMN delai_livraison        varchar(120),
    ADD CONSTRAINT ck_parametres_site_bouton_principal_fiche
        CHECK (bouton_principal_fiche IN ('PANIER', 'WHATSAPP')),
    ADD CONSTRAINT ck_parametres_site_delai_livraison
        CHECK (delai_livraison IS NULL OR length(btrim(delai_livraison)) > 0);
