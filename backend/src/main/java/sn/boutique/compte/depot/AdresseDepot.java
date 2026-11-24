package sn.boutique.compte.depot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.compte.modele.Adresse;

public interface AdresseDepot extends JpaRepository<Adresse, Long> {

    List<Adresse> findByUtilisateurIdOrderByParDefautDescDateCreationAsc(Long utilisateurId);
}
