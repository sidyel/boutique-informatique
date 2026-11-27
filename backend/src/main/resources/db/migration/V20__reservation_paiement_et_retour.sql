-- Création de la facture chez le prestataire : jamais de verrou ni de transaction pendant l'appel réseau.
-- La commande est « réservée » pour quelques secondes (verrou bref, commit), l'appel se fait hors transaction,
-- puis le résultat est enregistré dans une nouvelle transaction. Une réservation périmée (serveur arrêté pendant
-- l'appel) est reprise par la requête suivante.
ALTER TABLE commande ADD COLUMN paiement_en_cours_jusqua timestamptz;

-- Adresse de retour donnée au prestataire : elle porte un jeton de retour propre à chaque facture, distinct du
-- jeton de suivi (qui peut être renouvelé). Seule l'empreinte SHA-256 est stockée ; ce jeton ne donne accès
-- qu'au statut du paiement.
ALTER TABLE paiement ADD COLUMN empreinte_jeton_retour varchar(64);
ALTER TABLE paiement ADD CONSTRAINT ck_paiement_empreinte_jeton_retour
    CHECK (empreinte_jeton_retour IS NULL OR empreinte_jeton_retour ~ '^[0-9a-f]{64}$');
CREATE UNIQUE INDEX uk_paiement_empreinte_jeton_retour ON paiement (empreinte_jeton_retour)
    WHERE empreinte_jeton_retour IS NOT NULL;
