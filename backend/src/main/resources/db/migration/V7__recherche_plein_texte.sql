-- =====================================================================
-- V7 — Recherche plein texte des produits, insensible aux accents.
--
-- Vecteur pondéré stocké dans produit.vecteur_recherche (hors mapping JPA) :
--   A : nom du produit
--   B : marque + référence fabricant (MPN)
--   C : catégorie et catégories parentes + caractéristiques clés
--       (definition_caracteristique.cle_recherche = true : processeur, carte graphique…)
--   D : description courte
-- « asus portable » trouve donc un portable Asus dont le nom ne contient pas « Asus ».
--
-- Maintenu par triggers sur produit, marque, categorie, definition_caracteristique
-- et valeur_caracteristique : toujours exact, quel que soit le chemin d'écriture.
-- =====================================================================

-- unaccent() n'est que STABLE (son dictionnaire peut changer) : on l'enveloppe dans une
-- fonction IMMUTABLE pour pouvoir l'utiliser dans des index. Dictionnaire nommé avec son
-- schéma pour ne pas dépendre du search_path.
CREATE FUNCTION sans_accents(texte text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, texte);
$$;

-- Configuration française qui retire les accents AVANT la racinisation :
-- « écran », « ecran », « Écrans » donnent tous « ecran ».
CREATE TEXT SEARCH CONFIGURATION francais_sans_accents (COPY = pg_catalog.french);
ALTER TEXT SEARCH CONFIGURATION francais_sans_accents
    ALTER MAPPING FOR hword, hword_part, word WITH public.unaccent, french_stem;

ALTER TABLE produit ADD COLUMN vecteur_recherche tsvector NOT NULL DEFAULT ''::tsvector;

-- ---------------------------------------------------------------------
-- Calcul du vecteur d'un produit. Les champs propres au produit sont passés
-- en paramètres pour pouvoir servir dans un trigger BEFORE (ligne pas encore écrite).
-- ---------------------------------------------------------------------
CREATE FUNCTION calculer_vecteur_produit(
    p_id bigint, p_nom text, p_mpn text, p_marque_id bigint, p_categorie_id bigint, p_description_courte text)
RETURNS tsvector
LANGUAGE sql STABLE AS $$
    SELECT setweight(to_tsvector('francais_sans_accents', coalesce(p_nom, '')), 'A')
        || setweight(to_tsvector('francais_sans_accents',
               coalesce((SELECT nom FROM marque WHERE id = p_marque_id), '') || ' ' || coalesce(p_mpn, '')), 'B')
        || setweight(to_tsvector('francais_sans_accents',
               coalesce((WITH RECURSIVE ascendance AS (
                             SELECT id, parent_id, nom FROM categorie WHERE id = p_categorie_id
                             UNION ALL
                             SELECT c.id, c.parent_id, c.nom FROM categorie c JOIN ascendance a ON c.id = a.parent_id)
                         SELECT string_agg(nom, ' ') FROM ascendance), '')
               || ' ' ||
               coalesce((SELECT string_agg(
                                    coalesce(v.valeur_texte,
                                             trim_scale(v.valeur_nombre)::text || coalesce(' ' || d.unite, '')),
                                    ' ')
                           FROM valeur_caracteristique v
                           JOIN definition_caracteristique d ON d.id = v.definition_id
                          WHERE v.produit_id = p_id AND d.cle_recherche AND v.valeur_booleen IS NULL), '')), 'C')
        || setweight(to_tsvector('francais_sans_accents', coalesce(p_description_courte, '')), 'D');
$$;

-- Produit : calcul à l'insertion et quand un champ indexé change
CREATE FUNCTION declencheur_vecteur_produit() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.vecteur_recherche := calculer_vecteur_produit(
        NEW.id, NEW.nom, NEW.mpn, NEW.marque_id, NEW.categorie_id, NEW.description_courte);
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_produit_vecteur
    BEFORE INSERT OR UPDATE OF nom, mpn, marque_id, categorie_id, description_courte ON produit
    FOR EACH ROW EXECUTE FUNCTION declencheur_vecteur_produit();

-- Recalcul d'un ensemble de produits (utilisé par les triggers des tables liées)
CREATE FUNCTION recalculer_vecteurs(p_ids bigint[]) RETURNS void
LANGUAGE sql AS $$
    UPDATE produit
       SET vecteur_recherche = calculer_vecteur_produit(id, nom, mpn, marque_id, categorie_id, description_courte)
     WHERE id = ANY (p_ids);
$$;

-- Marque renommée
CREATE FUNCTION declencheur_vecteur_marque() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM recalculer_vecteurs(ARRAY(SELECT id FROM produit WHERE marque_id = NEW.id));
    RETURN NULL;
END;
$$;

CREATE TRIGGER tr_marque_vecteur
    AFTER UPDATE OF nom ON marque
    FOR EACH ROW WHEN (OLD.nom IS DISTINCT FROM NEW.nom)
    EXECUTE FUNCTION declencheur_vecteur_marque();

-- Catégorie renommée ou déplacée : ses produits et ceux de ses sous-catégories
CREATE FUNCTION declencheur_vecteur_categorie() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM recalculer_vecteurs(ARRAY(
        WITH RECURSIVE descendance AS (
            SELECT id FROM categorie WHERE id = NEW.id
            UNION ALL
            SELECT c.id FROM categorie c JOIN descendance d ON c.parent_id = d.id)
        SELECT p.id FROM produit p JOIN descendance d ON p.categorie_id = d.id));
    RETURN NULL;
END;
$$;

CREATE TRIGGER tr_categorie_vecteur
    AFTER UPDATE OF nom, parent_id ON categorie
    FOR EACH ROW WHEN (OLD.nom IS DISTINCT FROM NEW.nom OR OLD.parent_id IS DISTINCT FROM NEW.parent_id)
    EXECUTE FUNCTION declencheur_vecteur_categorie();

-- Définition de caractéristique ajoutée à la recherche ou retirée, ou unité modifiée
CREATE FUNCTION declencheur_vecteur_definition() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM recalculer_vecteurs(ARRAY(
        SELECT DISTINCT produit_id FROM valeur_caracteristique WHERE definition_id = NEW.id));
    RETURN NULL;
END;
$$;

CREATE TRIGGER tr_definition_vecteur
    AFTER UPDATE OF cle_recherche, unite ON definition_caracteristique
    FOR EACH ROW WHEN (OLD.cle_recherche IS DISTINCT FROM NEW.cle_recherche OR OLD.unite IS DISTINCT FROM NEW.unite)
    EXECUTE FUNCTION declencheur_vecteur_definition();

-- Valeur de caractéristique ajoutée, modifiée ou supprimée
CREATE FUNCTION declencheur_vecteur_valeur() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM recalculer_vecteurs(ARRAY[OLD.produit_id]);
    ELSIF TG_OP = 'UPDATE' AND OLD.produit_id <> NEW.produit_id THEN
        PERFORM recalculer_vecteurs(ARRAY[OLD.produit_id, NEW.produit_id]);
    ELSE
        PERFORM recalculer_vecteurs(ARRAY[NEW.produit_id]);
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER tr_valeur_vecteur
    AFTER INSERT OR UPDATE OR DELETE ON valeur_caracteristique
    FOR EACH ROW EXECUTE FUNCTION declencheur_vecteur_valeur();

-- Produits déjà présents (aucun en production à ce stade, mais la migration reste juste)
UPDATE produit
   SET vecteur_recherche = calculer_vecteur_produit(id, nom, mpn, marque_id, categorie_id, description_courte);

-- ---------------------------------------------------------------------
-- Index
-- ---------------------------------------------------------------------
CREATE INDEX ix_produit_recherche ON produit USING gin (vecteur_recherche);

-- Tolérance aux fautes de frappe (« lenvo », « logitec ») et autocomplétion : trigrammes
CREATE INDEX ix_produit_nom_trigramme ON produit USING gin (sans_accents(lower(nom)) gin_trgm_ops);
CREATE INDEX ix_marque_nom_trigramme ON marque USING gin (sans_accents(lower(nom)) gin_trgm_ops);
