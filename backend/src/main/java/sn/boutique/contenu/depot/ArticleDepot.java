package sn.boutique.contenu.depot;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.boutique.contenu.modele.Article;

public interface ArticleDepot extends JpaRepository<Article, Long> {

    Optional<Article> findBySlugAndPublieTrue(String slug);

    Page<Article> findByPublieTrueOrderByDatePublicationDesc(Pageable pageable);
}
