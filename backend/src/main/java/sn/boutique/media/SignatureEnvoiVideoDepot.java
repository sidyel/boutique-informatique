package sn.boutique.media;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignatureEnvoiVideoDepot extends JpaRepository<SignatureEnvoiVideo, Long> {

    Optional<SignatureEnvoiVideo> findByIdPublic(String idPublic);
}
