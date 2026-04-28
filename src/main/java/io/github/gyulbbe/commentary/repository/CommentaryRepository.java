package io.github.gyulbbe.commentary.repository;

import io.github.gyulbbe.commentary.entity.CommentaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentaryRepository extends JpaRepository<CommentaryEntity, Long> {
}
