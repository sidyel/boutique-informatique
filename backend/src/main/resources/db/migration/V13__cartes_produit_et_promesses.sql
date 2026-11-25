-- Phase 3a : caractéristiques affichées sur les cartes produit et promesses commerciales configurables.

-- Caractéristiques clés de la carte produit : 4 au plus par catégorie, dans l'ordre 1 à 4.
-- NULL : la caractéristique n'apparaît que sur la fiche produit.
ALTER TABLE definition_caracteristique
    ADD COLUMN ordre_carte   smallint,
    ADD COLUMN libelle_court varchar(20),
    ADD CONSTRAINT ck_definition_ordre_carte CHECK (ordre_carte BETWEEN 1 AND 4),
    ADD CONSTRAINT ck_definition_libelle_court CHECK (libelle_court IS NULL OR length(btrim(libelle_court)) > 0);

CREATE UNIQUE INDEX uk_definition_ordre_carte
    ON definition_caracteristique (categorie_id, ordre_carte) WHERE ordre_carte IS NOT NULL;

-- Promesses commerciales : texte de la barre supérieure et éléments du bandeau de confiance
-- (tableau JSON de {"icone", "titre", "texte"}). Vides par défaut : rien n'est affiché tant que
-- l'équipe ne les a pas saisis (aucune promesse écrite en dur dans le site).
ALTER TABLE parametres_site
    ADD COLUMN message_barre_superieure varchar(120),
    ADD COLUMN elements_confiance       jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_parametres_site_elements_confiance
        CHECK (jsonb_typeof(elements_confiance) = 'array' AND jsonb_array_length(elements_confiance) <= 6);
