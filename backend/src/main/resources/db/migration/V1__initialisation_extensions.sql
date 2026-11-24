-- Extensions PostgreSQL utilisées par la recherche plein texte (phase 2) :
--  - pg_trgm  : similarité par trigrammes (tolérance aux fautes de frappe)
--  - unaccent : recherche insensible aux accents (« ecran » trouve « écran »)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
