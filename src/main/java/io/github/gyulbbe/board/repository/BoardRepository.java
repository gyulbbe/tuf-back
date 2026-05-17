package io.github.gyulbbe.board.repository;

import io.github.gyulbbe.board.entity.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardRepository extends JpaRepository<BoardEntity, Long>, BoardRepositoryCustom {
    List<BoardEntity> findTop5ByOrderByRegDateDescIdDesc();
}
