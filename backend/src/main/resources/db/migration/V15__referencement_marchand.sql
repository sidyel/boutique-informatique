-- Phase 3d : informations marchand pour les données structurées (Google) et retrait définitif des produits.

-- Politique de retour (JSON-LD MerchantReturnPolicy), réglable sans redéploiement.
-- Retours acceptés : délai en jours obligatoire et au moins un mode de retour.
ALTER TABLE parametres_site
    ADD COLUMN retours_acceptes   boolean     NOT NULL DEFAULT true,
    ADD COLUMN delai_retour_jours integer              DEFAULT 7,
    ADD COLUMN frais_retour       varchar(40) NOT NULL DEFAULT 'GRATUIT',
    ADD COLUMN retour_en_boutique boolean     NOT NULL DEFAULT true,
    ADD COLUMN retour_par_livreur boolean     NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_parametres_site_frais_retour
        CHECK (frais_retour IN ('GRATUIT', 'A_LA_CHARGE_DU_CLIENT')),
    ADD CONSTRAINT ck_parametres_site_delai_retour
        CHECK (delai_retour_jours IS NULL OR delai_retour_jours BETWEEN 1 AND 365),
    ADD CONSTRAINT ck_parametres_site_retours
        CHECK (NOT retours_acceptes
               OR (delai_retour_jours IS NOT NULL AND (retour_en_boutique OR retour_par_livreur)));

-- Boutique physique (JSON-LD Store / LocalBusiness) : publiée seulement si boutique_physique est vrai, ce qui
-- impose une adresse complète. L'adresse structurée sert aussi à Organization.address.
-- Horaires d'ouverture : [{"jours": ["MONDAY", …], "ouverture": "09:00", "fermeture": "19:00"}].
-- La colonne texte « horaires » reste celle affichée sur le site.
ALTER TABLE parametres_site
    ADD COLUMN boutique_physique    boolean      NOT NULL DEFAULT false,
    ADD COLUMN boutique_rue         varchar(200),
    ADD COLUMN boutique_ville       varchar(100),
    ADD COLUMN boutique_region      varchar(100),
    ADD COLUMN boutique_code_postal varchar(10),
    ADD COLUMN boutique_latitude    numeric(9, 6),
    ADD COLUMN boutique_longitude   numeric(9, 6),
    ADD COLUMN horaires_ouverture   jsonb        NOT NULL DEFAULT '[]',
    ADD CONSTRAINT ck_parametres_site_boutique_adresse
        CHECK (NOT boutique_physique
               OR (length(btrim(coalesce(boutique_rue, ''))) > 0
                   AND length(btrim(coalesce(boutique_ville, ''))) > 0
                   AND length(btrim(coalesce(boutique_region, ''))) > 0)),
    ADD CONSTRAINT ck_parametres_site_boutique_coordonnees
        CHECK ((boutique_latitude IS NULL) = (boutique_longitude IS NULL)
               AND (boutique_latitude IS NULL OR boutique_latitude BETWEEN -90 AND 90)
               AND (boutique_longitude IS NULL OR boutique_longitude BETWEEN -180 AND 180)),
    ADD CONSTRAINT ck_parametres_site_horaires_ouverture
        CHECK (jsonb_typeof(horaires_ouverture) = 'array' AND jsonb_array_length(horaires_ouverture) <= 14);

-- Retrait définitif d'un produit (ADR 0004, tranché en 3d, ADR 0007) :
-- - masqué temporairement : actif = false, date_retrait NULL → 404 (Google réessaie) ;
-- - retiré : date_retrait renseignée (et actif = false) → 301 vers le remplaçant s'il est publié, sinon 410.
ALTER TABLE produit
    ADD COLUMN date_retrait          timestamptz,
    ADD COLUMN produit_remplacant_id bigint REFERENCES produit (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_produit_retrait_inactif
        CHECK (date_retrait IS NULL OR NOT actif),
    ADD CONSTRAINT ck_produit_remplacant
        CHECK (produit_remplacant_id IS NULL
               OR (date_retrait IS NOT NULL AND produit_remplacant_id <> id));

CREATE INDEX ix_produit_remplacant ON produit (produit_remplacant_id) WHERE produit_remplacant_id IS NOT NULL;
