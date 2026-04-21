package io.github.gyulbbe.board.repository;

import io.github.gyulbbe.board.dto.BoardSearchType;
import io.github.gyulbbe.board.entity.BoardEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BoardRepositoryCustom {

    Page<BoardEntity> search(BoardSearchType searchType, String keyword, Pageable pageable);
}
