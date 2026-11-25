package sn.boutique.media;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaOrphelinDepot extends JpaRepository<MediaOrphelin, Long> {

    Optional<MediaOrphelin> findByIdPublic(String idPublic);
}
