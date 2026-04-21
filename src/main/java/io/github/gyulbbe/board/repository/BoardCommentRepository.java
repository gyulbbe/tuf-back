package io.github.gyulbbe.board.repository;

import io.github.gyulbbe.board.entity.BoardCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardCommentRepository extends JpaRepository<BoardCommentEntity, Long> {

    List<BoardCommentEntity> findAllByBoardIdOrderByRegDateAscIdAsc(Long boardId);

    Optional<BoardCommentEntity> findByIdAndBoardId(Long id, Long boardId);

    void deleteAllByBoardId(Long boardId);
}
