-- Paiement reçu en double (deux factures du prestataire payées pour une même commande) ou après coup :
-- l'argent est encaissé mais ne correspond à aucune vente. Il est marqué « à rembourser » et signalé à l'équipe.
ALTER TABLE paiement DROP CONSTRAINT ck_paiement_statut;
ALTER TABLE paiement ADD CONSTRAINT ck_paiement_statut
    CHECK (statut IN ('EN_ATTENTE', 'REUSSI', 'ECHOUE', 'ANNULE', 'A_REMBOURSER', 'REMBOURSE'));
CREATE INDEX ix_paiement_a_rembourser ON paiement (date_creation) WHERE statut = 'A_REMBOURSER';
