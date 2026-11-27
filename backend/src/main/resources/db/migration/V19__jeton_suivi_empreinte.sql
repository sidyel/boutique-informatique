-- Lien de suivi sans compte : seule l'empreinte SHA-256 du jeton est stockée, comme pour les autres jetons
-- (rafraîchissement, vérification d'e-mail, réinitialisation). Une fuite de la base ne donne plus aucun lien de suivi.
-- Les liens déjà émis restent valables : leur empreinte est calculée ici à partir du jeton en clair, puis ce dernier
-- est supprimé. Pour renvoyer un lien, l'application génère un nouveau jeton qui remplace l'ancien.
ALTER TABLE commande ADD COLUMN empreinte_jeton_suivi varchar(64);
UPDATE commande SET empreinte_jeton_suivi = encode(sha256(convert_to(jeton_suivi, 'UTF8')), 'hex')
 WHERE jeton_suivi IS NOT NULL;
DROP INDEX uk_commande_jeton_suivi;
ALTER TABLE commande DROP CONSTRAINT ck_commande_jeton_suivi;
ALTER TABLE commande DROP COLUMN jeton_suivi;
ALTER TABLE commande ADD CONSTRAINT ck_commande_empreinte_jeton_suivi
    CHECK (empreinte_jeton_suivi IS NULL OR empreinte_jeton_suivi ~ '^[0-9a-f]{64}$');
CREATE UNIQUE INDEX uk_commande_empreinte_jeton_suivi ON commande (empreinte_jeton_suivi)
    WHERE empreinte_jeton_suivi IS NOT NULL;
